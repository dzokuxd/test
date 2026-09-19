package pl.gildie.monument;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.Const;
import pl.gildie.db.MonumentRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MonumentManager {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JavaPlugin plugin;
    private final MonumentRepository repo;
    private Location centerLocation;
    private final Map<Integer, Location> cornerLocations = new HashMap<>();
    private UUID centerCrystalId;
    private final Map<Integer, UUID> cornerCrystalIds = new HashMap<>();

    private BossBar centerBar;
    private BossBar cornerBar;
    private int lastCornerHour = -1;

    public MonumentManager(JavaPlugin plugin, MonumentRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
        createDefaultLocations();
        loadLocationsFromDb();
        purgeWorldCrystals();
        restoreAfterRestart();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    private void createDefaultLocations() {
        World w = Bukkit.getWorlds().get(0);
        if (w == null) return;
        int cx = Const.MONUMENT_CENTER_X;
        int cz = Const.MONUMENT_CENTER_Z;
        int cy = Const.MONUMENT_CENTER_Y;
        if (cy < 0) cy = w.getHighestBlockYAt(cx, cz) + 2;
        int off = Const.MONUMENT_CORNER_OFFSET;
        centerLocation = new Location(w, cx + 0.5, cy, cz + 0.5);
        cornerLocations.put(1, new Location(w, cx + off + 0.5, cy, cz + off + 0.5));
        cornerLocations.put(2, new Location(w, cx + off + 0.5, cy, cz - off + 0.5));
        cornerLocations.put(3, new Location(w, cx - off + 0.5, cy, cz + off + 0.5));
        cornerLocations.put(4, new Location(w, cx - off + 0.5, cy, cz - off + 0.5));
    }

    private void loadLocationsFromDb() {
        for (MonumentRepository.Crystal cr : repo.loadCrystals()) {
            World w = Bukkit.getWorld(cr.world);
            if (w == null || (cr.x == 0 && cr.y == 0 && cr.z == 0)) continue;
            Location loc = new Location(w, cr.x, cr.y, cr.z);
            if (cr.id == 0) centerLocation = loc;
            else if (cr.id >= 1 && cr.id <= 4) cornerLocations.put(cr.id, loc);
        }
    }

    // Srodek wraca po restarcie TYLKO jesli byl spawniety DZIS i nadal zyje.
    // Jesli dzis jeszcze nie bylo 18:00 (lastSpawnDate != today) -> nie ma go.
    private void restoreAfterRestart() {
        LocalDate today = LocalDate.now();
        MonumentRepository.CenterState cs = repo.getCenterState();
        String todayStr = today.format(DATE_FORMAT);

        if (cs.active && todayStr.equals(cs.lastSpawnDate)) {
            spawnCenter(false);
        } else {
            repo.setCenterActive(false);
        }

        for (int i = 1; i <= 4; i++) {
            if (!cornerCrystalIds.containsKey(i)) spawnCorner(i, false);
        }
        lastCornerHour = LocalDateTime.now().getHour();
    }

    private void tickSecond() {
        LocalDateTime now = LocalDateTime.now();
        String today = now.format(DATE_FORMAT);

        // Narozne: co pelna godzine -> brak = spawn, zywy = full HP
        if (now.getMinute() == 0 && lastCornerHour != now.getHour()) {
            lastCornerHour = now.getHour();
            for (int i = 1; i <= 4; i++) {
                UUID id = cornerCrystalIds.get(i);
                if (id != null) CrystalHP.set(id, Const.MONUMENT_CRYSTAL_HP);
                else spawnCorner(i, false);
            }
        }

        // Srodek: WYLACZNIE od 18:00 i tylko raz dziennie
        MonumentRepository.CenterState cs = repo.getCenterState();
        boolean centerAlive = centerCrystalId != null;
        boolean spawnedToday = today.equals(cs.lastSpawnDate);
        if (!centerAlive && !spawnedToday && now.getHour() >= Const.MONUMENT_CENTER_SPAWN_HOUR) {
            spawnCenter(false);
            centerAlive = true;
        }

        updateBossBars(now, centerAlive, spawnedToday);
    }

    private void updateBossBars(LocalDateTime now, boolean centerAlive, boolean spawnedToday) {
        boolean cornerMissing = false;
        for (int i = 1; i <= 4; i++) if (!cornerCrystalIds.containsKey(i)) cornerMissing = true;
        int secToHour = 3600 - (now.getMinute() * 60 + now.getSecond());
        if (cornerMissing && secToHour <= Const.MONUMENT_BOSSBAR_MINUTES * 60) {
            showBar(getCornerBar(), "§5Narozne monumenty odrodza sie za §d" + fmt(secToHour),
                    1.0 - secToHour / (double) (Const.MONUMENT_BOSSBAR_MINUTES * 60));
        } else {
            hideBar(getCornerBar());
        }

        long secTo18 = secondsUntilCenterSpawn(now);
        boolean willSpawn = !centerAlive && !spawnedToday;
        if (willSpawn && secTo18 > 0 && secTo18 <= Const.MONUMENT_BOSSBAR_MINUTES * 60) {
            showBar(getCenterBar(), "§6Srodkowy monument odrodzi sie za §e" + fmt(secTo18),
                    1.0 - secTo18 / (double) (Const.MONUMENT_BOSSBAR_MINUTES * 60));
        } else {
            hideBar(getCenterBar());
        }
    }

    private long secondsUntilCenterSpawn(LocalDateTime now) {
        LocalDateTime target = now.toLocalDate().atTime(Const.MONUMENT_CENTER_SPAWN_HOUR, 0);
        if (!now.isBefore(target)) target = target.plusDays(1);
        return Duration.between(now, target).getSeconds();
    }

    // ── Scoreboard: pozostałe ŻYCIE środka ─────────────────────────────────
    public boolean isCenterActive() { return centerCrystalId != null; }

    public String describeCenterHp() {
        int max = Const.MONUMENT_CRYSTAL_HP;
        if (centerCrystalId != null) {
            return "§a" + CrystalHP.get(centerCrystalId) + "/" + max;
        }
        String today = LocalDate.now().format(DATE_FORMAT);
        if (today.equals(repo.getCenterState().lastSpawnDate)) {
            return "§c0/" + max + " (zdobyta)";
        }
        return "§70/" + max + " (od 18:00)";
    }

    private String fmt(long sec) {
        long m = sec / 60, s = sec % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    private BossBar getCenterBar() {
        if (centerBar == null) centerBar = Bukkit.createBossBar("Monument", BarColor.YELLOW, BarStyle.SOLID);
        return centerBar;
    }

    private BossBar getCornerBar() {
        if (cornerBar == null) cornerBar = Bukkit.createBossBar("Monument", BarColor.PURPLE, BarStyle.SOLID);
        return cornerBar;
    }

    private void showBar(BossBar bar, String title, double progress) {
        bar.setTitle(title);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    private void hideBar(BossBar bar) {
        if (bar != null && !bar.getPlayers().isEmpty()) bar.removeAll();
    }

    // ── START / STOP (center / corner / all) ───────────────────────────────
    public void startCenter() { spawnCenter(true); }

    public void stopCenter() {
        if (centerCrystalId != null) removeCrystalEntity(centerCrystalId);
        clearCrystalsAt(centerLocation);
        centerCrystalId = null;
        repo.setCenterActive(false);
        repo.setCenterNextRespawnAt(0L);
        hideBar(getCenterBar());
    }

    public void startCorners() { for (int i = 1; i <= 4; i++) spawnCorner(i, true); }

    public void stopCorners() {
        for (UUID id : cornerCrystalIds.values()) removeCrystalEntity(id);
        cornerCrystalIds.clear();
        for (int i = 1; i <= 4; i++) repo.setCornerRespawnAt(i, 0L);
        hideBar(getCornerBar());
    }

    public void startAll() { startCenter(); startCorners(); }
    public void stopAll() { stopCenter(); stopCorners(); }

    // ── Spawn / destroy ────────────────────────────────────────────────────
    private void clearCrystalsAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 2, 4, 2)) {
            if (e instanceof EnderCrystal) e.remove();
        }
    }

    private void purgeWorldCrystals() {
        clearCrystalsAt(centerLocation);
        for (Location l : cornerLocations.values()) clearCrystalsAt(l);
    }

    public void spawnCenter(boolean force) {
        if (centerLocation == null) { if (force) plugin.getLogger().warning("Brak center location"); return; }
        if (centerCrystalId != null) removeCrystalEntity(centerCrystalId);
        EnderCrystal c = centerLocation.getWorld().spawn(centerLocation, EnderCrystal.class);
        c.setShowingBottom(false); c.setInvulnerable(false);
        CrystalHP.set(c.getUniqueId(), Const.MONUMENT_CRYSTAL_HP);
        centerCrystalId = c.getUniqueId();
        repo.setCenterActive(true);
        repo.setCenterLastSpawnDate(LocalDate.now().format(DATE_FORMAT));
        repo.setCenterNextRespawnAt(0L);
        Bukkit.broadcastMessage("§6§l[MONUMENT] §eSrodkowy crystal zostal przywolany!");
    }

    public void spawnCorner(int id, boolean force) {
        Location loc = cornerLocations.get(id);
        if (loc == null) { if (force) plugin.getLogger().warning("Brak corner " + id); return; }
        UUID old = cornerCrystalIds.get(id);
        if (old != null) removeCrystalEntity(old);
        clearCrystalsAt(loc);
        EnderCrystal c = loc.getWorld().spawn(loc, EnderCrystal.class);
        c.setShowingBottom(false); c.setInvulnerable(false);
        CrystalHP.set(c.getUniqueId(), Const.MONUMENT_CRYSTAL_HP);
        cornerCrystalIds.put(id, c.getUniqueId());
        repo.setCornerRespawnAt(id, 0L);
    }

    private void removeCrystalEntity(UUID u) {
        if (u == null) return;
        CrystalHP.remove(u);
        org.bukkit.entity.Entity e = Bukkit.getEntity(u);
        if (e != null) e.remove();
    }

    public void centerDestroyedByGuild() {
        centerCrystalId = null;
        repo.setCenterActive(false);
        repo.setCenterNextRespawnAt(0L);
        giveTopRewards();
    }

    private void giveTopRewards() {
        List<MonumentRepository.Hit> top = repo.getTopCenterHits(5);
        if (top.isEmpty()) return;
        Bukkit.broadcastMessage("§6§l[MONUMENT] §eTOP uderzen w korone:");
        for (int i = 0; i < top.size(); i++) {
            MonumentRepository.Hit h = top.get(i);
            Bukkit.broadcastMessage("§7" + (i + 1) + ". §f" + h.name + " §7- §a" + h.hits);
            if (i < Const.MONUMENT_TOP_REWARDS.length) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        Const.MONUMENT_TOP_REWARDS[i].replace("%player%", h.name));
            }
        }
        repo.resetCenterHits();
    }

    public void centerDestroyedNoGuild() {
        centerCrystalId = null;
        repo.setCenterActive(false);
        repo.setCenterNextRespawnAt(0L);
    }

    public void centerDelivered() {
        repo.setCenterActive(false);
        repo.setCenterCapturedDate(LocalDate.now().format(DATE_FORMAT));
        repo.setCenterNextRespawnAt(0L);
    }

    public void cornerDestroyedByGuild(int id) {
        cornerCrystalIds.remove(id);
        repo.setCornerRespawnAt(id, 0L);
    }

    public void cornerDestroyedNoGuild(int id) {
        cornerCrystalIds.remove(id);
        repo.setCornerRespawnAt(id, 0L);
    }

    public boolean isMonumentCrystal(UUID u) {
        if (u == null) return false;
        if (u.equals(centerCrystalId)) return true;
        for (UUID id : cornerCrystalIds.values()) if (u.equals(id)) return true;
        return false;
    }

    public boolean isCenter(Location l) {
        if (centerLocation == null || l.getWorld() != centerLocation.getWorld()) return false;
        return l.getBlockX() == centerLocation.getBlockX() && l.getBlockZ() == centerLocation.getBlockZ();
    }

    public int getCornerId(Location l) {
        for (var e : cornerLocations.entrySet()) {
            Location c = e.getValue();
            if (l.getWorld() == c.getWorld() && l.getBlockX() == c.getBlockX() && l.getBlockZ() == c.getBlockZ()) return e.getKey();
        }
        return -1;
    }

    public int getPointsForCrystal(Location l) {
        return isCenter(l) ? Const.MONUMENT_POINTS_CENTER : Const.MONUMENT_POINTS_CORNER;
    }

    public boolean isProtected(Location l) {
        if (centerLocation == null || l.getWorld() != centerLocation.getWorld()) return false;
        return l.distanceSquared(centerLocation) <= (long) Const.MONUMENT_PROTECTION_RADIUS * Const.MONUMENT_PROTECTION_RADIUS;
    }

    public Location getCenterLocation() { return centerLocation; }
    public Location getCornerLocation(int id) { return cornerLocations.get(id); }

    public void setCenterLocation(Location l) {
        centerLocation = l;
        repo.saveCrystal(0, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), "CENTER");
    }

    public void setCornerLocation(int id, Location l) {
        cornerLocations.put(id, l);
        repo.saveCrystal(id, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), "CORNER");
    }
}

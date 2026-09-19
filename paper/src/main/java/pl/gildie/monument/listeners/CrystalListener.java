package pl.gildie.monument.listeners;

import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import pl.gildie.Const;
import pl.gildie.db.MonumentRepository;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.monument.BannerService;
import pl.gildie.monument.CrystalHP;
import pl.gildie.monument.MonumentManager;
import pl.gildie.monument.PointsManager;
import pl.gildie.monument.util.MonumentBannerItem;
import pl.gildie.monument.util.MonumentMsg;
import pl.gildie.war.BannerItem;

public class CrystalListener implements Listener {
    private final MonumentManager monumentManager;
    private final GuildManager guildManager;
    private final MonumentRepository repo;

    public CrystalListener(MonumentManager mm, GuildManager gm, MonumentRepository repo) {
        this.monumentManager = mm;
        this.guildManager = gm;
        this.repo = repo;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal c)) return;
        if (!monumentManager.isMonumentCrystal(c.getUniqueId())) return;
        e.setCancelled(true);

        Player atk = getAttacker(e);
        if (atk == null) return;

        Guild guild = guildManager.getGuildByPlayer(atk.getUniqueId());
        if (guild == null) {
            atk.sendMessage(MonumentMsg.error("Musisz byc w gildii, aby bic monument!"));
            return;
        }

        if (BannerItem.isBanner(atk.getInventory().getHelmet())) {
            atk.sendMessage(MonumentMsg.error("Nie mozesz podbijac krysztalu, gdy nosisz sztandar wojny!"));
            return;
        }
        if (MonumentBannerItem.isBanner(atk.getInventory().getHelmet())) {
            atk.sendMessage(MonumentMsg.error("Nie mozesz podbijac krysztalu, gdy nosisz sztandar monumentu!"));
            return;
        }

        int dmg = (int) Math.ceil(e.getFinalDamage());
        if (dmg <= 0) dmg = 1;

        if (PointsManager.tryHit(atk)) PointsManager.addPoints(atk, monumentManager.getPointsForCrystal(c.getLocation()));

        // Licznik uderzen w korone (TOP5)
        if (monumentManager.isCenter(c.getLocation())) {
            repo.addCenterHit(atk.getUniqueId().toString(), atk.getName());
        }

        int left = CrystalHP.damage(c.getUniqueId(), dmg);
        if (left > 0) { atk.sendActionBar(MonumentMsg.title("Crystal: " + left + "/" + Const.MONUMENT_CRYSTAL_HP + " HP")); return; }

        handleDestroyed(c, atk);
    }

    private void handleDestroyed(EnderCrystal c, Player killer) {
        CrystalHP.remove(c.getUniqueId());
        c.remove();
        Guild g = guildManager.getGuildByPlayer(killer.getUniqueId());
        Location loc = c.getLocation();

        if (monumentManager.isCenter(loc)) {
            if (g != null) {
                monumentManager.centerDestroyedByGuild();
                ItemStack b = MonumentBannerItem.createBanner("CENTER", g.getTag(), -1, System.currentTimeMillis());
                BannerService.giveBanner(killer, b);
                killer.sendMessage(MonumentMsg.title("Zdobyles SZTANDAR KORONY!").append(MonumentMsg.desc(" Zanies go do jajka gildii!")));
            } else {
                monumentManager.centerDestroyedNoGuild();
                killer.sendMessage(MonumentMsg.error("Musisz byc w gildii, aby podbic monument!"));
            }
            return;
        }

        int cid = monumentManager.getCornerId(loc);
        if (cid == -1) return;
        if (g != null) {
            monumentManager.cornerDestroyedByGuild(cid);
            ItemStack b = MonumentBannerItem.createBanner("CORNER", g.getTag(), cid, System.currentTimeMillis());
            BannerService.giveBanner(killer, b);
            killer.sendMessage(MonumentMsg.title("Zdobyles sztandar naroznego krysztalu!").append(MonumentMsg.desc(" Zanies go do jajka gildii!")));
        } else {
            monumentManager.cornerDestroyedNoGuild(cid);
            killer.sendMessage(MonumentMsg.error("Musisz byc w gildii, aby podbic monument!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal)) return;
        if (monumentManager.isMonumentCrystal(e.getEntity().getUniqueId())) e.blockList().clear();
    }

    private Player getAttacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) return null;
        Entity d = by.getDamager();
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }
}

package pl.gildie.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.war.BannerItem;
import pl.gildie.war.EggHologram;
import pl.gildie.war.TntManager;
import pl.gildie.war.War;
import pl.gildie.war.WarGui;
import pl.gildie.war.WarManager;
import pl.gildie.war.WarStats;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class WarListener implements Listener {
    private final GuildManager guildManager;
    private final WarManager warManager;

    public WarListener(GuildManager guildManager, WarManager warManager) {
        this.guildManager = guildManager;
        this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!TntManager.isTntEnabled()) { e.blockList().clear(); return; }
        if (e.getEntity() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
            if (g != null) warManager.getActiveWarOf(g.getTag()).ifPresent(w -> w.getStats(g.getTag()).addTnt());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!TntManager.isTntEnabled()) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEggBreak(BlockBreakEvent e) {
        Guild g = eggGuildAt(e.getBlock());
        if (g == null) return;
        e.setCancelled(true);
        e.setDropItems(false);
        warManager.handleEggHit(e.getPlayer(), g);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEggInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        Guild g = eggGuildAt(b);
        if (g == null) return;
        e.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) warManager.handleEggHit(e.getPlayer(), g);
    }

    private Guild eggGuildAt(Block b) {
        if (b == null) return null;
        Guild g = guildManager.getGuildAt(b.getLocation());
        if (g != null && g.isEggBlock(b.getLocation())) return g;
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) if (BannerItem.isBanner(it.next())) it.remove();
        warManager.handleBannerDeath(p);

        Player killer = p.getKiller();
        Guild victimG = guildManager.getGuildByPlayer(p.getUniqueId());
        if (victimG == null) return;
        Optional<War> wOpt = warManager.getActiveWarOf(victimG.getTag());
        if (wOpt.isEmpty()) return;
        War w = wOpt.get();
        w.getStats(victimG.getTag()).addDeath();
        if (killer == null) return;
        Guild killerG = guildManager.getGuildByPlayer(killer.getUniqueId());
        if (killerG != null && w.isParticipant(killerG.getTag()) && !killerG.getTag().equals(victimG.getTag())) {
            w.getStats(killerG.getTag()).addKill();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        ItemStack item = e.getItem().getItemStack();
        if (!BannerItem.isBanner(item)) return;
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) { e.setCancelled(true); return; }
        e.setCancelled(true);
        e.getItem().remove();
        warManager.handleBannerPickup(player, item);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (BannerItem.isBanner(p.getInventory().getHelmet())) warManager.handleBannerDeath(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (BannerItem.isBanner(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cNie mozesz wyrzucic sztandaru!");
        }
    }

    @EventHandler
    public void onHoloManipulate(PlayerArmorStandManipulateEvent e) {
        ArmorStand as = e.getRightClicked();
        if (as.getScoreboardTags().contains(EggHologram.TAG) || as.hasMetadata(EggHologram.META_KEY)) e.setCancelled(true);
    }

    @EventHandler
    public void onWarGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();
        if (title == null) return;

        boolean isWarGui = title.equals(WarGui.TITLE_MAIN)
                || title.equals(WarGui.TITLE_CHALLENGE)
                || title.equals(WarGui.TITLE_DURATION)
                || title.startsWith(WarGui.TITLE_STATS)
                || title.equals(WarGui.TITLE_HISTORY)
                || title.equals(WarGui.TITLE_PICK_STATS);
        if (!isWarGui) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String name = meta.getDisplayName();
        if (name == null) return;

        if (title.equals(WarGui.TITLE_MAIN)) {
            if (name.contains("Wyzwij")) WarGui.openChallengeList(player, guildManager, warManager);
            else if (name.contains("Statystyki")) WarGui.openStatsPicker(player, guildManager, warManager);
            else if (name.contains("Historia")) WarGui.openHistory(player, warManager);
            return;
        }

        if (title.equals(WarGui.TITLE_CHALLENGE)) {
            if (clicked.getType() == Material.WHITE_BANNER && name.startsWith("§e")) {
                WarGui.openDurationPicker(player, name.replace("§e", "").trim());
            }
            return;
        }

        if (title.equals(WarGui.TITLE_DURATION)) {
            List<String> lore = meta.getLore();
            if (lore == null) return;
            for (String line : lore) {
                if (line.contains("TAG:")) {
                    String[] parts = line.replace("§8", "").trim().split(":");
                    if (parts.length >= 3) {
                        String tag = parts[1];
                        long hours = 1;
                        try { hours = Long.parseLong(parts[2]); } catch (NumberFormatException ignored) { }
                        hours = Math.max(1, Math.min(3, hours));
                        player.closeInventory();
                        warManager.declareWar(player, tag, hours * 3_600_000L);
                    }
                    return;
                }
            }
            return;
        }

        if (title.equals(WarGui.TITLE_PICK_STATS)) {
            if (name.startsWith("§e")) {
                String opp = name.replace("§e", "").trim();
                Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
                if (own != null) {
                    warManager.getActiveWarsOf(own.getTag()).stream()
                            .filter(w -> w.getOpponent(own.getTag()).equals(opp))
                            .findFirst()
                            .ifPresent(w -> WarGui.openStats(player, own, w));
                }
            }
            return;
        }

        if (name.contains("Powrót") || name.contains("Powrot")) WarGui.openMain(player, guildManager, warManager);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean banner = BannerItem.isBanner(current) || BannerItem.isBanner(cursor);
        if (!banner && !BannerItem.isBanner(player.getInventory().getHelmet())) return;

        ClickType type = e.getClick();
        if (type == ClickType.DROP || type == ClickType.CONTROL_DROP || type == ClickType.NUMBER_KEY) {
            if (BannerItem.isBanner(current) || BannerItem.isBanner(player.getInventory().getHelmet())) {
                e.setCancelled(true);
                player.sendMessage("§cNie mozesz zdjac sztandaru!");
                return;
            }
        }
        if (e.getSlot() == 39 || (e.getRawSlot() == 5 && e.getView().getType().name().contains("CRAFTING"))) {
            if (BannerItem.isBanner(current) || BannerItem.isBanner(cursor)) {
                e.setCancelled(true);
                player.sendMessage("§cNie mozesz zdjac sztandaru!");
            }
        }
        if (BannerItem.isBanner(current) && e.isShiftClick()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        if (!warManager.isBannerCarrier(e.getPlayer().getUniqueId())) return;
        warManager.tryCompleteConquest(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Guild g = guildManager.getGuildByPlayer(e.getPlayer().getUniqueId());
        if (g == null) return;
        Optional<War> wOpt = warManager.getActiveWarOf(g.getTag());
        if (wOpt.isEmpty()) return;
        WarStats s = wOpt.get().getStats(g.getTag());
        Material type = e.getItem().getType();
        if (type == Material.ENCHANTED_GOLDEN_APPLE) s.addKox();
        else if (type == Material.GOLDEN_APPLE || type.name().contains("POTION") || type == Material.GOLDEN_CARROT) s.addRefill();

    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) return;
        Player p = e.getPlayer();
        Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
        if (g == null) return;
        warManager.getActiveWarOf(g.getTag()).ifPresent(w -> w.getStats(g.getTag()).addPearl());
    }
}

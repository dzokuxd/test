package pl.gildie.monument.listeners;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.monument.BannerService;
import pl.gildie.monument.util.MonumentBannerItem;
import pl.gildie.monument.util.MonumentMsg;

public class MonumentBannerLockListener implements Listener {
    private final JavaPlugin plugin;
    private final GuildManager guildManager;

    public MonumentBannerLockListener(JavaPlugin p, GuildManager gm) {
        this.plugin = p;
        this.guildManager = gm;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (MonumentBannerItem.isBanner(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (MonumentBannerItem.isBanner(e.getCurrentItem()) || MonumentBannerItem.isBanner(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent e) {
        if (MonumentBannerItem.isBanner(e.getOldCursor())) { e.setCancelled(true); return; }
        for (ItemStack it : e.getNewItems().values()) if (MonumentBannerItem.isBanner(it)) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent e) {
        if (MonumentBannerItem.isBanner(e.getItem().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (MonumentBannerItem.isBanner(e.getMainHandItem()) || MonumentBannerItem.isBanner(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (MonumentBannerItem.isBanner(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Item it = e.getItem();
        ItemStack st = it.getItemStack();
        if (!MonumentBannerItem.isBanner(st)) return;
        e.setCancelled(true);

        String owner = MonumentBannerItem.getOwnerGuild(st);
        Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
        if (g == null || owner == null || !g.getTag().equalsIgnoreCase(owner)) return;
        if (MonumentBannerItem.isBanner(p.getInventory().getHelmet())) {
            p.sendMessage(MonumentMsg.error("Masz juz sztandar monumentu!"));
            return;
        }
        BannerService.removeDroppedBannerData(it);
        it.remove();
        BannerService.giveBanner(p, st);
        p.sendMessage(MonumentMsg.cmd("Podniosles sztandar monumentu!"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { BannerService.dropHelmetBanner(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        ItemStack h = p.getInventory().getHelmet();
        if (!MonumentBannerItem.isBanner(h)) return;
        e.getDrops().removeIf(MonumentBannerItem::isBanner);
        p.getInventory().setHelmet(null);
        BannerService.dropBannerItem(p.getLocation(), h);
    }
}

package pl.gildie.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.GildieModule;
import pl.gildie.commands.GCommand;

public class InviteWandListener implements Listener {
    private final GildieModule module;
    private final GCommand gCommand;

    public InviteWandListener(GildieModule module, GCommand gCommand) {
        this.module = module;
        this.gCommand = gCommand;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !GCommand.WAND_NAME.equals(meta.getDisplayName())) return;
        event.setCancelled(true);
        Long exp = module.getInviteWandUsers().get(player.getUniqueId());
        if (exp == null || System.currentTimeMillis() > exp) {
            player.sendMessage("\u00a7cRozdzka wygasla.");
            player.getInventory().setItemInMainHand(null);
            module.getInviteWandUsers().remove(player.getUniqueId());
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) return;
        gCommand.inviteFromWand(player, target);
    }
}

package pl.gildie.sector;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import pl.gildie.Const;
import pl.gildie.GildieModule;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;

public class SectorBridgeListener implements Listener {
    private static final String PENDING_KEY = "gildie-pending-tp:";
    private final GildieModule module;
    private final GuildManager guildManager;

    public SectorBridgeListener(GildieModule module, GuildManager guildManager) {
        this.module = module;
        this.guildManager = guildManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage().toLowerCase().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) return;
        String cmd = parts[0];
        String sub = parts.length > 1 ? parts[1] : "";

        boolean isG = cmd.equals("g") || cmd.equals("gildia") || cmd.equals("guild");
        boolean isWar = cmd.equals("wojna") || cmd.equals("wojny") || cmd.equals("war");
        if (!isG && !isWar) return;

        boolean onGuild = SectorProvider.isGuildSector(module.plugin());
        if (!onGuild && (isWar || sub.equals("zaloz") || sub.equals("ubw"))) {
            event.setCancelled(true);
            player.sendMessage("\u00a7cTa komenda dziala tylko na sektorze gildyjnym (\u00a7e" + Const.GUILD_SECTOR + "\u00a7c).");
            return;
        }

        if (isG && (sub.equals("dom") || sub.equals("bw")) && !onGuild) {
            event.setCancelled(true);
            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild == null) { player.sendMessage("\u00a7cNie masz gildii."); return; }
            Location target = sub.equals("dom") ? guild.getHome() : guild.getRaidBase();
            if (target == null) {
                player.sendMessage(sub.equals("dom") ? "\u00a7cBrak domu gildii." : "\u00a7cBrak aktywnej bazy.");
                return;
            }
            JsonObject loc = new JsonObject();
            loc.addProperty("world", target.getWorld().getName());
            loc.addProperty("x", target.getX()); loc.addProperty("y", target.getY()); loc.addProperty("z", target.getZ());
            loc.addProperty("yaw", target.getYaw()); loc.addProperty("pitch", target.getPitch());
            module.getRedisService().setWithTtlAsync(PENDING_KEY + player.getUniqueId(), loc.toString(), 120);
            module.sector().getTransferStateMachine().initiateTransfer(player, Const.GUILD_SECTOR);
        }
    }
}

package pl.dzoku.sectorsystem.tablist;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.sectorsystem.common.messaging.SectorMessage;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SectorTablist {
    private final SectorSystemPlugin plugin;
    private final Map<String, Set<String>> remotePlayers = new ConcurrentHashMap<>();

    public SectorTablist(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::publishLocal, 20L, 20L * 3);
        plugin.getNatsService().subscribe("tablist_update", this::onRemoteUpdate);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshHeaders, 40L, 40L);
    }

    private void publishLocal() {
        Set<String> names = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        SectorMessage msg = new SectorMessage(SectorMessage.Type.TABLIST_UPDATE);
        msg.setFromSector(plugin.getCurrentSectorId());
        msg.setPayload(String.join(",", names));
        try { plugin.getNatsService().publish(msg); } catch (Exception ignored) {}
    }

    private void onRemoteUpdate(SectorMessage msg) {
        if (msg.getType() != SectorMessage.Type.TABLIST_UPDATE) return;
        if (msg.getFromSector() == null || msg.getFromSector().equals(plugin.getCurrentSectorId())) return;
        Set<String> set = new HashSet<>();
        if (msg.getPayload() != null && !msg.getPayload().isEmpty()) {
            for (String n : msg.getPayload().split(",")) {
                if (!n.isBlank()) set.add(n.trim());
            }
        }
        remotePlayers.put(msg.getFromSector(), set);
    }

    private void refreshHeaders() {
        int local = Bukkit.getOnlinePlayers().size();
        int global = local;
        try {
            global = plugin.getRedisService().getGlobalPlayerCount();
            if (global < local) global = local;
        } catch (Exception ignored) {}

        Component header = Component.text("§a§lSectorSystem\n§7Sektor: §e" + plugin.getCurrentSectorId());
        Component footer = Component.text("§7Online: §a" + local + " §7| Global: §a" + global);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    public void onQuit(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::publishLocal);
    }
}

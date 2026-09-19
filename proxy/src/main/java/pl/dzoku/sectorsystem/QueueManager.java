package pl.dzoku.sectorsystem;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.sectorsystem.common.mysql.MySQLService;

import java.util.Optional;
import java.util.UUID;

public class QueueManager {
    private final SectorProxyPlugin plugin;

    public QueueManager(SectorProxyPlugin plugin) {
        this.plugin = plugin;
    }

    public void enqueueAfterAuth(Player player) {
        UUID uuid = player.getUniqueId();
        MySQLService mysql = plugin.getMysql();
        String targetSector = "guild"; // Domyślnie dla nowych graczy
        
        if (mysql != null && mysql.isEnabled()) {
            Optional<String> lastSectorOpt = mysql.getLastSector(uuid);
            if (lastSectorOpt.isPresent() && !lastSectorOpt.get().trim().isEmpty()) {
                targetSector = lastSectorOpt.get().trim();
            }
        }
        
        plugin.getLogger().info("Gracz " + player.getUsername() + " po autoryzacji kierowany do: " + targetSector);
        player.sendMessage(Component.text("Łączenie z sektorem: " + targetSector + "...", NamedTextColor.GREEN));
        
        if (plugin.getOrchestrator() != null) {
            plugin.getOrchestrator().enqueuePlayer(player, targetSector);
        } else {
            player.sendMessage(Component.text("Błąd: System kolejkowania jest niedostępny.", NamedTextColor.RED));
        }
    }
}

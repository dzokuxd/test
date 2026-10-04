package pl.dzoku.sectorsystem.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;

public class ServerListListener {
    private final SectorProxyPlugin plugin;
    private final ProxyServer server;

    public ServerListListener(SectorProxyPlugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing ping = event.getPing();
        ServerPing.Builder builder = ping.asBuilder();

        MySQLService mysql = plugin.getMysql();

        // 1. MOTD
        String motd = "A Velocity Server";
        if (mysql != null && mysql.isEnabled()) {
            String customMotd = mysql.getMotd();
            if (customMotd != null && !customMotd.isBlank()) {
                motd = customMotd;
            }

            // 2. Dodaj info o whitelist do MOTD
            if (mysql.isWhitelistEnabled()) {
                motd = motd + "\n§4Whitelisted";
            }
        }
        builder.description(Component.text(motd));

        event.setPing(builder.build());
    }
}
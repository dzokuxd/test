package pl.dzoku.sectorsystem.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Blokuje klienty starsze niz Minecraft 1.21 (protokol < 767).
 */
public class VersionGuardListener {

    private static final int MIN_PROTOCOL = 767;   // 1.21 / 1.21.1
    private static final String MIN_VERSION_NAME = "1.21";

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        int protocol = player.getProtocolVersion().getProtocol();

        if (protocol < MIN_PROTOCOL) {
            player.disconnect(Component.text()
                    .append(Component.text("Twoja wersja Minecraft jest za stara!", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Wymagana wersja: ", NamedTextColor.GRAY))
                    .append(Component.text(MIN_VERSION_NAME + " lub nowsza", NamedTextColor.GREEN))
                    .build());
        }
    }
}
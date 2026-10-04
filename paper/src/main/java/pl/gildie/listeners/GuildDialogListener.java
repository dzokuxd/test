package pl.gildie.listeners;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pl.gildie.commands.GuildCommand;

public class GuildDialogListener implements Listener {

    private final GuildCommand guildCommand;

    public GuildDialogListener(GuildCommand guildCommand) {
        this.guildCommand = guildCommand;
    }

    @EventHandler
    public void onDialogSubmit(PlayerCustomClickEvent event) {
        // Sprawdź czy kliknięto nasz przycisk
        if (!event.getIdentifier().equals(Key.key("twojplugin:confirm_guild"))) {
            return;
        }

        // Pobierz wpisany tag
        DialogResponseView view = event.getDialogResponseView();
        if (view == null) return;

        String tag = view.getText("guild_tag");
        Player player = ((PlayerGameConnection) event.getCommonConnection()).getPlayer();

        // Przekaż do dalszej obróbki
        guildCommand.processGuildCreation(player, tag);
    }
}
package pl.gildie.monument.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.gildie.Const;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.monument.GuildBonusManager;
import pl.gildie.monument.MonumentManager;
import pl.gildie.monument.util.MonumentBannerItem;
import pl.gildie.monument.util.MonumentMsg;

public class MonumentEggListener implements Listener {
    private final MonumentManager monumentManager;
    private final GuildManager guildManager;

    public MonumentEggListener(MonumentManager mm, GuildManager gm) {
        this.monumentManager = mm;
        this.guildManager = gm;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEggInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!e.getClickedBlock().getType().name().equalsIgnoreCase(Const.EGG_MATERIAL)) return;

        Player p = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();
        Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
        if (g == null) return;
        if (!g.isEggBlock(loc)) return;
        if (!MonumentBannerItem.isBanner(p.getInventory().getHelmet())) return;

        e.setCancelled(true);

        String type = MonumentBannerItem.getType(p.getInventory().getHelmet());
        String owner = MonumentBannerItem.getOwnerGuild(p.getInventory().getHelmet());
        if (!g.getTag().equalsIgnoreCase(owner)) {
            p.sendMessage(MonumentMsg.error("Ten sztandar nalezy do gildii " + owner + ", nie do Twojej!"));
            return;
        }

        if ("CENTER".equals(type)) {
            GuildBonusManager.addCenterBonus(g.getTag(), null);
            monumentManager.centerDelivered();
            p.sendMessage(MonumentMsg.title("Dostarczyles KORONE!").append(MonumentMsg.desc(" Twoja gildia ma bonus dropu na 60 min!")));
        } else {
            int cid = MonumentBannerItem.getCornerId(p.getInventory().getHelmet());
            String[] effects = {"SPEED", "FIRE_RESISTANCE", "REGENERATION", "HASTE"};
            String fx = (cid >= 1 && cid <= 4) ? effects[cid - 1] : "SPEED";
            GuildBonusManager.addCornerEffect(g.getTag(), fx);
            p.sendMessage(MonumentMsg.title("Dostarczyles sztandar narozny!").append(MonumentMsg.desc(" Efekt " + fx + " na 30 min!")));
        }

        p.getInventory().setHelmet(null);
    }
}

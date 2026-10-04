package pl.gildie.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.gildie.Const;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.managers.GuildBonusManager;
import pl.gildie.managers.MonumentManager;
import pl.gildie.util.MonumentBannerItem;
import pl.gildie.util.MonumentMsg;

public class MonumentEggListener implements Listener {
    private final MonumentManager monumentManager;
    private final GuildManager guildManager;

    public MonumentEggListener(MonumentManager mm, GuildManager gm) {
        this.monumentManager = mm;
        this.guildManager = gm;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEggInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
        // Tylko kliknięcia na bloki
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        // Tylko dragon egg
        if (!e.getClickedBlock().getType().name().equalsIgnoreCase(Const.EGG_MATERIAL)) return;

        Location eggLoc = e.getClickedBlock().getLocation();

        // ── NAJWAŻNIEJSZE: ANULUJ EVENT NATYCHMIAST (żeby jajko nie uciekło) ──
        e.setCancelled(true);

        // Sprawdź czy gracz ma sztandar na głowie
        if (!MonumentBannerItem.isBanner(p.getInventory().getHelmet())) {
            return; // kliknął jajko bez sztandaru - nic się nie dzieje, jajko nie ucieka
        }

        // Znajdź gildię gracza
        if (g == null) {
            p.sendMessage(MonumentMsg.error("Musisz byc w gildii, aby dostarczyc sztandar!"));
            return;
        }

        // Sprawdź czy to jajko TEJ gildii (porównaj z zapisaną pozycją)
        Location savedEgg = g.getEggLocation();
        if (savedEgg == null) {
            p.sendMessage(MonumentMsg.error("Twoja gildia nie ma jajka!"));
            return;
        }

        if (!locationsMatch(eggLoc, savedEgg)) {
            p.sendMessage(MonumentMsg.error("To nie jest jajko Twojej gildii!"));
            return;
        }

        // Sprawdź właściciela sztandaru
        String type = MonumentBannerItem.getType(p.getInventory().getHelmet());

        // ── LOGIKA DOSTARCZENIA ──
        if ("CENTER".equals(type)) {
            GuildBonusManager.addCenterBonus(g.getTag());
            monumentManager.centerDelivered();
            p.sendMessage(MonumentMsg.title("Dostarczyles KORONE!").append(MonumentMsg.desc(" Twoja gildia ma bonus dropu na 60 min!")));
        } else {
            int cid = MonumentBannerItem.getCornerId(p.getInventory().getHelmet());
            String[] effects = {"SPEED", "FIRE_RESISTANCE", "REGENERATION", "HASTE"};
            String fx = (cid >= 1 && cid <= 4) ? effects[cid - 1] : "SPEED";
            GuildBonusManager.addCornerEffect(g.getTag(), fx);
            p.sendMessage(MonumentMsg.title("Dostarczyles sztandar narozny!").append(MonumentMsg.desc(" Efekt " + fx + " na 30 min!")));
        }

        // Usuń sztandar z głowy
        p.getInventory().setHelmet(null);
    }

    // Pomocnicze: porównanie lokalizacji (block coords, nie exact doubles)
    private boolean locationsMatch(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() != b.getWorld()) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
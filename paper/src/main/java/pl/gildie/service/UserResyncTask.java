package pl.gildie.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.gildie.GildieModule;
import pl.gildie.model.Guild;

import java.util.Objects;
import java.util.UUID;

public class UserResyncTask {
    private final GildieModule module;
    public UserResyncTask(GildieModule module) { this.module = module; }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(module.plugin(), this::tick, 20L * 30, 20L * 60);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { sync(p); } catch (Exception ignored) { }
        }
    }

    private void sync(Player player) {
        UUID uuid = player.getUniqueId();
        Guild g = module.getGuildManager().getGuildByPlayer(uuid);
        String wantTag = g != null ? g.getTag() : null;
        String wantRole = g == null ? null : g.isOwner(uuid) ? "LEADER" : g.isDeputy(uuid) ? "DEPUTY" : "MEMBER";
        String[] cur = module.getUserRepository().fetchGuildRole(uuid);
        if (cur == null) {
            module.getUserRepository().upsertIdentity(uuid, player.getName());
            cur = module.getUserRepository().fetchGuildRole(uuid);
        }
        if (cur != null && Objects.equals(cur[0], wantTag) && Objects.equals(cur[1], wantRole)) return;
        if (wantTag == null) module.getUserRepository().clearGuild(uuid);
        else module.getUserRepository().setGuild(uuid, wantTag, wantRole);
    }
}

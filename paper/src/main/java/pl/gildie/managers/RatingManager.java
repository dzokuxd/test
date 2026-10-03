package pl.gildie.managers;

import org.bukkit.entity.Player;
import pl.gildie.GildieModule;
import pl.gildie.model.Guild;
import java.util.*;
import java.util.stream.Collectors;

public class RatingManager {
    private final GildieModule module;
    private boolean ratingActive = false;

    public RatingManager(GildieModule module) { this.module = module; }
    public boolean isRatingActive() { return ratingActive; }
    public void setRatingActive(boolean active) { this.ratingActive = active; }

    public boolean adminRate(String targetTag, int rating) {
        if (!ratingActive || rating < 1 || rating > 5) return false;
        Guild target = module.getGuildManager().getGuild(targetTag);
        if (target == null) return false;
        target.addAdminRating(rating);
        module.getGuildManager().markDirty(target.getTag());
        module.getGuildManager().save();
        return true;
    }

    public PlayerRatingResult playerRate(Player player, String targetTag, int rating) {
        if (!ratingActive) return PlayerRatingResult.RATING_INACTIVE;
        if (rating < 1 || rating > 5) return PlayerRatingResult.INVALID_RATING;

        Guild playerGuild = module.getGuildManager().getGuildByPlayer(player.getUniqueId());
        if (playerGuild == null) return PlayerRatingResult.NOT_IN_GUILD;

        Guild targetGuild = module.getGuildManager().getGuild(targetTag);
        if (targetGuild == null) return PlayerRatingResult.TARGET_NOT_FOUND;
        if (playerGuild.getTag().equals(targetGuild.getTag())) return PlayerRatingResult.CANNOT_RATE_OWN_GUILD;

        String currentRatedGuild = playerGuild.getRatedGuildTag();
        if (currentRatedGuild == null) {
            if (playerGuild.isOwner(player.getUniqueId())) {
                // Lider głosuje pierwszy - ustaw cel
                playerGuild.setRatedGuildTag(targetGuild.getTag());
                playerGuild.addPlayerVote(player.getUniqueId(), rating);

                // Dodaj ocenę DO gildii target (NIE do playerGuild!)
                targetGuild.addReceivedPlayerRating(rating);

                module.getGuildManager().markDirty(playerGuild.getTag());
                module.getGuildManager().markDirty(targetGuild.getTag());
                module.getGuildManager().save();
                return PlayerRatingResult.SUCCESS;
            } else {
                return PlayerRatingResult.LEADER_MUST_VOTE_FIRST;
            }
        } else {
            if (!currentRatedGuild.equals(targetGuild.getTag())) return PlayerRatingResult.ALREADY_RATING_DIFFERENT_GUILD;
            if (playerGuild.hasPlayerVoted(player.getUniqueId())) return PlayerRatingResult.ALREADY_VOTED;

            // Gracz może głosować
            playerGuild.addPlayerVote(player.getUniqueId(), rating);

            // Dodaj ocenę DO gildii target
            targetGuild.addReceivedPlayerRating(rating);

            module.getGuildManager().markDirty(playerGuild.getTag());
            module.getGuildManager().markDirty(targetGuild.getTag());
            module.getGuildManager().save();
            return PlayerRatingResult.SUCCESS;
        }
    }

    public List<GuildRating> getTopAdminRatings(int limit) {
        return module.getGuildManager().getAll().stream()
                .filter(g -> g.getAdminRatingSum() > 0)
                .map(g -> new GuildRating(g.getTag(), g.getAdminRatingSum()))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<GuildRating> getTopPlayerRatings(int limit) {
        return module.getGuildManager().getAll().stream()
                .filter(g -> g.getReceivedPlayerRatingCount() > 0)
                .map(g -> new GuildRating(g.getTag(), g.getReceivedPlayerRatingAverage()))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static class GuildRating {
        public final String guildTag;
        public final double score;
        public GuildRating(String guildTag, double score) { this.guildTag = guildTag; this.score = score; }
    }

    public enum PlayerRatingResult {
        SUCCESS("§aPomyślnie oceniono gildię!"),
        RATING_INACTIVE("§cOcenianie jest wyłączone!"),
        INVALID_RATING("§cOcena musi być między 1 a 5!"),
        NOT_IN_GUILD("§cNie jesteś w gildii!"),
        TARGET_NOT_FOUND("§cNie znaleziono gildii!"),
        CANNOT_RATE_OWN_GUILD("§cNie możesz ocenić własnej gildii!"),
        LEADER_MUST_VOTE_FIRST("§cNajpierw lider musi ocenić gildię!"),
        ALREADY_RATING_DIFFERENT_GUILD("§cTwoja gildia już ocenia inną gildię!"),
        ALREADY_VOTED("§cJuż głosowałeś!");
        private final String message;
        PlayerRatingResult(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
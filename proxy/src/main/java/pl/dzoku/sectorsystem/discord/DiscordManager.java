package pl.dzoku.sectorsystem.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

public class DiscordManager {
    private final SectorProxyPlugin plugin;
    private JDA jda;
    private final String token;
    private final long guildId;
    private final long verifiedRoleId;
    private final long leaderRoleId;
    private final long staffRoleId;

    public DiscordManager(SectorProxyPlugin plugin, String token, long guildId,
                          long verifiedRoleId, long leaderRoleId, long staffRoleId) {
        this.plugin = plugin;
        this.token = token;
        this.guildId = guildId;
        this.verifiedRoleId = verifiedRoleId;
        this.leaderRoleId = leaderRoleId;
        this.staffRoleId = staffRoleId;
    }

    public void startBot() {
        if (token == null || token.equals("TWÓJ_TOKEN_BOTA")) {
            plugin.getLogger().info("[Discord] Token nie ustawiony, bot wyłączony.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .enableCache(CacheFlag.MEMBER_OVERRIDES)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(new DiscordListener(plugin))
                    .build()
                    .awaitReady();
            plugin.getLogger().info("[Discord] Bot uruchomiony jako: " + jda.getSelfUser().getAsTag());

            jda.updateCommands().addCommands(
                    net.dv8tion.jda.api.interactions.commands.build.Commands.slash("gracz", "Pokazuje statystyki gracza")
                            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "nick", "Nick gracza", true)
            ).queue();

            jda.addEventListener(new DiscordCommands(plugin));
        } catch (Exception e) {
            plugin.getLogger().severe("[Discord] Błąd uruchamiania bota: " + e.getMessage());
        }
    }

    public void shutdown() { if (jda != null) jda.shutdown(); }
    public JDA getJda() { return jda; }
    public Guild getGuild() { return jda != null ? jda.getGuildById(guildId) : null; }
    public long getVerifiedRoleId() { return verifiedRoleId; }
    public long getLeaderRoleId() { return leaderRoleId; }
    public long getStaffRoleId() { return staffRoleId; }

    public void giveRole(String discordId, long roleId) {
        Guild guild = getGuild(); if (guild == null) return;
        Member member = guild.getMemberById(discordId); if (member == null) return;
        Role role = guild.getRoleById(roleId);
        if (role != null && !member.getRoles().contains(role)) guild.addRoleToMember(member, role).queue();
    }

    public void removeRole(String discordId, long roleId) {
        Guild guild = getGuild(); if (guild == null) return;
        Member member = guild.getMemberById(discordId); if (member == null) return;
        Role role = guild.getRoleById(roleId);
        if (role != null && member.getRoles().contains(role)) guild.removeRoleFromMember(member, role).queue();
    }

    public boolean hasRole(String discordId, long roleId) {
        Guild guild = getGuild(); if (guild == null) return false;
        Member member = guild.getMemberById(discordId); if (member == null) return false;
        Role role = guild.getRoleById(roleId);
        return role != null && member.getRoles().contains(role);
    }

    public boolean isVerified(String discordId) {
        return hasRole(discordId, verifiedRoleId);
    }
}
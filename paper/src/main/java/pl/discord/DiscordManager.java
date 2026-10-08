package pl.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.configuration.file.FileConfiguration;
import pl.dzoku.sectorsystem.SectorSystemPlugin;

public class DiscordManager {
    private final SectorSystemPlugin plugin;
    private JDA jda;
    private FileConfiguration config;

    public DiscordManager(SectorSystemPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void startBot() {
        if (!config.getBoolean("discord.enabled", true)) {
            plugin.getLogger().info("Moduł Discord jest wyłączony w config.yml");
            return;
        }

        String token = config.getString("discord.bot-token");
        if (token == null || token.equals("TWÓJ_TOKEN_BOTA")) {
            plugin.getLogger().severe("Nie ustawiono tokenu bota w config.yml!");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS
                    )
                    .enableCache(CacheFlag.MEMBER_OVERRIDES)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(new DiscordListener(plugin))
                    .build()
                    .awaitReady();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("Tworzenie embedów Discord...");
                new pl.discord.DiscordEmbedManager(plugin).createAllEmbeds();
            }, 100L);

            plugin.getLogger().info("✓ Bot Discord uruchomiony jako: " + jda.getSelfUser().getAsTag());

        } catch (Exception e) {
            plugin.getLogger().severe("Nie udało się uruchomić bota Discord: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    public JDA getJda() { return jda; }

    public Guild getGuild() {
        return jda != null ? jda.getGuildById(config.getLong("discord.guild-id")) : null;
    }

    public void giveRole(String discordId, long roleId) {
        Guild guild = getGuild();
        if (guild == null) return;
        Member member = guild.getMemberById(discordId);
        if (member == null) return;
        Role role = guild.getRoleById(roleId);
        if (role != null && !member.getRoles().contains(role)) {
            guild.addRoleToMember(member, role).queue();
        }
    }

    public void removeRole(String discordId, long roleId) {
        Guild guild = getGuild();
        if (guild == null) return;
        Member member = guild.getMemberById(discordId);
        if (member == null) return;
        Role role = guild.getRoleById(roleId);
        if (role != null && member.getRoles().contains(role)) {
            guild.removeRoleFromMember(member, role).queue();
        }
    }

    public boolean hasRole(String discordId, long roleId) {
        Guild guild = getGuild();
        if (guild == null) return false;
        Member member = guild.getMemberById(discordId);
        if (member == null) return false;
        Role role = guild.getRoleById(roleId);
        return role != null && member.getRoles().contains(role);
    }

    public boolean isVerified(String discordId) {
        return hasRole(discordId, config.getLong("discord.roles.verified"));
    }

}
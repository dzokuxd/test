package pl.gildie;

public final class Const {
    public static final String DB_HOST = "mysql.titanaxe.com";
    public static final int    DB_PORT = 3306;
    public static final String DB_USER = "srv325281";
    public static final String DB_PASS = "Vaw4wCB4";
    public static final String DB_NAME = "srv325281";

    public static final String GUILD_SECTOR = "guild";
    public static final String NATS_URL     = "nats://localhost:4222";

    public static final int    REGION_RADIUS   = 50;
    public static final int    ALLIANCE_LIMIT  = 15;
    public static final int    TP_HOME_SECONDS = 15;
    public static final int    INVITE_DIAMOND  = 4;

    public static final int    WAR_EGG_HITS        = 500;
    public static final int    WAR_EGG_REGEN       = 5;
    public static final int    WAR_EGG_ALERT_EVERY = 50;
    public static final int    WAR_MIN_HOURS = 1;
    public static final int    WAR_MAX_HOURS = 3;
    public static final int    WAR_MAX_ACTIVE_PER_GUILD = 2;
    public static final int    WAR_RANK_START_POINTS = 1000;
    public static final int    WAR_TRANSFER_BASE_PERCENT = 5;
    public static final int    WAR_TRANSFER_CONQUEST_PERCENT = 10;
    public static final int    WAR_TRANSFER_KILLS_PERCENT = 15;
    public static final int    WAR_TRANSFER_CAP_PERCENT = 30;

    public static final int    TNT_START_HOUR = 16;
    public static final int    TNT_END_HOUR   = 21;
    public static final String TNT_TIMEZONE   = "Europe/Warsaw";
    public static final int    TNT_BUILD_LOCK_SECONDS = 60;
    public static final int    TNT_BUILD_LOCK_WAR_SECONDS = 180;

    public static final int    EGG_ROOM_Y      = 40;
    public static final int    EGG_ROOM_RADIUS = 3;
    public static final String EGG_MATERIAL    = "DRAGON_EGG";

    public static final int  REGEN_AUTO_Y = 60;

    public static final long   RAID_DURATION_MS = 3_600_000L;
    public static final long   RAID_COOLDOWN_MS = 3_600_000L;
    public static final double RAID_NEAR_BLOCKS = 30.0;
    public static final int    DISPENSER_HITS_REQUIRED = 5;
    public static final int    PING_SCOREBOARD_SECONDS = 20;

    // ── MONUMENT ───────────────────────────────────────────────────────────
    public static final int     MONUMENT_CRYSTAL_HP = 500;
    public static final int     MONUMENT_PROTECTION_RADIUS = 48;
    public static final int     MONUMENT_SHOP_RADIUS = 24;
    public static final int     MONUMENT_POINTS_CENTER = 3;
    public static final int     MONUMENT_POINTS_CORNER = 1;
    public static final long    MONUMENT_HIT_COOLDOWN_MS = 1000L;
    public static final int     MONUMENT_CENTER_X = 0;
    public static final int     MONUMENT_CENTER_Z = 0;
    public static final int     MONUMENT_CENTER_Y = -1;
    public static final int     MONUMENT_CORNER_OFFSET = 20;
    public static final int     MONUMENT_CENTER_SPAWN_HOUR = 18;
    public static final boolean MONUMENT_BANNER_CARRIER_WP = true;
    public static final boolean MONUMENT_BANNER_GLOBAL_WP = true;
    public static final int     MONUMENT_BANNER_WP_COLOR = 0xFF5555;
    public static final long    MONUMENT_CENTER_BONUS_MS = 3_600_000L;
    public static final long    MONUMENT_CORNER_EFFECT_MS = 1_800_000L;
    public static final int     MONUMENT_CENTER_DROP_BONUS_PERCENT = 20;
    public static final int     MONUMENT_HASTE_EXTRA_DROP_PERCENT = 3;
    public static final int     MONUMENT_SPEED_AMPLIFIER = 0;
    public static final int     MONUMENT_FIRE_RESIST_AMPLIFIER = 0;
    public static final int     MONUMENT_REGEN_AMPLIFIER = 0;
    public static final int     MONUMENT_HASTE_AMPLIFIER = 0;
    public static final int     MONUMENT_BOSSBAR_MINUTES = 10;
    public static final String[] MONUMENT_TOP_REWARDS = {
            "give %player% diamond 32",
            "give %player% diamond 16",
            "give %player% diamond 8",
            "give %player% diamond 4",
            "give %player% diamond 2"
    };

    private Const() {}
}

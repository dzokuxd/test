package pl.gildie.monument.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MonumentMsg {
    public static final TextColor DESC = TextColor.color(0xAAAAAA);
    public static final TextColor CMD = TextColor.color(0xFFAA00);

    private MonumentMsg() {}

    public static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }
    public static Component error(String s) { return Component.text(s, NamedTextColor.RED); }
    public static Component desc(String s) { return Component.text(s, DESC); }
    public static Component cmd(String s) { return Component.text(s, CMD); }
    public static Component title(String s) { return Component.text(s, TextColor.color(0xFFCC00)); }
}

package pl.gildie.util;

public class TimeUtil {
    public static long parseTime(String time) {
        if (time.equalsIgnoreCase("perm") || time.equalsIgnoreCase("p") || time.equalsIgnoreCase("permanent")) {
            return 0;
        }
        try {
            long amount = Long.parseLong(time.substring(0, time.length() - 1));
            char unit = time.charAt(time.length() - 1);
            return switch (Character.toLowerCase(unit)) {
                case 's' -> amount * 1000L;
                case 'm' -> amount * 60000L;
                case 'h' -> amount * 3600000L;
                case 'd' -> amount * 86400000L;
                case 'w' -> amount * 604800000L;
                case 'y' -> amount * 31536000000L;
                default -> throw new IllegalArgumentException("Nieznana jednostka czasu: " + unit);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Nieprawidłowy format czasu. Użyj np. 1h, 3d, perm");
        }
    }

    public static String formatTime(long ms) {
        if (ms == 0) return "permanentnie";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " dni";
        if (hours > 0) return hours + " godzin";
        if (minutes > 0) return minutes + " minut";
        return seconds + " sekund";
    }
}
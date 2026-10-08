package pl.dzoku.sectorsystem.managers;

import com.velocitypowered.api.proxy.Player;
import pl.dzoku.sectorsystem.SectorProxyPlugin;
import pl.sectorsystem.common.mysql.MySQLService;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class AuthManager {
    private static final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> premiumCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    public static class AuthSession {
        public final UUID uuid;
        public final String username;
        public final boolean isPremium;
        public boolean isLoggedIn;
        public final String ip;
        public final long loginTime;

        public AuthSession(UUID uuid, String username, boolean isPremium, String ip) {
            this.uuid = uuid;
            this.username = username;
            this.isPremium = isPremium;
            this.isLoggedIn = isPremium;
            this.ip = ip;
            this.loginTime = System.currentTimeMillis();
        }
    }

    public static AuthSession getSession(String username) {
        return sessions.get(username.toLowerCase());
    }

    public static void createSession(Player player, boolean isPremium) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        sessions.put(player.getUsername().toLowerCase(), new AuthSession(player.getUniqueId(), player.getUsername(), isPremium, ip));
    }

    public static void removeSession(String username) {
        sessions.remove(username.toLowerCase());
        loginAttempts.remove(username.toLowerCase());
    }

    // ── NOWE: zapisuje KAŻDEGO gracza (premium i no-premium) przy wejściu ──
    /**
     * Premium: premium=TRUE, registered=TRUE, password=NULL (nie musi się rejestrować).
     * No-premium: premium=FALSE, registered=FALSE (dopiero /register ustawi hasło i registered=TRUE).
     */
    public static void ensurePlayerRow(Player player, boolean isPremium) {
        CompletableFuture.runAsync(() -> {
            MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
            if (mysql == null || !mysql.isEnabled()) return;
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            try (Connection c = mysql.getConnection()) {
                int updated;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE auth_players SET premium=?, lastIP=? WHERE username=?")) {
                    ps.setBoolean(1, isPremium);
                    ps.setString(2, ip);
                    ps.setString(3, player.getUsername());
                    updated = ps.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO auth_players (uuid, username, premium, password, registered, firstIP, lastIP, rememberIP) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ins.setString(1, player.getUniqueId().toString());
                        ins.setString(2, player.getUsername());
                        ins.setBoolean(3, isPremium);
                        ins.setString(4, "");          // premium nie ma hasła -> pusty string (kolumna NOT NULL)
                        ins.setBoolean(5, isPremium);  // premium = od razu zalogowany, cracked = czeka na /register
                        ins.setString(6, ip);
                        ins.setString(7, ip);
                        ins.setString(8, "");          // rememberIP -> pusty string zamiast NULL
                        ins.executeUpdate();
                    }
                }
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Blad zapisu auth_players: " + e.getMessage());
            }
        });
    }

    public static CompletableFuture<Boolean> isPremiumAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String lower = username.toLowerCase();
            if (premiumCache.containsKey(lower)) return premiumCache.get(lower);
            try {
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                int code = conn.getResponseCode();
                conn.disconnect();
                boolean isPremium = (code == 200);
                premiumCache.put(lower, isPremium);
                return isPremium;
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Błąd sprawdzania premium: " + e.getMessage());
                return false;
            }
        });
    }

    public static boolean isRegistered(String username) {
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT registered FROM auth_players WHERE username=?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean("registered");
                }
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Błąd isRegistered: " + e.getMessage());
            }
        }
        return false;
    }

    public static boolean isIPRemembered(Player player) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT rememberIP FROM auth_players WHERE username=?")) {
                ps.setString(1, player.getUsername());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String rememberIP = rs.getString("rememberIP");
                        return rememberIP != null && rememberIP.equals(ip);
                    }
                }
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Błąd isIPRemembered: " + e.getMessage());
            }
        }
        return false;
    }

    // ── POPRAWIONE: UPDATE jesli wiersz juz istnieje (bo ensurePlayerRow go stworzyl), inaczej INSERT ──
    public static boolean register(Player player, String password) {
        AuthSession session = getSession(player.getUsername());
        if (session == null) return false;
        if (session.isPremium) return false;

        String hashed = hashPassword(password);
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql == null || !mysql.isEnabled()) return false;
        try (Connection c = mysql.getConnection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE auth_players SET password=?, registered=TRUE, lastIP=? WHERE username=?")) {
                ps.setString(1, hashed);
                ps.setString(2, session.ip);
                ps.setString(3, player.getUsername());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO auth_players (uuid, username, premium, password, registered, firstIP, lastIP, rememberIP) " +
                                "VALUES (?, ?, FALSE, ?, TRUE, ?, ?, NULL)")) {
                    ins.setString(1, player.getUniqueId().toString());
                    ins.setString(2, player.getUsername());
                    ins.setString(3, hashed);
                    ins.setString(4, session.ip);
                    ins.setString(5, session.ip);
                    ins.executeUpdate();
                }
            }
            session.isLoggedIn = true;
            return true;
        } catch (Exception e) {
            SectorProxyPlugin.getInstance().getLogger().severe("Błąd rejestracji: " + e.getMessage());
            return false;
        }
    }

    public static boolean login(Player player, String password) {
        AuthSession session = getSession(player.getUsername());
        if (session == null || session.isPremium) return false;

        String lower = player.getUsername().toLowerCase();
        int attempts = loginAttempts.getOrDefault(lower, 0);
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            return false;
        }

        String hashed = hashPassword(password);
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT registered FROM auth_players WHERE username=? AND password=?")) {
                ps.setString(1, player.getUsername());
                ps.setString(2, hashed);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBoolean("registered")) {
                        session.isLoggedIn = true;
                        loginAttempts.remove(lower);
                        return true;
                    } else {
                        loginAttempts.put(lower, attempts + 1);
                    }
                }
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Błąd logowania: " + e.getMessage());
            }
        }
        return false;
    }

    public static boolean rememberIP(Player player) {
        AuthSession session = getSession(player.getUsername());
        if (session == null || !session.isLoggedIn) return false;

        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE auth_players SET rememberIP=? WHERE username=?")) {
                ps.setString(1, session.ip);
                ps.setString(2, player.getUsername());
                ps.executeUpdate();
                return true;
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Błąd rememberIP: " + e.getMessage());
            }
        }
        return false;
    }

    public static boolean changePassword(Player player, String oldPassword, String newPassword) {
        AuthSession session = getSession(player.getUsername());
        if (session == null || session.isPremium || !session.isLoggedIn) return false;

        String oldHashed = hashPassword(oldPassword);
        String newHashed = hashPassword(newPassword);

        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE auth_players SET password=? WHERE username=? AND password=?")) {
                ps.setString(1, newHashed);
                ps.setString(2, player.getUsername());
                ps.setString(3, oldHashed);
                int affected = ps.executeUpdate();
                return affected > 0;
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().severe("Błąd zmiany hasła: " + e.getMessage());
            }
        }
        return false;
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            SectorProxyPlugin.getInstance().getLogger().severe("Błąd hashowania: " + e.getMessage());
            return password;
        }
    }

    public static void shutdown() {
        sessions.clear();
        premiumCache.clear();
        loginAttempts.clear();
    }
}
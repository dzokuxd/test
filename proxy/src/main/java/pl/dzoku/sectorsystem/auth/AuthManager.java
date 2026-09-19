package pl.dzoku.sectorsystem.auth;

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
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public static boolean isRegistered(String username) {
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] MySQL jest dostępne");
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT registered FROM auth_players WHERE username=?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean("registered");
                }
            } catch (Exception e) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isIPRemembered(Player player) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] MySQL jest dostępne");
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
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean register(Player player, String password) {
        pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] Rejestracja gracza: " + player.getUsername());
        
        AuthSession session = getSession(player.getUsername());
        if (session == null) {
            pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Session jest NULL dla: " + player.getUsername());
            return false;
        }
        if (session.isPremium) {
            pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().warning("[DEBUG] Gracz jest PREMIUM: " + player.getUsername());
            return false;
        }
        pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] Session OK, isPremium=false");
        
        String hashed = hashPassword(password);
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] MySQL jest dostępne");
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO auth_players (uuid, username, premium, password, registered, firstIP, lastIP, rememberIP) VALUES (?, ?, FALSE, ?, TRUE, ?, ?, ?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getUsername());
                ps.setString(3, hashed);
                ps.setString(4, session.ip);
                ps.setString(5, session.ip);
                ps.setString(6, session.ip);
                ps.executeUpdate();
                session.isLoggedIn = true;
                return true;
            } catch (Exception e) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean login(Player player, String password) {
        AuthSession session = getSession(player.getUsername());
        if (session == null || session.isPremium) return false;

        // Sprawdź limit prób logowania
        String lower = player.getUsername().toLowerCase();
        int attempts = loginAttempts.getOrDefault(lower, 0);
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            return false;
        }

        String hashed = hashPassword(password);
        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] MySQL jest dostępne");
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
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean rememberIP(Player player) {
        AuthSession session = getSession(player.getUsername());
        if (session == null || !session.isLoggedIn) return false;

        MySQLService mysql = SectorProxyPlugin.getInstance().getMysql();
        if (mysql != null && mysql.isEnabled()) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] MySQL jest dostępne");
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE auth_players SET rememberIP=? WHERE username=?")) {
                ps.setString(1, session.ip);
                ps.setString(2, player.getUsername());
                ps.executeUpdate();
                return true;
            } catch (Exception e) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                e.printStackTrace();
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
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().info("[DEBUG] MySQL jest dostępne");
            try (Connection c = mysql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE auth_players SET password=? WHERE username=? AND password=?")) {
                ps.setString(1, newHashed);
                ps.setString(2, player.getUsername());
                ps.setString(3, oldHashed);
                int affected = ps.executeUpdate();
                return affected > 0;
            } catch (Exception e) {
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
                e.printStackTrace();
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
                pl.dzoku.sectorsystem.SectorProxyPlugin.getInstance().getLogger().severe("[DEBUG] Błąd rejestracji: " + e.getMessage());
                e.printStackTrace();
            return password;
        }
    }

    public static void shutdown() {
        sessions.clear();
        premiumCache.clear();
        loginAttempts.clear();
    }
}

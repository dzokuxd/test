package pl.dzoku.sectorsystem.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import pl.dzoku.sectorsystem.SectorProxyPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class SkinManager {
    private static final Gson gson = new Gson();
    private static final String[] RANDOM_SKINS = {
        "Notch", "jeb_", "Dinnerbone", "Grumm", "Dream", "Technoblade",
        "Philza", "TommyInnit", "Tubbo", "Ranboo", "WilburSoot", "Quackity"
    };

    public static void applySkin(Player player, boolean isPremium) {
        if (isPremium) {
            fetchAndApplyPremiumSkin(player);
        } else {
            applyRandomSkin(player);
        }
    }

    private static void fetchAndApplyPremiumSkin(Player player) {
        CompletableFuture.runAsync(() -> {
            try {
                String username = player.getUsername();
                String uuid = getMojangUUID(username);
                if (uuid == null) {
                    SectorProxyPlugin.getInstance().getLogger().warning("Nie udało się pobrać UUID dla: " + username);
                    return;
                }
                
                String[] skinData = getSkinData(uuid);
                if (skinData != null) {
                    applySkinToPlayer(player, skinData[0], skinData[1]);
                    SectorProxyPlugin.getInstance().getLogger().info("Skin ustawiony dla premium: " + username);
                }
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().warning("Błąd ustawiania skina dla " + player.getUsername() + ": " + e.getMessage());
            }
        });
    }

    private static void applyRandomSkin(Player player) {
        CompletableFuture.runAsync(() -> {
            try {
                String randomNick = RANDOM_SKINS[ThreadLocalRandom.current().nextInt(RANDOM_SKINS.length)];
                String uuid = getMojangUUID(randomNick);
                if (uuid == null) {
                    SectorProxyPlugin.getInstance().getLogger().info("Ustawiono domyślny skin dla cracked: " + player.getUsername());
                    return;
                }
                
                String[] skinData = getSkinData(uuid);
                if (skinData != null) {
                    applySkinToPlayer(player, skinData[0], skinData[1]);
                    SectorProxyPlugin.getInstance().getLogger().info("Losowy skin (" + randomNick + ") ustawiony dla cracked: " + player.getUsername());
                }
            } catch (Exception e) {
                SectorProxyPlugin.getInstance().getLogger().warning("Błąd ustawiania losowego skina dla " + player.getUsername() + ": " + e.getMessage());
            }
        });
    }

    private static String getMojangUUID(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();
                
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                return json.get("id").getAsString();
            }
            conn.disconnect();
        } catch (Exception e) {
            // Ignoruj
        }
        return null;
    }

    private static String[] getSkinData(String uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();
                
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                var properties = json.getAsJsonArray("properties");
                
                if (properties != null && properties.size() > 0) {
                    JsonObject texture = properties.get(0).getAsJsonObject();
                    String value = texture.get("value").getAsString();
                    String signature = texture.has("signature") ? texture.get("signature").getAsString() : null;
                    
                    return new String[]{value, signature};
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // Ignoruj
        }
        return null;
    }

    private static void applySkinToPlayer(Player player, String value, String signature) {
        try {
            GameProfile gameProfile = player.getGameProfile();
            
            // Stwórz nową listę właściwości bez starych tekstur
            List<GameProfile.Property> newProperties = new ArrayList<>();
            for (GameProfile.Property prop : gameProfile.getProperties()) {
                if (!prop.getName().equals("textures")) {
                    newProperties.add(prop);
                }
            }
            
            // Dodaj nowe właściwości skina
            if (signature != null && !signature.isEmpty()) {
                newProperties.add(new GameProfile.Property("textures", value, signature));
            } else {
                newProperties.add(new GameProfile.Property("textures", value, ""));
            }
            
            // Stwórz nowy profil
            GameProfile newProfile = new GameProfile(
                gameProfile.getId(),
                gameProfile.getName(),
                newProperties
            );
            
            // Zaktualizuj profil gracza
            player.setGameProfileProperties(newProfile.getProperties());
            
        } catch (Exception e) {
            SectorProxyPlugin.getInstance().getLogger().warning("Nie udało się ustawić skina dla " + player.getUsername() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

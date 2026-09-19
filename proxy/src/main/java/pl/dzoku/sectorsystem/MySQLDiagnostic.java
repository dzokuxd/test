package pl.dzoku.sectorsystem;

import pl.sectorsystem.common.config.SystemConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLDiagnostic {
    
    public static void runDiagnostic(SystemConfig.MySQLConfig config) {
        System.out.println("=== DIAGNOSTYKA MySQL ===");
        
        // 1. Sprawdź czy driver jest załadowany
        System.out.println("1. Sprawdzam MySQL Driver...");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("   ✓ Driver MySQL załadowany!");
        } catch (ClassNotFoundException e) {
            System.out.println("   ✗ Driver MySQL NIE znaleziony!");
            System.out.println("   Błąd: " + e.getMessage());
            System.out.println("   Rozwiązanie: Sprawdź czy mysql-connector-j jest w JAR");
            return;
        }
        
        // 2. Sprawdź konfigurację
        System.out.println("\n2. Konfiguracja:");
        System.out.println("   Host: " + config.getHost());
        System.out.println("   Port: " + config.getPort());
        System.out.println("   Database: " + config.getDatabase());
        System.out.println("   Username: " + config.getUsername());
        System.out.println("   Enabled: " + config.isEnabled());
        
        // 3. Spróbuj połączyć się bezpośrednio
        System.out.println("\n3. Test połączenia...");
        String url = "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase() 
                   + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC";
        
        System.out.println("   URL: " + url);
        
        try {
            Connection conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
            System.out.println("   ✓ Połączenie UDANE!");
            conn.close();
        } catch (SQLException e) {
            System.out.println("   ✗ Połączenie NIEUDANE!");
            System.out.println("   SQL State: " + e.getSQLState());
            System.out.println("   Error Code: " + e.getErrorCode());
            System.out.println("   Message: " + e.getMessage());
            
            if (e.getMessage() != null && e.getMessage().contains("No suitable driver")) {
                System.out.println("\n   >>> DRIVER NIE JEST DOSTĘPNY W CLASSPATH <<<");
                System.out.println("   Sprawdź czy mysql-connector-j.jar jest w proxy-2.0.0-all.jar");
            }
        }
        
        System.out.println("\n=== KONIEC DIAGNOSTYKI ===");
    }
}

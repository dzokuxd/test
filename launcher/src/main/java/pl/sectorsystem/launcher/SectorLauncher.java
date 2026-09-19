package pl.sectorsystem.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ulepszony launcher SectorSystem.
 * Funkcje:
 * - start / stop / status / restart
 * - osobne logi dla każdego procesu
 * - PID tracking
 * - konfiguracja JVM per sektor
 * - automatyczne tworzenie folderów
 */
public class SectorLauncher {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("launcher-config.json");
    private static final Path PIDS_PATH = Path.of("launcher-pids.json");
    private static final Path LOGS_DIR = Path.of("logs");

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       SectorSystem Launcher          ║");
        System.out.println("╚══════════════════════════════════════╝");

        LauncherConfig config = loadOrCreateConfig();
        Files.createDirectories(LOGS_DIR);

        String command = args.length > 0 ? args[0].toLowerCase() : "start";

        switch (command) {
            case "start" -> startAll(config);
            case "stop" -> stopAll(config);
            case "restart" -> {
                stopAll(config);
                Thread.sleep(3000);
                startAll(config);
            }
            case "status" -> status(config);
            case "help" -> printHelp();
            default -> {
                System.out.println("Nieznana komenda: " + command);
                printHelp();
            }
        }
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("Użycie: java -jar launcher.jar <komenda>");
        System.out.println();
        System.out.println("  start     – uruchamia Redis, NATS, Velocity i wszystkie sektory");
        System.out.println("  stop      – zatrzymuje wszystkie zarządzane procesy");
        System.out.println("  restart   – stop + start");
        System.out.println("  status    – pokazuje status procesów");
        System.out.println("  help      – ta pomoc");
        System.out.println();
    }

    // ==================== START ====================

    private static void startAll(LauncherConfig config) throws Exception {
        Map<String, Long> pids = loadPids();

        System.out.println("\n[1/4] Usługi bazowe...");
        startInfrastructure(config, pids);

        Thread.sleep(2000);

        System.out.println("\n[2/4] Velocity Proxy...");
        startJavaProcess("velocity", config.velocityJar, config.velocityDir,
                config.velocityJvmArgs, pids);

        Thread.sleep(config.delayAfterVelocityMs);

        System.out.println("\n[3/4] Sektory Paper...");
        for (Map.Entry<String, SectorEntry> entry : config.sectors.entrySet()) {
            String id = entry.getKey();
            SectorEntry sector = entry.getValue();
            System.out.println("  → " + id + " (" + sector.directory + ")");
            startJavaProcess("paper-" + id, config.paperJar, sector.directory,
                    sector.jvmArgs != null ? sector.jvmArgs : config.defaultPaperJvmArgs, pids);
            Thread.sleep(config.delayBetweenSectorsMs);
        }

        savePids(pids);

        System.out.println("\n[4/4] Gotowe!");
        System.out.println("Użyj: java -jar launcher.jar status");
        System.out.println("      java -jar launcher.jar stop");
    }

    private static void startInfrastructure(LauncherConfig config, Map<String, Long> pids) {
        if (config.startRedis) {
            try {
                ProcessBuilder pb = new ProcessBuilder(config.redisCommand);
                pb.redirectErrorStream(true);
                pb.redirectOutput(LOGS_DIR.resolve("redis.log").toFile());
                Process p = pb.start();
                pids.put("redis", p.pid());
                System.out.println("  ✓ Redis (PID " + p.pid() + ")");
            } catch (Exception e) {
                System.out.println("  ⚠ Redis: " + e.getMessage() + " (może już działa)");
            }
        }

        if (config.startNats) {
            try {
                ProcessBuilder pb = new ProcessBuilder(config.natsCommand);
                pb.redirectErrorStream(true);
                pb.redirectOutput(LOGS_DIR.resolve("nats.log").toFile());
                Process p = pb.start();
                pids.put("nats", p.pid());
                System.out.println("  ✓ NATS (PID " + p.pid() + ")");
            } catch (Exception e) {
                System.out.println("  ⚠ NATS: " + e.getMessage() + " (może już działa)");
            }
        }
    }

    private static void startJavaProcess(String name, String jar, String workDir,
                                         List<String> jvmArgs, Map<String, Long> pids) {
        try {
            Path dir = Path.of(workDir);
            Files.createDirectories(dir);

            List<String> command = new ArrayList<>();
            command.add("java");
            if (jvmArgs != null) command.addAll(jvmArgs);
            command.add("-jar");
            command.add(jar);
            if (name.startsWith("paper")) {
                command.add("nogui");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(LOGS_DIR.resolve(name + ".log").toFile());

            Process p = pb.start();
            pids.put(name, p.pid());
            System.out.println("  ✓ " + name + " (PID " + p.pid() + ")");
        } catch (Exception e) {
            System.err.println("  ✗ " + name + ": " + e.getMessage());
        }
    }

    // ==================== STOP ====================

    private static void stopAll(LauncherConfig config) throws Exception {
        Map<String, Long> pids = loadPids();

        if (pids.isEmpty()) {
            System.out.println("Brak zapisanych PID-ów. Próbuję pkill...");
            fallbackKill();
            return;
        }

        System.out.println("Zatrzymywanie procesów...");
        List<String> order = new ArrayList<>(pids.keySet());
        // Najpierw Paper, potem Velocity, na końcu infra
        order.sort((a, b) -> {
            int score = (a.startsWith("paper") ? 0 : a.equals("velocity") ? 1 : 2)
                    - (b.startsWith("paper") ? 0 : b.equals("velocity") ? 1 : 2);
            return score;
        });

        for (String name : order) {
            Long pid = pids.get(name);
            if (pid == null) continue;
            try {
                boolean killed = killPid(pid);
                System.out.println((killed ? "  ✓ " : "  ⚠ ") + name + " (PID " + pid + ")");
            } catch (Exception e) {
                System.out.println("  ✗ " + name + ": " + e.getMessage());
            }
        }

        Files.deleteIfExists(PIDS_PATH);
        System.out.println("Zakończono.");
    }

    private static boolean killPid(long pid) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("win")) {
            pb = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid));
        } else {
            pb = new ProcessBuilder("kill", "-15", String.valueOf(pid)); // SIGTERM najpierw
        }
        Process p = pb.start();
        boolean finished = p.waitFor(5, TimeUnit.SECONDS);
        if (!finished || p.exitValue() != 0) {
            // Force
            if (os.contains("win")) {
                new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid)).start().waitFor(3, TimeUnit.SECONDS);
            } else {
                new ProcessBuilder("kill", "-9", String.valueOf(pid)).start().waitFor(3, TimeUnit.SECONDS);
            }
        }
        return true;
    }

    private static void fallbackKill() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("taskkill", "/F", "/IM", "java.exe").start().waitFor(5, TimeUnit.SECONDS);
            } else {
                new ProcessBuilder("pkill", "-f", "paper").start().waitFor(3, TimeUnit.SECONDS);
                new ProcessBuilder("pkill", "-f", "velocity").start().waitFor(3, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}
    }

    // ==================== STATUS ====================

    private static void status(LauncherConfig config) throws Exception {
        Map<String, Long> pids = loadPids();
        System.out.println("\nStatus procesów:");
        System.out.println("─────────────────────────────────────");

        if (pids.isEmpty()) {
            System.out.println("  Brak zapisanych procesów.");
            return;
        }

        for (Map.Entry<String, Long> e : pids.entrySet()) {
            boolean alive = isAlive(e.getValue());
            String status = alive ? "§aONLINE" : "§cOFFLINE";
            // Bez kolorów w konsoli systemowej
            status = alive ? "ONLINE " : "OFFLINE";
            System.out.printf("  %-16s PID %-8d %s%n", e.getKey(), e.getValue(), status);
        }
        System.out.println("─────────────────────────────────────");
    }

    private static boolean isAlive(long pid) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid);
            } else {
                pb = new ProcessBuilder("kill", "-0", String.valueOf(pid));
            }
            Process p = pb.start();
            return p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== CONFIG & PIDS ====================

    private static LauncherConfig loadOrCreateConfig() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, LauncherConfig.class);
            }
        }

        LauncherConfig config = new LauncherConfig();
        config.startRedis = true;
        config.startNats = true;
        config.redisCommand = List.of("redis-server");
        config.natsCommand = List.of("nats-server");
        config.velocityJar = "velocity.jar";
        config.velocityDir = "velocity";
        config.velocityJvmArgs = List.of("-Xms512M", "-Xmx1G");
        config.paperJar = "paper.jar";
        config.defaultPaperJvmArgs = List.of("-Xms1G", "-Xmx2G");
        config.delayAfterVelocityMs = 4000;
        config.delayBetweenSectorsMs = 2500;

        config.sectors = new LinkedHashMap<>();
        config.sectors.put("guild", new SectorEntry("servers/guild", null));
        config.sectors.put("spawn", new SectorEntry("servers/spawn", null));
        config.sectors.put("afk", new SectorEntry("servers/afk", List.of("-Xms512M", "-Xmx1G")));

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(config, writer);
        }
        System.out.println("Utworzono domyślny launcher-config.json – edytuj przed użyciem!");
        return config;
    }

    private static Map<String, Long> loadPids() {
        if (!Files.exists(PIDS_PATH)) return new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(PIDS_PATH)) {
            Map<?, ?> raw = GSON.fromJson(reader, Map.class);
            Map<String, Long> result = new LinkedHashMap<>();
            if (raw != null) {
                raw.forEach((k, v) -> result.put(String.valueOf(k), ((Number) v).longValue()));
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void savePids(Map<String, Long> pids) throws IOException {
        try (Writer writer = Files.newBufferedWriter(PIDS_PATH)) {
            GSON.toJson(pids, writer);
        }
    }

    // ==================== MODELS ====================

    public static class LauncherConfig {
        public boolean startRedis = true;
        public boolean startNats = true;
        public List<String> redisCommand;
        public List<String> natsCommand;

        public String velocityJar;
        public String velocityDir;
        public List<String> velocityJvmArgs;

        public String paperJar;
        public List<String> defaultPaperJvmArgs;

        public long delayAfterVelocityMs = 4000;
        public long delayBetweenSectorsMs = 2500;

        public Map<String, SectorEntry> sectors;
    }

    public static class SectorEntry {
        public String directory;
        public List<String> jvmArgs; // null = użyj defaultPaperJvmArgs

        public SectorEntry() {}
        public SectorEntry(String directory, List<String> jvmArgs) {
            this.directory = directory;
            this.jvmArgs = jvmArgs;
        }
    }
}

package pl.dzoku.sectorsystem;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class Launcher {
    private static final Map<String, Process> processes = new ConcurrentHashMap<>();
    private static final Path LOGS_DIR = Path.of("logs");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Files.createDirectories(LOGS_DIR);

        switch (args[0].toLowerCase()) {
            case "start" -> startAll();
            case "stop" -> stopAll();
            case "restart" -> { stopAll(); Thread.sleep(2000); startAll(); }
            case "status" -> printStatus();
            default -> printUsage();
        }
    }

    private static void startAll() throws Exception {
        System.out.println("Starting SectorSystem stack...");

        startProcess("velocity", "java", "-Djava.awt.headless=true", "-Xmx512M", "-jar", "velocity/velocity.jar");
        Thread.sleep(3000);

        startProcess("guild", "java", "-Djava.awt.headless=true", "-Xmx1G", "-jar", "paper-guild/paper.jar");
        startProcess("spawn", "java", "-Djava.awt.headless=true", "-Xmx1G", "-jar", "paper-spawn/paper.jar");
        startProcess("afk", "java", "-Djava.awt.headless=true", "-Xmx1G", "-jar", "paper-afk/paper.jar");

        System.out.println("All processes started. Use 'status' to check.");
    }

    private static void startProcess(String name, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(LOGS_DIR.resolve(name + ".log").toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        processes.put(name, process);

        Files.writeString(LOGS_DIR.resolve(name + ".pid"), String.valueOf(process.pid()));
        System.out.println("  [OK] " + name + " started (PID: " + process.pid() + ")");
    }

    private static void stopAll() {
        System.out.println("Stopping all processes...");
        processes.forEach((name, process) -> {
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                System.out.println("  [OK] " + name + " stopped");
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        });
        processes.clear();
    }

    private static void printStatus() {
        System.out.println("=== SectorSystem Status ===");
        processes.forEach((name, process) -> {
            String status = process.isAlive() ? "RUNNING" : "STOPPED";
            System.out.println("  " + name + ": " + status + " (PID: " + process.pid() + ")");
        });
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar launcher.jar <start|stop|restart|status>");
    }
}

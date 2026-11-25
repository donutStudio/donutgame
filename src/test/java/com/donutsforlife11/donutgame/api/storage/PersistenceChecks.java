package com.donutsforlife11.donutgame.api.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.donutsforlife11.donutgame.internal.file.ModuleFiles;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.storage.StorageDatabase;

public final class PersistenceChecks {
    public record Stats(int level, Map<String, Integer> skills, List<String> achievements, UUID owner) {}

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("donutgame-persistence-");
        try {
            storage(temporary);
            files(temporary);
            System.out.println("Passed storage persistence and module file checks.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void storage(Path temporary) throws Exception {
        Path file = temporary.resolve("game/storage.db");
        GameModule game = new GameModule() {};
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Stats stats = new Stats(7, Map.of("mining", 4), List.of("survivor"), first);
        try (StorageDatabase database = new StorageDatabase(file)) {
            GameStorage storage = new GameStorage(game, database);
            check(storage.get("missing", Integer.class, 12) == 12, "Missing values use the supplied fallback");
            check(!storage.contains("missing") && !storage.remove("missing"), "Missing reads and deletes are harmless");
            storage.player(first).clear();
            check(!Files.exists(file), "Reads and empty scopes must not create a database");
            storage.set("difficulty", "hard");
            storage.player(first).set("stats", stats);
            storage.player(second).set("stats", 42);
            check(storage.get("stats", Stats.class) == null, "Game and player scopes are isolated");
            check(storage.player(second).get("stats", Integer.class) == 42, "Player scopes are isolated");
            check(storage.player(first).get("stats", Stats.class).equals(stats), "Nested records and UUID values round trip");
            for (int index = 0; index < 100; index++) storage.player(first).set("stats", stats);
            try (var db = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var query = db.createStatement(); var rows = query.executeQuery("SELECT count(*) FROM storage")) {
                check(rows.next() && rows.getInt(1) == 3, "Repeated saves overwrite one row instead of accumulating keys");
            }
            // A second active game instance shares this game-id database and sees committed changes.
            try (StorageDatabase concurrent = new StorageDatabase(file)) {
                check(new GameStorage(game, concurrent).player(first).get("stats", Stats.class).equals(stats), "Simultaneous instances share committed values");
                new GameStorage(game, concurrent).set("difficulty", "easy");
                check(storage.get("difficulty", String.class).equals("easy"), "No stale per-instance read cache");
            }
            var mutable = new ArrayList<>(List.of("before"));
            storage.set("snapshot", mutable);
            mutable.add("after");
            check(storage.get("snapshot", String[].class).length == 1, "Saving takes a detached value snapshot");
            storage.set("snapshot", null);
            check(!storage.contains("snapshot"), "Null physically removes values");
            storage.clear();
            check(storage.player(first).contains("stats"), "Clearing game scope preserves player values");
        }
        try (StorageDatabase database = new StorageDatabase(file)) {
            GameStorage storage = new GameStorage(game, database);
            check(storage.player(first).get("stats", Stats.class).equals(stats), "Records survive database close and reopen");
            storage.player(first).clear();
            check(storage.player(second).contains("stats"), "Clearing one player preserves other players");
            check(storage.player(second).remove("stats"), "Deletion reports whether the key existed");
            try (var db = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var query = db.createStatement(); var rows = query.executeQuery("SELECT count(*) FROM storage")) {
                check(rows.next() && rows.getInt(1) == 0, "Deletes leave no tombstones or empty scope records");
            }
        }
    }

    private static void files(Path temporary) throws Exception {
        Path jar = temporary.resolve("example.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            entry(output, "config.yml", "id: example\ncountdown_ticks: 200\n");
            entry(output, "events.yml", "item_drops: []\n");
            entry(output, "data/example/function/start.mcfunction", "say hello");
            entry(output, "example/Main.class", "compiled");
            entry(output, "META-INF/MANIFEST.MF", "manifest");
        }
        Path directory = ModuleFiles.directory(temporary, "example");
        ModuleFiles.seed(jar, directory);
        check(Files.exists(directory.resolve("data/example/function/start.mcfunction")), "Module datapack resources are installed externally");
        check(!Files.exists(directory.resolve("example/Main.class")) && !Files.exists(directory.resolve("META-INF")), "Code and metadata stay in the jar");
        Files.writeString(directory.resolve("config.yml"), "id: example\ncountdown_ticks: 50\n");
        ModuleFiles.seed(jar, directory);
        GameModuleDescriptor descriptor = new GameModuleDescriptor("example", "", "Example", GameModule.class, "", jar.toFile(), directory.toFile());
        check(descriptor.createConfig().getInt("countdown_ticks") == 50, "Reload uses server config edits and seeding never overwrites them");
        Files.writeString(directory.resolve("config.yml"), "id: example\ncountdown_ticks: 30\n");
        check(descriptor.createConfig().getInt("countdown_ticks") == 30, "Each game starts from a fresh external config snapshot");
        rejects(() -> ModuleFiles.directory(temporary, ".."));
        rejects(() -> ModuleFiles.resolve(directory, "../outside"));
        rejects(() -> ModuleFiles.resolve(directory, "C:/outside"));
        rejects(() -> ModuleFiles.resolve(directory, "/outside"));
        Path snapshot = ModuleFiles.snapshot(jar, temporary.resolve("module-jars"));
        byte[] originalSnapshot = Files.readAllBytes(snapshot);
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{snapshot.toUri().toURL()}, null)) {
            check(loader.getResource("config.yml") != null, "Snapshot loader opens its own jar index");
            Files.writeString(jar, "An upload overwrote the deployment jar while the server was running");
            try (var resource = loader.getResourceAsStream("config.yml")) {
                check(resource != null && resource.readAllBytes().length > 0, "Lazy reads still use the immutable snapshot after deployment overwrite");
            }
            check(java.util.Arrays.equals(originalSnapshot, Files.readAllBytes(snapshot)), "Deployment changes cannot mutate the loaded module");
        }
        try { ModuleFiles.snapshot(jar, temporary.resolve("module-jars")); throw new AssertionError("Invalid jar accepted"); }
        catch (java.io.IOException expected) { }
        Path unsafe = temporary.resolve("unsafe.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(unsafe))) { entry(output, "../escaped.yml", "bad"); }
        rejects(() -> ModuleFiles.seed(unsafe, directory));
        check(!Files.exists(temporary.resolve("escaped.yml")), "Unsafe archive paths cannot escape the game directory");
    }

    private static void entry(JarOutputStream output, String name, String contents) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected invalid module path rejection"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

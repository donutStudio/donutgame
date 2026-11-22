package com.donutsforlife11.donutgame.api.data;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.advancement.Advancement;
import org.bukkit.inventory.Recipe;
import org.bukkit.loot.LootTable;
import org.bukkit.configuration.file.YamlConfiguration;
import com.donutsforlife11.donutgame.internal.file.ModuleFiles;

import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItems;
import com.donutsforlife11.donutgame.api.item.GameLootTable;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;

/**
 * Shared implementation behind the build-time generated {@code GameData} API.
 */
public abstract class GameDataBase {
    private final GameModule module;
    private final Path moduleDirectory;

    protected GameDataBase(GameModule module, File moduleDirectory) {
        this.module = Objects.requireNonNull(module, "module");
        this.moduleDirectory = Objects.requireNonNull(moduleDirectory, "moduleDirectory").toPath().toAbsolutePath().normalize();
    }

    public final String namespace() {
        return module.id();
    }

    public final NamespacedKey key(String key) {
        return ownedKey(key);
    }

    /**
     * Read any file under {@code data/<module id>/} in this module directory.
     * The returned resource is read-only.
     */
    public final GameDataResource resource(String relativePath) {
        return new GameDataResource(moduleDirectory, namespace(), relativePath);
    }

    /** Reads an editable YAML file from the game's directory, without jar fallbacks. */
    public final YamlConfiguration config(String relativePath) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(ModuleFiles.resolve(moduleDirectory, relativePath).toFile());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load module config " + relativePath + ".", exception);
        }
        return config;
    }

    public final GameLootTable lootTable(NamespacedKey key) {
        NamespacedKey owned = ownedKey(key);
        requireResource(dataFile("loot_table", owned, ".json"), "loot table", owned);
        LootTable value = Bukkit.getLootTable(owned);
        if (value == null) {
            throw missingLoaded("loot table", owned);
        }
        return new GameLootTable(module, value);
    }

    public final GameLootTable lootTable(String key) {
        return lootTable(ownedKey(key));
    }

    public final GameLootTable lootPool(Collection<?> items) {
        return GameItems.lootPool(module, items);
    }

    public final GameItem item(String shorthand) {
        return GameItems.item(shorthand);
    }

    public final Advancement advancement(NamespacedKey key) {
        NamespacedKey owned = ownedKey(key);
        requireResource(dataFile("advancement", owned, ".json"), "advancement", owned);
        Advancement value = Bukkit.getAdvancement(owned);
        if (value == null) {
            throw missingLoaded("advancement", owned);
        }
        return value;
    }

    public final Advancement advancement(String key) {
        return advancement(ownedKey(key));
    }

    public final Recipe recipe(NamespacedKey key) {
        NamespacedKey owned = ownedKey(key);
        requireResource(dataFile("recipe", owned, ".json"), "recipe", owned);
        Recipe value = Bukkit.getRecipe(owned);
        if (value == null) {
            throw missingLoaded("recipe", owned);
        }
        return value;
    }

    public final Recipe recipe(String key) {
        return recipe(ownedKey(key));
    }

    public final GameFunction function(NamespacedKey key) {
        NamespacedKey owned = ownedKey(key);
        GameDataResource source = dataFile("function", owned, ".mcfunction");
        requireResource(source, "function", owned);
        return new GameFunction(owned, source);
    }

    public final GameFunction function(String key) {
        return function(ownedKey(key));
    }

    protected final NamespacedKey ownedKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Data key cannot be blank.");
        }
        String normalized = key.contains(":") ? key : namespace() + ":" + key;
        NamespacedKey parsed = NamespacedKey.fromString(normalized);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid data key: " + key);
        }
        return ownedKey(parsed);
    }

    protected final NamespacedKey ownedKey(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        if (!namespace().equals(key.getNamespace())) {
            throw new IllegalArgumentException(
                "Module '" + namespace() + "' may only access its own data namespace; got '" + key + "'."
            );
        }
        return key;
    }

    protected final <T extends Keyed> T registry(RegistryKey<T> registryKey, NamespacedKey key) {
        NamespacedKey owned = ownedKey(key);
        String registryPath = registryKey.key().value();
        requireResource(dataFile(registryPath, owned, ".json"), registryPath, owned);
        T value = RegistryAccess.registryAccess().getRegistry(registryKey).get(owned);
        if (value == null) {
            throw missingLoaded(registryPath, owned);
        }
        return value;
    }

    protected final <T extends Keyed> Tag<T> tag(RegistryKey<T> registryKey, NamespacedKey key) {
        NamespacedKey owned = ownedKey(key);
        String registryPath = registryKey.key().value();
        requireResource(dataFile("tags/" + registryPath, owned, ".json"), registryPath + " tag", owned);
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        try {
            return registry.getTag(registryKey.tagKey(owned.toString()));
        } catch (NoSuchElementException | UnsupportedOperationException exception) {
            throw new IllegalArgumentException(
                "Module data tag '" + owned + "' is present in the module directory but is not loaded in registry '" + registryPath + "'.",
                exception
            );
        }
    }

    private GameDataResource dataFile(String directory, NamespacedKey key, String extension) {
        return resource(directory + "/" + key.getKey() + extension);
    }

    private void requireResource(GameDataResource resource, String type, NamespacedKey key) {
        if (!resource.exists()) {
            throw new IllegalArgumentException(
                "Module '" + namespace() + "' does not own " + type + " '" + key + "' (expected " + resource.path() + ")."
            );
        }
    }

    private IllegalArgumentException missingLoaded(String type, NamespacedKey key) {
        return new IllegalArgumentException(
            "Module " + type + " '" + key + "' exists in the module directory but is not loaded by the server. "
                + "Reload Donutgame/server data after changing module datapack files."
        );
    }
}

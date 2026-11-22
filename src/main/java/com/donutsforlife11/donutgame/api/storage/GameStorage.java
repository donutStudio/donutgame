package com.donutsforlife11.donutgame.api.storage;

import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.ReflectionAccessFilter;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameScope;
import com.donutsforlife11.donutgame.internal.storage.StorageDatabase;

/** Persistent values belonging to a game id, optionally scoped to a player. */
public final class GameStorage {
    private final GameModule module;
    private final StorageDatabase database;
    private final String scope;
    private final Gson json;

    public GameStorage(GameModule module, StorageDatabase database) {
        this(module, database, "", new GsonBuilder()
            .addReflectionAccessFilter(type -> {
                String name = type.getName();
                boolean liveType = name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                    || name.startsWith("com.donutsforlife11.donutgame.");
                return liveType && !type.isRecord() && !type.isEnum()
                    ? ReflectionAccessFilter.FilterResult.BLOCK_ALL : ReflectionAccessFilter.FilterResult.INDECISIVE;
            })
            .registerTypeAdapter(GameItem.class, (JsonSerializer<GameItem>) (item, type, context) ->
                new JsonPrimitive(Base64.getEncoder().encodeToString(item.copyBukkitItem().serializeAsBytes())))
            .registerTypeAdapter(GameItem.class, (JsonDeserializer<GameItem>) (value, type, context) ->
                new GameItem(ItemStack.deserializeBytes(Base64.getDecoder().decode(value.getAsString()))))
            .create());
    }

    private GameStorage(GameModule module, StorageDatabase database, String scope, Gson json) {
        this.module = Objects.requireNonNull(module, "module");
        this.database = Objects.requireNonNull(database, "database");
        this.scope = scope;
        this.json = json;
    }

    public GameStorage player(GamePlayer player) {
        Objects.requireNonNull(player, "player");
        GameScope.requirePlayer(module, player);
        return player(player.uuid());
    }

    public GameStorage player(UUID uuid) {
        GameScope.requireActive(module);
        return new GameStorage(module, database, Objects.requireNonNull(uuid, "uuid").toString(), json);
    }

    public <T> T get(String key, Class<T> type) {
        return get(key, type, null);
    }

    public <T> T get(String key, Class<T> type, T fallback) {
        requireKey(key);
        Objects.requireNonNull(type, "type");
        String value = database.get(scope, key);
        return value == null ? fallback : json.fromJson(value, type);
    }

    /** Commits immediately. Null removes the key; unchanged values do not rewrite the row. */
    public void set(String key, Object value) {
        requireKey(key);
        database.set(scope, key, value == null ? null : json.toJson(value));
    }

    public boolean contains(String key) {
        requireKey(key);
        return database.get(scope, key) != null;
    }

    public boolean remove(String key) {
        requireKey(key);
        return database.remove(scope, key);
    }

    /** Removes values only in this scope (the game, or this player). */
    public void clear() {
        GameScope.requireActive(module);
        database.clear(scope);
    }

    private void requireKey(String key) {
        GameScope.requireActive(module);
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Storage key cannot be blank.");
    }
}

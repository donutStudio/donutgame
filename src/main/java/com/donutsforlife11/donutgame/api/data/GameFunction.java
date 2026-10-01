package com.donutsforlife11.donutgame.api.data;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;

/**
 * A command function owned by a game module.
 */
public final class GameFunction {
    private final NamespacedKey key;
    private final GameDataResource source;

    GameFunction(NamespacedKey key, GameDataResource source) {
        this.key = Objects.requireNonNull(key, "key");
        this.source = Objects.requireNonNull(source, "source");
    }

    public NamespacedKey key() {
        return key;
    }

    public GameDataResource source() {
        return source;
    }

    public boolean run() {
        return runAs(Bukkit.getConsoleSender());
    }

    public boolean runAs(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (!source.exists()) {
            throw new IllegalArgumentException("Unknown module function: " + key);
        }
        return Bukkit.dispatchCommand(sender, "minecraft:function " + key);
    }
}

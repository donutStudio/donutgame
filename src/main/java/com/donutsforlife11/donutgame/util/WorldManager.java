package com.donutsforlife11.donutgame.util;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;

import net.kyori.adventure.text.Component;

public class WorldManager {
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private SlimeLoader loader;
    private final Donutgame plugin;
    private int worldIndex = 0;

    public WorldManager(Donutgame plugin, File worldDirectory) {
        this.plugin = plugin;
        this.loader = new FileLoader(worldDirectory);
    }

    public CompletableFuture<SlimeWorldInstance> loadSlimeWorld(String templateWorldName, String instanceWorldName) {
        CompletableFuture<SlimeWorldInstance> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SlimePropertyMap properties = new SlimePropertyMap();

                SlimeWorld template = asp.readWorld(loader, templateWorldName, true, properties);
                SlimeWorld instanceWorld = template.clone(instanceWorldName);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        SlimeWorldInstance instance = asp.loadWorld(instanceWorld, true);
                        future.complete(instance);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        return future;
    }

    public void sendPlayerToWorld(Player player, World world) {
        
        if (world == null) {
            player.sendMessage(Component.text("World not found."));
            return;
        }

        player.teleportAsync(world.getSpawnLocation());
    }

    public int incrementWorldIndex() {
        return worldIndex++;
    }

    public int decrementWorldIndex() {
        return worldIndex--;
    }
}

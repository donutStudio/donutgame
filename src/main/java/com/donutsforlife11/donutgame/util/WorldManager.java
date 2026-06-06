package com.donutsforlife11.donutgame.util;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
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
    private final SlimeLoader loader;
    private final Donutgame plugin;
    private final Map<String, Integer> nextTemplateSlots = new HashMap<>();
    private final Map<String, Queue<Integer>> freeTemplateSlots = new HashMap<>();
    private final Map<String, WorldSession> worldsByName = new HashMap<>();

    public WorldManager(Donutgame plugin, File worldDirectory) {
        this.plugin = plugin;
        this.loader = new FileLoader(worldDirectory);
    }

    public CompletableFuture<WorldSession> loadSlimeWorld(String templateWorldName) {
        int slot = allocateSlot(templateWorldName);
        String instanceWorldName = templateWorldName + "_" + slot;
        CompletableFuture<WorldSession> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SlimePropertyMap properties = new SlimePropertyMap();

                SlimeWorld template = asp.readWorld(loader, templateWorldName, true, properties);
                SlimeWorld instanceWorld = template.clone(instanceWorldName);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        SlimeWorldInstance instance = asp.loadWorld(instanceWorld, true);
                        WorldSession session = new WorldSession(
                            templateWorldName,
                            instanceWorldName,
                            slot,
                            UUID.randomUUID().toString(),
                            instance
                        );

                        worldsByName.put(instanceWorldName, session);
                        future.complete(session);
                    } catch (Throwable t) {
                        releaseSlot(templateWorldName, slot);
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                releaseSlot(templateWorldName, slot);
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

    public String getPlayerStateId(World world) {
        WorldSession session = worldsByName.get(world.getName());
        return session == null ? world.getName() : session.playerStateId();
    }

    public WorldSession releaseWorld(String instanceWorldName) {
        WorldSession session = worldsByName.remove(instanceWorldName);

        if (session != null) {
            releaseSlot(session.templateWorldName(), session.slot());
        }

        return session;
    }

    private int allocateSlot(String templateWorldName) {
        Queue<Integer> freeSlots = freeTemplateSlots.computeIfAbsent(templateWorldName, ignored -> new ArrayDeque<>());

        if (!freeSlots.isEmpty()) {
            return freeSlots.poll();
        }

        int nextSlot = nextTemplateSlots.getOrDefault(templateWorldName, 0);
        nextTemplateSlots.put(templateWorldName, nextSlot + 1);
        return nextSlot;
    }

    private void releaseSlot(String templateWorldName, int slot) {
        freeTemplateSlots.computeIfAbsent(templateWorldName, ignored -> new ArrayDeque<>()).offer(slot);
    }

    public record WorldSession(
        String templateWorldName,
        String instanceWorldName,
        int slot,
        String playerStateId,
        SlimeWorldInstance worldInstance
    ) {
    }
}

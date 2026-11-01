package com.donutsforlife11.doppelganger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.attribute.Attribute;

final class NPCSettings {
    private static final double DEFAULT_PLAYER_MOVEMENT_ATTRIBUTE = 0.1;
    private static final double VANILLA_WALK_BLOCKS_PER_SECOND = 4.317;

    final int appearancesPerPlayer;
    final int scanRadius;
    final int scanYBelow;
    final int scanYAbove;
    final int maxPathDistance;
    final int maxExpandedNodes;
    final double walkSpeedMultiplier;
    final int idleMinTicks;
    final int idleMaxTicks;
    final double wanderChance;
    final int wanderRadius;
    final boolean debugDestinations;
    final boolean debugPathfinding;
    final boolean debugMovement;
    final int debugSampleTicks;
    final Set<Material> targetBlocks;
    final Set<Material> passageBlocks;

    private NPCSettings(
        int appearancesPerPlayer,
        int scanRadius,
        int scanYBelow,
        int scanYAbove,
        int maxPathDistance,
        int maxExpandedNodes,
        double walkSpeedMultiplier,
        int idleMinTicks,
        int idleMaxTicks,
        double wanderChance,
        int wanderRadius,
        boolean debugDestinations,
        boolean debugPathfinding,
        boolean debugMovement,
        int debugSampleTicks,
        Set<Material> targetBlocks,
        Set<Material> passageBlocks
    ) {
        this.appearancesPerPlayer = appearancesPerPlayer;
        this.scanRadius = scanRadius;
        this.scanYBelow = scanYBelow;
        this.scanYAbove = scanYAbove;
        this.maxPathDistance = maxPathDistance;
        this.maxExpandedNodes = maxExpandedNodes;
        this.walkSpeedMultiplier = walkSpeedMultiplier;
        this.idleMinTicks = idleMinTicks;
        this.idleMaxTicks = Math.max(idleMinTicks, idleMaxTicks);
        this.wanderChance = wanderChance;
        this.wanderRadius = wanderRadius;
        this.debugDestinations = debugDestinations;
        this.debugPathfinding = debugPathfinding;
        this.debugMovement = debugMovement;
        this.debugSampleTicks = Math.max(20, debugSampleTicks);
        this.targetBlocks = targetBlocks;
        this.passageBlocks = passageBlocks;
    }

    static NPCSettings from(Doppelganger game) {
        ConfigurationSection npc = game.config().getConfigurationSection("npc");
        int appearancesPerPlayer = getInt(npc, "appearances-per-player", getInt(npc, "population-per-player", 3));
        int scanRadius = getInt(npc, "scan-radius", 64);
        int scanYBelow = getInt(npc, "scan-y-below", 8);
        int scanYAbove = getInt(npc, "scan-y-above", 12);
        int maxPathDistance = getInt(npc, "max-path-distance", 48);
        int maxExpandedNodes = getInt(npc, "max-expanded-nodes", 2500);
        double walkSpeedMultiplier = getDouble(npc, "walk-speed-multiplier", 1.0);
        int idleMinTicks = seconds(getInt(npc, "idle-seconds.min", 3));
        int idleMaxTicks = seconds(getInt(npc, "idle-seconds.max", 9));
        double wanderChance = getDouble(npc, "wander-chance", 0.20);
        int wanderRadius = getInt(npc, "wander-radius", 18);
        ConfigurationSection debug = npc == null ? null : npc.getConfigurationSection("debug");
        return new NPCSettings(
            appearancesPerPlayer,
            scanRadius,
            scanYBelow,
            scanYAbove,
            maxPathDistance,
            maxExpandedNodes,
            walkSpeedMultiplier,
            idleMinTicks,
            idleMaxTicks,
            wanderChance,
            wanderRadius,
            getBoolean(debug, "destinations", false),
            getBoolean(debug, "pathfinding", false),
            getBoolean(debug, "movement", false),
            getInt(debug, "sample-ticks", 100),
            materials(npc, "target-blocks", List.of(
                "minecraft:crafting_table",
                "minecraft:furnace",
                "minecraft:blast_furnace",
                "minecraft:smoker",
                "minecraft:barrel",
                "minecraft:lectern",
                "minecraft:composter"
            ), Material.CRAFTING_TABLE),
            materials(npc, "passage-blocks", List.of(
                "minecraft:oak_door",
                "minecraft:spruce_door",
                "minecraft:birch_door",
                "minecraft:jungle_door",
                "minecraft:acacia_door",
                "minecraft:dark_oak_door",
                "minecraft:mangrove_door",
                "minecraft:cherry_door",
                "minecraft:bamboo_door",
                "minecraft:crimson_door",
                "minecraft:warped_door",
                "minecraft:oak_fence_gate",
                "minecraft:spruce_fence_gate",
                "minecraft:birch_fence_gate",
                "minecraft:jungle_fence_gate",
                "minecraft:acacia_fence_gate",
                "minecraft:dark_oak_fence_gate",
                "minecraft:mangrove_fence_gate",
                "minecraft:cherry_fence_gate",
                "minecraft:bamboo_fence_gate",
                "minecraft:crimson_fence_gate",
                "minecraft:warped_fence_gate",
                "minecraft:oak_trapdoor",
                "minecraft:spruce_trapdoor",
                "minecraft:birch_trapdoor",
                "minecraft:jungle_trapdoor",
                "minecraft:acacia_trapdoor",
                "minecraft:dark_oak_trapdoor",
                "minecraft:mangrove_trapdoor",
                "minecraft:cherry_trapdoor",
                "minecraft:bamboo_trapdoor",
                "minecraft:crimson_trapdoor",
                "minecraft:warped_trapdoor"
            ), Material.OAK_DOOR)
        );
    }

    double playerWalkSpeed(Doppelganger game) {
        double averageAttribute = game.playerManager().getOnlinePlayers().stream()
            .map(GamePlayer -> GamePlayer.attributeValue(Attribute.MOVEMENT_SPEED))
            .filter(value -> value > 0.0)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(DEFAULT_PLAYER_MOVEMENT_ATTRIBUTE);
        return (VANILLA_WALK_BLOCKS_PER_SECOND / 20.0)
            * (averageAttribute / DEFAULT_PLAYER_MOVEMENT_ATTRIBUTE)
            * walkSpeedMultiplier;
    }

    private static Set<Material> materials(ConfigurationSection npc, String path, List<String> fallback, Material emptyFallback) {
        List<String> names = npc == null ? List.of() : npc.getStringList(path);
        if (names.isEmpty()) {
            names = fallback;
        }
        Set<Material> materials = new LinkedHashSet<>();
        for (String name : names) {
            Material material = material(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials.isEmpty() ? Set.of(emptyFallback) : Set.copyOf(materials);
    }

    private static Material material(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        Material material = Registry.MATERIAL.get(NamespacedKey.fromString(normalized));
        return material == null ? Material.matchMaterial(name) : material;
    }

    private static int getInt(ConfigurationSection section, String path, int fallback) {
        return section == null ? fallback : section.getInt(path, fallback);
    }

    private static double getDouble(ConfigurationSection section, String path, double fallback) {
        return section == null ? fallback : section.getDouble(path, fallback);
    }

    private static boolean getBoolean(ConfigurationSection section, String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }

    private static int seconds(int seconds) {
        return Math.max(1, seconds) * 20;
    }
}

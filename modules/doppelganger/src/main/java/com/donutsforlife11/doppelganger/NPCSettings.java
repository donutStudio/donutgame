package com.donutsforlife11.doppelganger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

final class NPCSettings {
    final int population;
    final int scanRadius;
    final int scanYBelow;
    final int scanYAbove;
    final int maxPathDistance;
    final int maxExpandedNodes;
    final double walkSpeed;
    final int idleMinTicks;
    final int idleMaxTicks;
    final double wanderChance;
    final int wanderRadius;
    final Set<Material> targetBlocks;

    private NPCSettings(
        int population,
        int scanRadius,
        int scanYBelow,
        int scanYAbove,
        int maxPathDistance,
        int maxExpandedNodes,
        double walkSpeed,
        int idleMinTicks,
        int idleMaxTicks,
        double wanderChance,
        int wanderRadius,
        Set<Material> targetBlocks
    ) {
        this.population = population;
        this.scanRadius = scanRadius;
        this.scanYBelow = scanYBelow;
        this.scanYAbove = scanYAbove;
        this.maxPathDistance = maxPathDistance;
        this.maxExpandedNodes = maxExpandedNodes;
        this.walkSpeed = walkSpeed;
        this.idleMinTicks = idleMinTicks;
        this.idleMaxTicks = Math.max(idleMinTicks, idleMaxTicks);
        this.wanderChance = wanderChance;
        this.wanderRadius = wanderRadius;
        this.targetBlocks = targetBlocks;
    }

    static NPCSettings from(Doppelganger game) {
        ConfigurationSection npc = game.config().getConfigurationSection("npc");
        int population = getInt(npc, "population", 12);
        int scanRadius = getInt(npc, "scan-radius", 64);
        int scanYBelow = getInt(npc, "scan-y-below", 8);
        int scanYAbove = getInt(npc, "scan-y-above", 12);
        int maxPathDistance = getInt(npc, "max-path-distance", 48);
        int maxExpandedNodes = getInt(npc, "max-expanded-nodes", 2500);
        double walkSpeed = getDouble(npc, "walk-speed", 0.16);
        int idleMinTicks = seconds(getInt(npc, "idle-seconds.min", 3));
        int idleMaxTicks = seconds(getInt(npc, "idle-seconds.max", 9));
        double wanderChance = getDouble(npc, "wander-chance", 0.20);
        int wanderRadius = getInt(npc, "wander-radius", 18);
        return new NPCSettings(
            population,
            scanRadius,
            scanYBelow,
            scanYAbove,
            maxPathDistance,
            maxExpandedNodes,
            walkSpeed,
            idleMinTicks,
            idleMaxTicks,
            wanderChance,
            wanderRadius,
            targetBlocks(npc)
        );
    }

    private static Set<Material> targetBlocks(ConfigurationSection npc) {
        List<String> names = npc == null ? List.of() : npc.getStringList("target-blocks");
        if (names.isEmpty()) {
            names = List.of("CRAFTING_TABLE", "FURNACE", "BLAST_FURNACE", "SMOKER", "BARREL", "LECTERN", "COMPOSTER");
        }
        Set<Material> materials = new LinkedHashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials.isEmpty() ? Set.of(Material.CRAFTING_TABLE) : Set.copyOf(materials);
    }

    private static int getInt(ConfigurationSection section, String path, int fallback) {
        return section == null ? fallback : section.getInt(path, fallback);
    }

    private static double getDouble(ConfigurationSection section, String path, double fallback) {
        return section == null ? fallback : section.getDouble(path, fallback);
    }

    private static int seconds(int seconds) {
        return Math.max(1, seconds) * 20;
    }
}

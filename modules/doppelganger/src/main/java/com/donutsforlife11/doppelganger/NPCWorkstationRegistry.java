package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;

import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocation;

final class NPCWorkstationRegistry {
    private static final int[][] CARDINALS = {
        { 1, 0 },
        { -1, 0 },
        { 0, 1 },
        { 0, -1 }
    };

    private final Doppelganger game;
    private final NPCSettings settings;
    private final List<NPCDestination> destinations = new ArrayList<>();

    NPCWorkstationRegistry(Doppelganger game, NPCSettings settings) {
        this.game = game;
        this.settings = settings;
    }

    void discover() {
        destinations.clear();
        if (game.spawns().isEmpty()) {
            return;
        }

        Bounds bounds = Bounds.around(game, settings);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                if (!game.world().isChunkLoaded(x, z)) {
                    continue;
                }
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    GameLocation blockLocation = new GameLocation(game.world(), x, y, z);
                    if (!settings.targetBlocks.contains(game.world().blockAt(blockLocation).type())) {
                        continue;
                    }
                    addStandableTargetsNear(blockLocation);
                }
            }
        }
        game.log("Discovered " + destinations.size() + " NPC workstation stand spots.");
    }

    List<NPCDestination> destinations() {
        return List.copyOf(destinations);
    }

    NPCDestination randomDestination() {
        if (destinations.isEmpty()) {
            return null;
        }
        return destinations.get(game.random().nextInt(destinations.size()));
    }

    NPCDestination randomWanderDestination(GameLocation origin) {
        if (origin == null) {
            return randomDestination();
        }
        List<NPCDestination> choices = new ArrayList<>();
        int radius = settings.wanderRadius;
        int minY = Math.max(game.world().minHeight() + 1, origin.blockY() - 3);
        int maxY = Math.min(game.world().maxHeight() - 2, origin.blockY() + 3);
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = origin.blockX() + game.random().nextInt(-radius, radius + 1);
            int z = origin.blockZ() + game.random().nextInt(-radius, radius + 1);
            if (!game.world().isChunkLoaded(x, z)) {
                continue;
            }
            for (int y = minY; y <= maxY; y++) {
                if (NPCPathRules.isStandable(game, x, y, z)) {
                    choices.add(new NPCDestination(new GameLocation(game.world(), x + 0.5, y, z + 0.5), null));
                    break;
                }
            }
        }
        if (choices.isEmpty()) {
            return randomDestination();
        }
        Collections.shuffle(choices, game.random());
        return choices.getFirst();
    }

    private void addStandableTargetsNear(GameLocation workstation) {
        for (int[] direction : CARDINALS) {
            int x = workstation.blockX() + direction[0];
            int z = workstation.blockZ() + direction[1];
            for (int y = workstation.blockY() - 1; y <= workstation.blockY() + 1; y++) {
                if (NPCPathRules.isStandable(game, x, y, z)) {
                    destinations.add(new NPCDestination(
                        new GameLocation(game.world(), x + 0.5, y, z + 0.5),
                        workstation
                    ));
                    break;
                }
            }
        }
    }

    record NPCDestination(GameLocation standAt, GameLocation lookAt) {
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        static Bounds around(Doppelganger game, NPCSettings settings) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (GameLocation spawn : game.spawns()) {
                minX = Math.min(minX, spawn.blockX() - settings.scanRadius);
                maxX = Math.max(maxX, spawn.blockX() + settings.scanRadius);
                minY = Math.min(minY, spawn.blockY() - settings.scanYBelow);
                maxY = Math.max(maxY, spawn.blockY() + settings.scanYAbove);
                minZ = Math.min(minZ, spawn.blockZ() - settings.scanRadius);
                maxZ = Math.max(maxZ, spawn.blockZ() + settings.scanRadius);
            }
            minY = Math.max(game.world().minHeight() + 1, minY);
            maxY = Math.min(game.world().maxHeight() - 2, maxY);
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }

    static final class NPCPathRules {
        private NPCPathRules() {
        }

        static boolean isStandable(Doppelganger game, int x, int y, int z) {
            return y > game.world().minHeight()
                && y < game.world().maxHeight() - 1
                && isGround(game.world().blockAt(new GameLocation(game.world(), x, y - 1, z)))
                && isPassable(game.world().blockAt(new GameLocation(game.world(), x, y, z)))
                && isPassable(game.world().blockAt(new GameLocation(game.world(), x, y + 1, z)));
        }

        static boolean isPassable(GameBlock block) {
            Material type = block.type();
            BlockData data = block.blockData();
            return type.isAir()
                || type == Material.WATER
                || data instanceof Openable;
        }

        private static boolean isGround(GameBlock block) {
            Material type = block.type();
            return type.isSolid() && type != Material.CACTUS && type != Material.MAGMA_BLOCK;
        }
    }
}

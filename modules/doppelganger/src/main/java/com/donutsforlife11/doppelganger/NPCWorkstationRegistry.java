package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;

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
            debug("Skipping workstation discovery because the map has no spawn points.");
            return;
        }

        Bounds bounds = Bounds.around(game, settings);
        int scannedBlocks = 0;
        int targetBlocks = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                if (!game.world().isChunkLoaded(x, z)) {
                    continue;
                }
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    GameLocation blockLocation = new GameLocation(game.world(), x, y, z);
                    scannedBlocks++;
                    if (!settings.targetBlocks.contains(game.world().blockAt(blockLocation).type())) {
                        continue;
                    }
                    targetBlocks++;
                    addStandableTargetsNear(blockLocation);
                }
            }
        }
        game.log("Discovered " + destinations.size() + " NPC workstation stand spots.");
        debug("Scanned " + scannedBlocks + " blocks, found " + targetBlocks + " workstation blocks, accepted "
            + destinations.size() + " stand spots.");
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
            int[] offsets = {0, 1, -1, 2, -2, 3, -3};
            for (int offset : offsets) {
                int y = origin.blockY() + offset;

                if (y < minY || y > maxY) {
                    continue;
                }

                if (NPCPathRules.isStandable(
                        game, settings, x, y, z)) {

                    choices.add(new NPCDestination(
                        new GameLocation(
                            game.world(),
                            x + 0.5,
                            y,
                            z + 0.5
                        ),
                        null
                    ));

                    break;
                }
            }
        }
        if (choices.isEmpty()) {
            debug("No random wander candidates near " + describe(origin) + "; falling back to workstation destinations.");
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
                if (NPCPathRules.isStandable(game, settings, x, y, z)) {
                    destinations.add(new NPCDestination(
                        new GameLocation(game.world(), x + 0.5, y, z + 0.5),
                        workstation
                    ));
                    break;
                }
            }
        }
    }

    private void debug(String message) {
        if (settings.debugDestinations) {
            game.log("[npc:destinations] " + message);
        }
    }

    private String describe(GameLocation location) {
        if (location == null) {
            return "null";
        }
        return location.blockX() + "," + location.blockY() + "," + location.blockZ();
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

        static boolean isStandable(Doppelganger game, NPCSettings settings, int x, int y, int z) {
            return y > game.world().minHeight()
                && y < game.world().maxHeight() - 1
                && isGround(game.world().blockAt(new GameLocation(game.world(), x, y - 1, z)))
                && isPassable(settings, game.world().blockAt(new GameLocation(game.world(), x, y, z)))
                && isPassable(settings, game.world().blockAt(new GameLocation(game.world(), x, y + 1, z)));
        }

        static boolean isPassable(NPCSettings settings, GameBlock block) {
            Material type = block.type();
            org.bukkit.block.data.BlockData data = block.blockData();
            return type.isAir() || (settings.passageBlocks.contains(type) && data instanceof org.bukkit.block.data.Openable);
        }

        static boolean isPassage(Doppelganger game, NPCSettings settings, int x, int y, int z) {
            Material type = game.world().blockAt(new GameLocation(game.world(), x, y, z)).type();
            return settings.passageBlocks.contains(type);
        }

        private static boolean isGround(GameBlock block) {
            Material type = block.type();
            org.bukkit.block.data.BlockData data = block.blockData();
            return ((type.isSolid() && type != Material.CACTUS && type != Material.MAGMA_BLOCK)
                || data instanceof org.bukkit.block.data.type.Snow snow && snow.getLayers() >= 5)
                && type != Material.SWEET_BERRY_BUSH;
        }
    }
}

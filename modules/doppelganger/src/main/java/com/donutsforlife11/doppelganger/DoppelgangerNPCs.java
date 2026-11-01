package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.object.GameEntities;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;

final class DoppelgangerNPCs {
    private final Doppelganger game;
    private final NPCSettings settings;
    private final NPCWorkstationRegistry workstations;
    private final List<GameEntity> entities = new ArrayList<>();
    private final List<NPCBehavior> behaviors = new ArrayList<>();
    private final List<ResolvableProfile> profiles = new ArrayList<>();
    private BukkitTask tickTask;

    DoppelgangerNPCs(Doppelganger game) {
        this.game = game;
        this.settings = NPCSettings.from(game);
        this.workstations = new NPCWorkstationRegistry(game, settings);
    }

    void start() {
        cleanup();

        List<ResolvableProfile> assignments = profilesToSpawn();

        game.log("NPC startup: "
            + assignments.size() + " mannequins requested.");

        if (assignments.isEmpty()) {
            game.log("No NPCs spawned: no player profiles available.");
            return;
        }

        List<GameLocation> spawns = new ArrayList<>(game.spawns());

        if (spawns.isEmpty()) {
            spawns.add(game.world().worldSpawn());
        }

        Collections.shuffle(spawns, game.random());

        int spawnedCount = 0;

        for (int index = 0; index < assignments.size(); index++) {
            ResolvableProfile profile = assignments.get(index);

            GameLocation spawn = spawns.get(index % spawns.size());

            try {
                GameEntity entity = game.world().spawn(
                    spawn,
                    GameEntities.MANNEQUIN
                );

                if (entity == null) {
                    game.log("NPC spawn returned null at index " + index);
                    continue;
                }

                boolean configured = entity.editEntity(
                    org.bukkit.entity.Mannequin.class,
                    mannequin -> configureMannequin(mannequin, profile)
                );

                if (!configured) {
                    game.log("Spawned entity is not a mannequin: "
                        + entity.type());

                    entity.remove();
                    continue;
                }

                entities.add(entity);
                profiles.add(profile);

                behaviors.add(new NPCBehavior(
                    game,
                    settings,
                    workstations,
                    entity
                ));

                spawnedCount++;

            } catch (Exception exception) {
                game.logError(
                    "Failed to spawn mannequin at index " + index,
                    exception
                );
            }
        }

    if (spawnedCount == 0) {
        game.log("NPC startup failed: no mannequins were created.");
        return;
    }

    try {
        workstations.discover();
    } catch (RuntimeException exception) {
        game.logError(
            "Workstation discovery failed; NPC wandering may be limited.",
            exception
        );
    }

    tickTask = game.plugin()
        .getServer()
        .getScheduler()
        .runTaskTimer(
            game.plugin(),
            this::tick,
            1L,
            1L
        );

    game.log("Spawned " + spawnedCount
        + " doppelganger townspeople.");
}

    void cleanup() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (GameEntity entity : List.copyOf(entities)) {
            if (!entity.isDead()) {
                entity.remove();
            }
        }
        entities.clear();
        behaviors.clear();
        profiles.clear();
    }

    boolean owns(GameEntity entity) {
        return entity != null && entities.stream().map(GameEntity::uuid).anyMatch(entity.uuid()::equals);
    }

    void remove(GameEntity entity) {
        if (entity == null) {
            return;
        }
        UUID uuid = entity.uuid();
        for (int index = entities.size() - 1; index >= 0; index--) {
            if (entities.get(index).uuid().equals(uuid)) {
                entities.remove(index);
                if (index < profiles.size()) {
                    profiles.remove(index);
                }
                behaviors.remove(index);
            }
        }
    }

    private void tick() {
        for (int index = behaviors.size() - 1; index >= 0; index--) {
            NPCBehavior behavior = behaviors.get(index);

            try {
                behavior.tick();
            } catch (RuntimeException | LinkageError error) {
                game.logError("NPC behavior failed. Disabling AI for this NPC.", error);
                behaviors.remove(index);
            }
        }
    }

    // private void removeFailedBehavior(int index, String message, Throwable throwable) {
    //     game.logError(message, throwable);
    //     behaviors.remove(index);
    //     if (index < entities.size()) {
    //         GameEntity entity = entities.remove(index);
    //         if (!entity.isDead()) {
    //             entity.remove();
    //         }
    //     }
    //     if (index < profiles.size()) {
    //         profiles.remove(index);
    //     }
    // }

    private void configureMannequin(org.bukkit.entity.Mannequin mannequin, ResolvableProfile profile) {
        mannequin.setImmovable(false);
        mannequin.setInvulnerable(false);
        mannequin.setSilent(true);
        mannequin.setCustomNameVisible(false);
        mannequin.setDescription(Component.empty());
        if (profile != null) {
            mannequin.setProfile(profile);
        }
    }

    private List<ResolvableProfile> profilesToSpawn() {
        List<GamePlayer> players = game.playerManager().getOnlinePlayers().stream()
            .filter(player -> player != null && player.isOnline())
            .toList();

        int realPlayerCount = players.size();
        int npcCount = realPlayerCount * Math.max(0, settings.appearancesPerPlayer - 1);
        if (players.isEmpty()) {
            game.log("No active players available for NPC profiles.");
            return List.of();
        }

        List<ResolvableProfile> playerProfiles = players.stream()
            .map(this::resolvableProfile)
            .filter(profile -> profile != null)
            .toList();
        if (playerProfiles.isEmpty()) {
            game.log("No player profiles were available for doppelganger NPC skins.");
            return List.of();
        }

        List<ResolvableProfile> assignments = new ArrayList<>();
        for (ResolvableProfile profile : playerProfiles) {
            for (int copy = 1; copy < settings.appearancesPerPlayer; copy++) {
                assignments.add(profile);
                if (assignments.size() >= npcCount) {
                    break;
                }
            }
            if (assignments.size() >= npcCount) {
                break;
            }
        }

        int fillIndex = 0;
        while (assignments.size() < npcCount) {
            assignments.add(playerProfiles.get(fillIndex++ % playerProfiles.size()));
        }
        Collections.shuffle(assignments, game.random());
        return assignments;
    }

    private ResolvableProfile resolvableProfile(GamePlayer player) {
        var profile = player.playerProfile();
        if (profile != null) {
            return ResolvableProfile.resolvableProfile(profile);
        }
        return ResolvableProfile.resolvableProfile()
            .uuid(player.uuid())
            .name(player.name())
            .build();
    }

    Set<UUID> uuids() {
        return entities.stream().map(GameEntity::uuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

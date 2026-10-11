package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Mannequin;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
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
    private BukkitTask tickTask;

    DoppelgangerNPCs(Doppelganger game) {
        this.game = game;
        this.settings = NPCSettings.from(game);
        this.workstations = new NPCWorkstationRegistry(game, settings);
    }

    void start() {
        cleanup();
        workstations.discover();
        List<ResolvableProfile> profiles = profilesToSpawn();
        List<com.donutsforlife11.donutgame.api.map.GameLocation> spawns = new ArrayList<>(game.spawns());
        Collections.shuffle(spawns, game.random());
        for (int index = 0; index < profiles.size(); index++) {
            var spawn = spawns.isEmpty() ? game.world().worldSpawn() : spawns.get(index % spawns.size());
            ResolvableProfile profile = profiles.get(index);
            GameEntity entity = game.world().spawn(spawn, GameEntities.MANNEQUIN, spawned -> {
                spawned.editEntity(Mannequin.class, mannequin -> configureMannequin(mannequin, profile));
            });
            entities.add(entity);
            behaviors.add(new NPCBehavior(game, settings, workstations, entity));
        }
        tickTask = game.plugin().getServer().getScheduler().runTaskTimer(game.plugin(), this::tick, 1L, 1L);
        game.log("Spawned " + entities.size() + " lightweight doppelganger NPCs.");
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
                behaviors.remove(index);
            }
        }
    }

    private void tick() {
        for (NPCBehavior behavior : List.copyOf(behaviors)) {
            behavior.tick();
        }
    }

    private void configureMannequin(Mannequin mannequin, ResolvableProfile profile) {
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
        int totalAppearances = Math.max(1, realPlayerCount) * settings.appearancesPerPlayer;
        int npcCount = Math.max(1, totalAppearances - realPlayerCount);
        if (players.isEmpty()) {
            return Collections.nCopies(npcCount, Mannequin.defaultProfile());
        }

        List<ResolvableProfile> realProfiles = players.stream()
            .map(this::resolvableProfile)
            .filter(profile -> profile != null)
            .toList();
        if (realProfiles.isEmpty()) {
            game.log("No live player profiles were available for NPC skins; skipping NPC spawn instead of using default skins.");
            return List.of();
        }

        List<ResolvableProfile> assignments = new ArrayList<>();
        int appearancesPerPlayer = Math.max(1, settings.appearancesPerPlayer);
        for (ResolvableProfile profile : realProfiles) {
            for (int count = 1; count < appearancesPerPlayer; count++) {
                assignments.add(profile);
                if (assignments.size() >= npcCount) {
                    break;
                }
            }
            if (assignments.size() >= npcCount) {
                break;
            }
        }
        int index = 0;
        while (assignments.size() < npcCount) {
            assignments.add(realProfiles.get(index++ % realProfiles.size()));
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

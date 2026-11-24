package com.donutsforlife11.doppelganger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.donutsforlife11.donutgame.api.entity.npc.GameNpc;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

final class DoppelgangerNpcManager {
    private final Doppelganger game;
    private final Map<UUID, GameNpc> bodies = new HashMap<>();
    private final Map<UUID, GamePlayer> owners = new HashMap<>();
    private final Map<UUID, GameNpc> bots = new LinkedHashMap<>();
    private final List<GameLocation> targets = new ArrayList<>();
    private boolean stopped;

    DoppelgangerNpcManager(Doppelganger game) { this.game = game; }

    void createCrowd(List<GamePlayer> players) {
        // TODO: Review text and string identifiers below before release.
        int copies = Math.max(2, game.config().getInt("npc.npc_per_player", 3));
        // TODO: The pool includes the player-controlled mannequins, rather than adding extra copies on top of the players.
        List<PlayerProfile> profiles = new ArrayList<>();
        for (GamePlayer player : players) for (int i = 0; i < copies; i++) profiles.add(player.playerProfile());
        Collections.shuffle(profiles);
        int index = 0;
        for (GamePlayer player : players) {
            GameNpc npc = create(player.spawnPoint(), profiles.get(index++));
            npc.setGravity(false);
            npc.setImmovable(true);
            // TODO: Hide the player's own mannequin so it cannot intercept their clicks or obscure their camera.
            npc.setVisibleTo(player, false);
            bodies.put(player.uuid(), npc);
            owners.put(npc.uuid(), player);
        }
        while (index < profiles.size()) {
            GameNpc npc = create(game.spawns().get(index % game.spawns().size()), profiles.get(index));
            bots.put(npc.uuid(), npc);
            index++;
        }
        findTargets();
    }

    private GameNpc create(GameLocation point, PlayerProfile profile) {
        GameNpc npc = game.world().newNpc(point, profile);
        npc.setDescriptionVisible(false);
        npc.setCollidable(false);
        npc.setAttackCooldown(5);
        npc.onAttack(player -> game.attack(player, npc));
        npc.onInteract(player -> game.disguise(player, npc));
        npc.controller().pathfindSettings().setOpenDoors(true);
        npc.controller().pathfindSettings().setRadius(32);
        npc.controller().setNavigationTimeout(300);
        npc.controller().setArrivalDistance(0.5);
        return npc;
    }

    private void findTargets() {
        // TODO: Review text and string identifiers below before release.
        targets.addAll(game.world().getPoints("npc_target"));
        // TODO: Review text and string identifiers below before release.
        var region = game.world().getRegion("town");
        if (region == null) {
            // TODO: Review text and string identifiers below before release.
            if (targets.isEmpty()) throw new IllegalStateException("Doppelganger needs a town region or npc_target points.");
            return;
        }
        // TODO: Review text and string identifiers below before release.
        if (region.blockVolume() > 262144) throw new IllegalStateException("The town scanning region must contain at most 262144 blocks; use npc_target points for larger maps.");
        Set<Material> workstations = new HashSet<>();
        // TODO: Review text and string identifiers below before release.
        for (String value : game.config().getStringList("npc.target_blocks")) {
            Material block = Material.matchMaterial(value);
            // TODO: Review text and string identifiers below before release.
            if (block == null) throw new IllegalArgumentException("Unknown NPC workstation " + value);
            workstations.add(block);
        }
        for (int x = region.min().blockX(); x <= region.max().blockX(); x++) {
            for (int z = region.min().blockZ(); z <= region.max().blockZ(); z++) {
                if (!game.world().isChunkLoaded(x, z)) continue;
                for (int y = region.min().blockY(); y <= region.max().blockY(); y++) {
                    if (!workstations.contains(game.world().blockAt(new GameLocation(x,y,z)).getType())) continue;
                    for (int[] direction : List.of(new int[]{1,0}, new int[]{-1,0}, new int[]{0,1}, new int[]{0,-1})) {
                        GameLocation next = new GameLocation(x+direction[0]+0.5, y, z+direction[1]+0.5);
                        if (game.world().blockAt(next).getType().isAir() && game.world().blockAt(new GameLocation(next.x(),y+1,next.z())).getType().isAir())
                            targets.add(next);
                    }
                }
            }
        }
        if (targets.isEmpty()) targets.addAll(game.spawns());
    }

    void startWandering() {
        for (GameNpc npc : bots.values()) {
            npc.controller().onNavigationSuccess(ignored -> schedule(npc));
            npc.controller().onNavigationFail(ignored -> schedule(npc));
            schedule(npc);
        }
    }

    private void schedule(GameNpc npc) {
        if (stopped || !bots.containsKey(npc.uuid())) return;
        // TODO: Review text and string identifiers below before release.
        int minimum = Math.max(1, game.config().getInt("npc.min_idle_time", 3));
        // TODO: Review text and string identifiers below before release.
        int maximum = Math.max(minimum, game.config().getInt("npc.max_idle_time", 6));
        game.timeManager().newTimer(ThreadLocalRandom.current().nextInt(minimum*20, maximum*20+1)).onFinish(ignored -> {
            if (stopped || !game.running() || !bots.containsKey(npc.uuid())) return;
            List<GameLocation> nearby = targets.stream().filter(p -> distance(npc.getLocation(),p) <= 32*32).toList();
            if (nearby.isEmpty()) { schedule(npc); return; }
            if (ThreadLocalRandom.current().nextDouble() < 0.25) { schedule(npc); return; }
            GameLocation destination = nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
            npc.controller().pathfindTo(destination);
            game.timeManager().newTimer(ThreadLocalRandom.current().nextInt(10,40)).onFinish(pause -> {
                if (stopped || !bots.containsKey(npc.uuid()) || !npc.controller().isNavigating()) return;
                npc.controller().pause();
                game.timeManager().newTimer(ThreadLocalRandom.current().nextInt(5,20)).onFinish(resume -> {
                    if (!stopped && bots.containsKey(npc.uuid())) npc.controller().resume();
                }).start();
            }).start();
        }).start();
    }

    void moveBody(GamePlayer player, GameLocation destination) {
        GameNpc npc = bodies.get(player.uuid());
        if (npc == null || npc.isDead()) return;
        npc.teleport(destination).thenAccept(moved -> {
            if (moved && bodies.get(player.uuid()) == npc && !npc.isDead()) npc.setRotation(destination.yaw(), destination.pitch());
        });
    }

    GameNpc body(GamePlayer player) { return bodies.get(player.uuid()); }
    GamePlayer owner(GameNpc npc) { return owners.get(npc.uuid()); }
    int botCount() { return bots.size(); }
    void removeBody(GamePlayer player) {
        GameNpc body = bodies.remove(player.uuid());
        if (body != null) { owners.remove(body.uuid()); body.remove(); }
    }
    void remove(GameNpc npc) {
        if (bots.remove(npc.uuid()) == null) return;
        game.world().particle(Particle.POOF, npc.getLocation(), 20);
        npc.remove();
    }
    void stop() {
        stopped = true;
        bots.values().forEach(npc -> npc.controller().cancelNavigation());
    }
    private double distance(GameLocation a, GameLocation b) { return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2); }
}

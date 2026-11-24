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
    private final Map<UUID, GameNpc> bots = new LinkedHashMap<>();
    private final List<GameLocation> targets = new ArrayList<>();
    private boolean stopped;

    DoppelgangerNpcManager(Doppelganger game) { this.game = game; }

    void createCrowd(List<GamePlayer> players) {
        // RODO: Review text and string identifiers below before release.
        int copies = Math.max(2, game.config().getInt("npc.npc_per_player", 3));
        // RODO: The pool includes the player-controlled mannequins, rather than adding extra copies on top of the players.
        List<PlayerProfile> profiles = new ArrayList<>();
        for (GamePlayer player : players) for (int i = 0; i < copies; i++) profiles.add(player.playerProfile());
        Collections.shuffle(profiles);
        int index = 0;
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
        npc.setInvulnerable(false);
        npc.setAttributeBase(org.bukkit.attribute.Attribute.MAX_HEALTH,20);
        npc.setHealth(20);
        npc.controller().pathfindSettings().setOpenDoors(true);
        npc.controller().pathfindSettings().setRadius(32);
        npc.controller().setNavigationTimeout(300);
        npc.controller().setArrivalDistance(0.25);
        return npc;
    }

    private void findTargets() {
        // RODO: Review text and string identifiers below before release.
        targets.addAll(game.world().getPoints("npc_target"));
        // RODO: Review text and string identifiers below before release.
        var region = game.world().getRegion("town");
        if (region == null) {
            // RODO: Review text and string identifiers below before release.
            if (targets.isEmpty()) {
                game.logWarning("No town region or npc_target points; NPCs will use spawn points.");
                targets.addAll(game.spawns());
            }
            return;
        }
        // RODO: Review text and string identifiers below before release.
        if (region.blockVolume() > 262144) {
            game.logWarning("Town region is too large to scan; using npc_target or spawn points.");
            if (targets.isEmpty()) targets.addAll(game.spawns());
            return;
        }
        Set<Material> workstations = new HashSet<>();
        // RODO: Review text and string identifiers below before release.
        for (String value : game.config().getStringList("npc.target_blocks")) {
            Material block = Material.matchMaterial(value);
            // RODO: Review text and string identifiers below before release.
            if (block == null) {
                game.logWarning("Ignoring unknown NPC workstation " + value);
                continue;
            }
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
        // RODO: Review text and string identifiers below before release.
        int minimum = Math.max(1, game.config().getInt("npc.min_idle_time", 1));
        // RODO: Review text and string identifiers below before release.
        int maximum = Math.max(minimum, game.config().getInt("npc.max_idle_time", 2));
        game.timeManager().newTimer(ThreadLocalRandom.current().nextInt(minimum*20, maximum*20+1)).onFinish(ignored -> {
            if (stopped || !game.running() || !bots.containsKey(npc.uuid())) return;
            List<GameLocation> nearby = targets.stream().filter(p -> distance(npc.getLocation(),p) <= 32*32).toList();
            if (nearby.isEmpty()) { schedule(npc); return; }

            GameLocation destination = nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
            List<GameLocation> path = npc.controller().findPath(destination);
            if (path.isEmpty()) { schedule(npc); return; }
            npc.controller().moveAlong(intermediatePoints(npc.getLocation(), path));

        }).start();
    }

    int botCount() { return bots.size(); }
    void remove(GameNpc npc) {
        if (bots.remove(npc.uuid()) == null) return;
        game.world().particle(Particle.POOF, npc.getLocation(), 20);
        npc.remove();
    }
    void stop() {
        stopped = true;
        bots.values().forEach(npc -> npc.controller().cancelNavigation());
    }
    private List<GameLocation> intermediatePoints(GameLocation start, List<GameLocation> path) {
        List<GameLocation> result = new ArrayList<>();
        GameLocation previous = start;
        for (GameLocation next : path) {
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(distance(previous, next)) / 0.75));
            for (int step = 1; step <= steps; step++) {
                double fraction = step / (double) steps;
                result.add(new GameLocation(next.world(),
                    previous.x() + (next.x() - previous.x()) * fraction,
                    previous.y() + (next.y() - previous.y()) * fraction,
                    previous.z() + (next.z() - previous.z()) * fraction));
            }
            previous = next;
        }
        return result;
    }

    private double distance(GameLocation a, GameLocation b) { return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2); }
}

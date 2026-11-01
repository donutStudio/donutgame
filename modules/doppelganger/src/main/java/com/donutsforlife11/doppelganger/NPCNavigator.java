package com.donutsforlife11.doppelganger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocation;

final class NPCNavigator {
    private static final double ARRIVAL_DISTANCE = 0.32;
    private static final double JUMP_VELOCITY = 0.42;
    private static final int STUCK_TICKS = 60;
    private static final float MAX_TURN_PER_TICK = 8.0f;
    private static final double VELOCITY_BLEND = 0.30;
    private static final double MIN_SPEED_FACTOR = 0.55;

    private static final int[][] CARDINALS = {
        { 1, 0 },
        { -1, 0 },
        { 0, 1 },
        { 0, -1 }
    };

    private final Doppelganger game;
    private final NPCSettings settings;
    private final GameEntity entity;
    private final ArrayDeque<PathNode> path = new ArrayDeque<>();
    private GameLocation lastLocation;
    private String lastFailure = "not started";
    private int stuckTicks;
    private float yaw;

    NPCNavigator(Doppelganger game, NPCSettings settings, GameEntity entity) {
        this.game = game;
        this.settings = settings;
        this.entity = entity;
        this.yaw = entity.location().yaw();
    }

    boolean moveTo(GameLocation target) {
        GameLocation start = entity.location();
        if (start == null || target == null) {
            lastFailure = start == null ? "entity is not loaded" : "target is null";
            return false;
        }

        PathNode startNode = standableNodeNear(start);
        PathNode goalNode = standableNodeNear(target);
        if (startNode == null || goalNode == null) {
            lastFailure = startNode == null ? "no standable start node" : "no standable goal node";
            return false;
        }
        if (startNode.equals(goalNode)) {
            path.clear();
            lastFailure = null;
            return true;
        }

        List<PathNode> route = findPath(startNode, goalNode);
        if (route.isEmpty()) {
            return false;
        }

        path.clear();
        path.addAll(route);
        lastLocation = start;
        stuckTicks = 0;
        yaw = start.yaw();
        lastFailure = null;
        debug("Path " + describe(startNode) + " -> " + describe(goalNode) + " has " + route.size() + " nodes.");
        return true;
    }

    String lastFailure() {
        return lastFailure == null ? "none" : lastFailure;
    }

    MoveResult tick() {
        if (path.isEmpty()) {
            entity.setVelocity(new Vector());
            return MoveResult.ARRIVED;
        }

        GameLocation location = entity.location();
        if (location == null) {
            return blocked("entity unloaded while moving");
        }

        PathNode next = path.peekFirst();
        GameLocation nextCenter = next.center(game);
        double dx = nextCenter.x() - location.x();
        double dz = nextCenter.z() - location.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = nextCenter.y() - location.y();

        if (horizontal <= ARRIVAL_DISTANCE && Math.abs(dy) <= 1.1) {
            path.removeFirst();
            openPassageBlocksAround(next);
            lastLocation = location;
            if (path.isEmpty()) {
                entity.setVelocity(new Vector(0.0, entity.velocity().getY(), 0.0));
                return MoveResult.ARRIVED;
            }
            return MoveResult.MOVING;
        }

        if (horizontal <= 0.0001) {
            return MoveResult.MOVING;
        }

        openPassageBlocksAround(next);
        face(dx, dz);

        double speed = settings.playerWalkSpeed(game);
        // Slow down slightly near the final destination.
        if (path.size() == 1) {
            speed *= Math.max(
                0.35,
                Math.min(1.0, horizontal)
            );
        }
        // Gradually blend horizontal movement toward the target.
        Vector current = entity.velocity();
        double desiredX = dx / horizontal * speed;
        double desiredZ = dz / horizontal * speed;
        double newX = current.getX() + (desiredX - current.getX()) * VELOCITY_BLEND;
        double newZ = current.getZ() + (desiredZ - current.getZ()) * VELOCITY_BLEND;
        // Prevent acceleration and maintain approximately
        // the intended walking speed.
        double magnitude = Math.sqrt(newX * newX + newZ * newZ);
        double maxSpeed = speed;
        if (magnitude > maxSpeed && magnitude > 0.00001) {
            newX = newX / magnitude * maxSpeed;
            newZ = newZ / magnitude * maxSpeed;
        }
        // Slow down during sharp turns.
        // This helps the mannequin follow a turn
        // instead of sliding unnaturally sideways.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double turnDifference = Math.abs(wrapDegrees(targetYaw - yaw));

        double speedFactor = Math.max(
            MIN_SPEED_FACTOR,
            1.0 - turnDifference / 180.0
        );

        newX *= speedFactor;
        newZ *= speedFactor;

        // Preserve gravity and existing vertical momentum.
        double vertical = current.getY();

        if (dy > 0.45 && vertical < 0.1) {
            vertical = JUMP_VELOCITY;
        }

        entity.setVelocity(new Vector(
            newX,
            vertical,
            newZ
        ));

        updateStuck(location);
        if (stuckTicks > STUCK_TICKS) {
            return blocked("stalled while following path");
        }
        return MoveResult.MOVING;
    }

    private MoveResult blocked(String reason) {
        path.clear();
        stuckTicks = 0;
        entity.setVelocity(new Vector());
        lastFailure = reason;
        debug(reason);
        return MoveResult.BLOCKED;
    }

    private void updateStuck(GameLocation location) {
        if (lastLocation == null) {
            lastLocation = location;
            return;
        }
        double dx = location.x() - lastLocation.x();
        double dz = location.z() - lastLocation.z();
        if (dx * dx + dz * dz < 0.0004) {
            stuckTicks++;
            return;
        }
        stuckTicks = 0;
        lastLocation = location;
    }

    private PathNode standableNodeNear(GameLocation location) {
        PathNode exact = PathNode.from(location);
        if (isStandable(exact)) {
            return exact;
        }
        for (int y = exact.y() + 1; y >= exact.y() - 2; y--) {
            PathNode candidate = new PathNode(exact.x(), y, exact.z());
            if (isStandable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<PathNode> findPath(PathNode start, PathNode goal) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::score));
        Map<PathNode, PathNode> parent = new HashMap<>();
        Map<PathNode, Double> costs = new HashMap<>();
        Set<PathNode> closed = new HashSet<>();

        open.add(new SearchNode(start, heuristic(start, goal)));
        costs.put(start, 0.0);

        int expanded = 0;
        while (!open.isEmpty() && expanded < settings.maxExpandedNodes) {
            expanded++;
            PathNode current = open.poll().node();
            if (!closed.add(current)) {
                continue;
            }
            if (current.equals(goal)) {
                return rebuildPath(parent, current);
            }

            for (PathNode neighbor : neighbors(current, goal)) {
                if (closed.contains(neighbor)) {
                    continue;
                }
                double cost = costs.get(current) + movementCost(current, neighbor);
                if (cost > settings.maxPathDistance || cost >= costs.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    continue;
                }
                parent.put(neighbor, current);
                costs.put(neighbor, cost);
                open.add(new SearchNode(neighbor, cost + heuristic(neighbor, goal)));
            }
        }

        lastFailure = open.isEmpty()
            ? "A* exhausted reachable nodes after " + expanded + " expansions"
            : "A* hit max-expanded-nodes=" + settings.maxExpandedNodes;
        debug(lastFailure + " from " + describe(start) + " to " + describe(goal));
        return List.of();
    }

    private List<PathNode> neighbors(PathNode current, PathNode goal) {
        List<PathNode> result = new ArrayList<>();
        for (int[] direction : CARDINALS) {
            int x = current.x() + direction[0];
            int z = current.z() + direction[1];
            if (!game.world().isChunkLoaded(x, z)) {
                continue;
            }

            for (int y : new int[] {current.y(), current.y() + 1, current.y() - 1}) {
                PathNode candidate = new PathNode(x, y, z);
                if (isStandable(candidate) && canStep(current, candidate)) {
                    result.add(candidate);
                    break;
                }
            }
        }
        return result;
    }

    private boolean canStep(PathNode from, PathNode to) {
        int vertical = to.y() - from.y();
        if (vertical > 1 || vertical < -1) {
            return false;
        }
        if (vertical > 0 && !hasHeadroom(from.x(), from.y() + 2, from.z())) {
            return false;
        }
        return true;
    }

    private boolean hasHeadroom(int x, int y, int z) {
        return NPCWorkstationRegistry.NPCPathRules.isPassable(settings, game.world().blockAt(new GameLocation(game.world(), x, y, z)));
    }

    private boolean isStandable(PathNode node) {
        return NPCWorkstationRegistry.NPCPathRules.isStandable(game, settings, node.x(), node.y(), node.z());
    }

    private double movementCost(PathNode from, PathNode to) {
        double cost = 1.0 + Math.abs(to.y() - from.y()) * 0.55;
        if (NPCWorkstationRegistry.NPCPathRules.isPassage(game, settings, to.x(), to.y(), to.z())
            || NPCWorkstationRegistry.NPCPathRules.isPassage(game, settings, to.x(), to.y() + 1, to.z())) {
            cost += 0.75;
        }
        return cost;
    }

    private void openPassageBlocksAround(PathNode node) {
        openPassageBlock(node.x(), node.y(), node.z());
        openPassageBlock(node.x(), node.y() + 1, node.z());
        for (int[] direction : CARDINALS) {
            openPassageBlock(node.x() + direction[0], node.y(), node.z() + direction[1]);
            openPassageBlock(node.x() + direction[0], node.y() + 1, node.z() + direction[1]);
        }
    }

    private void openPassageBlock(int x, int y, int z) {
        GameBlock block = game.world().blockAt(new GameLocation(game.world(), x, y, z));
        org.bukkit.block.data.BlockData data = block.blockData();
        if (data instanceof org.bukkit.block.data.Openable openable && !openable.isOpen()) {
            openable.setOpen(true);
            block.setBlockData(data);
        }
    }

    private double heuristic(PathNode current, PathNode goal) {
        return Math.abs(goal.x() - current.x()) + Math.abs(goal.y() - current.y()) + Math.abs(goal.z() - current.z());
    }

    private List<PathNode> rebuildPath(Map<PathNode, PathNode> parent, PathNode end) {
        ArrayDeque<PathNode> route = new ArrayDeque<>();
        for (PathNode current = end; current != null; current = parent.get(current)) {
            route.addFirst(current);
        }
        if (!route.isEmpty()) {
            route.removeFirst();
        }
        return List.copyOf(route);
    }

    private void face(double dx, double dz) {
        if (dx * dx + dz * dz < 0.000001) {
            return;
        }

        float targetYaw = (float) Math.toDegrees(
            Math.atan2(-dx, dz)
        );

        float difference = (float) wrapDegrees(targetYaw - yaw);

        float turn = Math.max(
            -MAX_TURN_PER_TICK,
            Math.min(MAX_TURN_PER_TICK, difference)
        );

        yaw = (float) wrapDegrees(yaw + turn);

        entity.setRotation(yaw, 0.0f);
    }

    private double wrapDegrees(double angle) {
        angle %= 360.0;
        if (angle >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    private void debug(String message) {
        if (settings.debugPathfinding) {
            game.log("[npc:path] " + entity.uuid() + " " + message);
        }
    }

    private String describe(PathNode node) {
        return node.x() + "," + node.y() + "," + node.z();
    }

    private record SearchNode(PathNode node, double score) {
    }

    enum MoveResult {
        MOVING,
        ARRIVED,
        BLOCKED
    }

    private record PathNode(int x, int y, int z) {
        static PathNode from(GameLocation location) {
            return new PathNode(location.blockX(), location.blockY(), location.blockZ());
        }

        GameLocation center(Doppelganger game) {
            return new GameLocation(game.world(), x + 0.5, y, z + 0.5);
        }
    }
}

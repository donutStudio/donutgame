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

import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocation;

final class NPCNavigator {
    private static final double PLAYER_JUMP_VELOCITY = 0.42;

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
    private GameLocation currentTarget;
    private int stuckTicks;
    private GameLocation lastLocation;
    private Vector driveVelocity = new Vector();
    private Vector lastAppliedVelocity = new Vector();
    private int externalMotionTicks;
    private float currentYaw;

    NPCNavigator(Doppelganger game, NPCSettings settings, GameEntity entity) {
        this.game = game;
        this.settings = settings;
        this.entity = entity;
    }

    boolean moveTo(GameLocation target) {
        return moveTo(target, false);
    }

    private boolean moveTo(GameLocation target, boolean preserveMotion) {
        GameLocation start = entity.location();
        if (start == null || target == null) {
            return false;
        }
        List<PathNode> route = findPath(
            PathNode.from(start),
            PathNode.from(target)
        );
        if (route.isEmpty()) {
            return false;
        }
        path.clear();
        path.addAll(route);
        currentTarget = target;
        stuckTicks = 0;
        lastLocation = start;
        if (!preserveMotion) {
            driveVelocity = new Vector();
            lastAppliedVelocity = new Vector();
            externalMotionTicks = 0;
            currentYaw = start.yaw();
        }
        return true;
    }

    boolean isWalking() {
        return !path.isEmpty();
    }

    MoveResult tick() {
        if (path.isEmpty()) {
            return MoveResult.ARRIVED;
        }
        GameLocation location = entity.location();
        if (location == null) {
            path.clear();
            return MoveResult.BLOCKED;
        }
        PathNode next = path.peekFirst();
        GameLocation nodeCenter = next.center(game);
        double nodeDx = nodeCenter.x() - location.x();
        double nodeDz = nodeCenter.z() - location.z();
        double nodeYDistance = nodeCenter.y() - location.y();
        double nodeHorizontalDistance = Math.sqrt(nodeDx * nodeDx + nodeDz * nodeDz);
        double baseSpeed = settings.playerWalkSpeed(game);
        if (nodeHorizontalDistance <= Math.max(0.10, baseSpeed * 0.55) && Math.abs(nodeYDistance) <= 0.75) {
            path.removeFirst();
            openNearbyDoors(next);
            if (path.isEmpty()) {
                driveVelocity.multiply(settings.playerWalkFriction());
            }
            GameLocation nextFacing = path.isEmpty() ? nodeCenter : steeringTarget(path.peekFirst(), nodeCenter);
            face(nextFacing.x() - nodeCenter.x(), nextFacing.z() - nodeCenter.z());
            lastLocation = location;
            return path.isEmpty() ? MoveResult.ARRIVED : MoveResult.MOVING;
        }

        if (yieldingToExternalMotion(location)) {
            return MoveResult.MOVING;
        }

        GameLocation nextCenter = steeringTarget(next, location);
        double dx = nextCenter.x() - location.x();
        double dz = nextCenter.z() - location.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 0.0001) {
            path.removeFirst();
            return path.isEmpty() ? MoveResult.ARRIVED : MoveResult.MOVING;
        }

        double inputScale = inputScale(nodeHorizontalDistance, location);
        Vector movement = nextVelocity(dx / distance, dz / distance, inputScale, baseSpeed);
        openNearbyDoors(next);
        face(dx, dz);
        Vector currentVelocity = entity.velocity();
        double verticalVelocity = currentVelocity.getY();
        if (nodeYDistance > 0.45 && Math.abs(verticalVelocity) < 0.05) {
            verticalVelocity = PLAYER_JUMP_VELOCITY;
        }
        entity.setVelocity(new Vector(movement.getX(), verticalVelocity, movement.getZ()));
        lastAppliedVelocity = movement.clone();
        updateStuck(location);
        if (stuckTicks > 60 && currentTarget != null) {
            return recoverFromStall(next);
        }
        return MoveResult.MOVING;
    }

    private MoveResult recoverFromStall(PathNode next) {
        openNearbyDoors(next);
        if (currentTarget != null && moveTo(currentTarget, true)) {
            return MoveResult.MOVING;
        }
        path.clear();
        driveVelocity = new Vector();
        lastAppliedVelocity = new Vector();
        return MoveResult.BLOCKED;
    }

    private double inputScale(double distance, GameLocation location) {
        double scale = 1.0;
        if (path.size() <= 2) {
            scale *= Math.max(0.0, Math.min(1.0, distance / 1.8));
        }
        PathNode first = path.peekFirst();
        PathNode second = secondPathNode();
        if (first != null && second != null) {
            double turn = Math.abs(angleBetween(location, first, second));
            if (turn > 35.0) {
                scale *= 0.72;
            }
        }
        return scale;
    }

    private Vector nextVelocity(double directionX, double directionZ, double inputScale, double maxSpeed) {
        if (inputScale > 0.02) {
            double acceleration = settings.playerWalkAcceleration(game) * inputScale;
            driveVelocity.add(new Vector(directionX * acceleration, 0.0, directionZ * acceleration));
        }

        Vector moving = driveVelocity.clone();
        double speed = Math.sqrt(moving.getX() * moving.getX() + moving.getZ() * moving.getZ());
        if (speed > maxSpeed) {
            moving.multiply(maxSpeed / speed);
        }
        driveVelocity = moving.clone().multiply(settings.playerWalkFriction());
        return moving;
    }

    private boolean yieldingToExternalMotion(GameLocation location) {
        Vector current = entity.velocity();
        double appliedDifferenceSquared = horizontalDifferenceSquared(current, lastAppliedVelocity);
        double frictionDifferenceSquared = horizontalDifferenceSquared(current, driveVelocity);
        double differenceSquared = Math.min(appliedDifferenceSquared, frictionDifferenceSquared);
        double currentHorizontalSquared = current.getX() * current.getX() + current.getZ() * current.getZ();
        if (externalMotionTicks > 0) {
            externalMotionTicks--;
            driveVelocity = new Vector(current.getX(), 0.0, current.getZ()).multiply(settings.playerWalkFriction());
            lastAppliedVelocity = new Vector(current.getX(), 0.0, current.getZ());
            lastLocation = location;
            return true;
        }
        if (currentHorizontalSquared > 0.0025 && differenceSquared > 0.01) {
            externalMotionTicks = 10;
            driveVelocity = new Vector(current.getX(), 0.0, current.getZ()).multiply(settings.playerWalkFriction());
            lastAppliedVelocity = new Vector(current.getX(), 0.0, current.getZ());
            lastLocation = location;
            return true;
        }
        return false;
    }

    private double horizontalDifferenceSquared(Vector left, Vector right) {
        double differenceX = left.getX() - right.getX();
        double differenceZ = left.getZ() - right.getZ();
        return differenceX * differenceX + differenceZ * differenceZ;
    }

    private GameLocation steeringTarget(PathNode next, GameLocation location) {
        PathNode after = secondPathNode();
        if (after == null) {
            return next.center(game);
        }
        GameLocation nextCenter = next.center(game);
        double dx = nextCenter.x() - location.x();
        double dz = nextCenter.z() - location.z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance > 0.65) {
            return nextCenter;
        }
        GameLocation afterCenter = after.center(game);
        double blend = Math.max(0.0, Math.min(0.55, (0.65 - horizontalDistance) / 0.65));
        return new GameLocation(
            game.world(),
            nextCenter.x() + (afterCenter.x() - nextCenter.x()) * blend,
            nextCenter.y() + (afterCenter.y() - nextCenter.y()) * blend,
            nextCenter.z() + (afterCenter.z() - nextCenter.z()) * blend
        );
    }

    private PathNode secondPathNode() {
        if (path.size() < 2) {
            return null;
        }
        var iterator = path.iterator();
        iterator.next();
        return iterator.next();
    }

    private void updateStuck(GameLocation location) {
        if (lastLocation == null) {
            lastLocation = location;
            return;
        }
        double dx = location.x() - lastLocation.x();
        double dz = location.z() - lastLocation.z();
        if ((dx * dx + dz * dz) < 0.0004) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastLocation = location;
        }
    }

    private void face(double dx, double dz) {
        if (Math.abs(dx) >= 0.0001 || Math.abs(dz) >= 0.0001) {
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            currentYaw = rotateToward(currentYaw, targetYaw, 9.0f);
            entity.setRotation(currentYaw, 0.0f);
        }
    }

    private List<PathNode> findPath(PathNode start, PathNode goal) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::score));
        Map<PathNode, PathNode> parent = new HashMap<>();
        Map<PathNode, Double> costs = new HashMap<>();
        Set<PathNode> closed = new HashSet<>();

        open.add(new SearchNode(start, heuristic(start, goal)));
        costs.put(start, 0.0);
        int expanded = 0;

        while (!open.isEmpty() && expanded++ < settings.maxExpandedNodes) {
            PathNode current = open.poll().node();
            if (!closed.add(current)) {
                continue;
            }
            if (current.equals(goal) || heuristic(current, goal) <= 1.0) {
                return rebuildPath(parent, current);
            }
            for (PathNode neighbor : neighbors(current, goal)) {
                if (closed.contains(neighbor)) {
                    continue;
                }
                double nextCost = costs.get(current) + movementCost(current, neighbor);
                if (nextCost > settings.maxPathDistance) {
                    continue;
                }
                if (nextCost >= costs.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    continue;
                }
                parent.put(neighbor, current);
                costs.put(neighbor, nextCost);
                open.add(new SearchNode(neighbor, nextCost + heuristic(neighbor, goal)));
            }
        }
        return List.of();
    }

    private List<PathNode> neighbors(PathNode current, PathNode goal) {
        List<PathNode> neighbors = new ArrayList<>();
        for (int[] direction : CARDINALS) {
            int x = current.x() + direction[0];
            int z = current.z() + direction[1];
            if (!game.world().isChunkLoaded(x, z)) {
                continue;
            }
            for (int y : new int[] { current.y(), current.y() + 1, current.y() - 1, current.y() - 2 }) {
                PathNode candidate = new PathNode(x, y, z);
                if ((candidate.equals(goal) || NPCWorkstationRegistry.NPCPathRules.isStandable(game, settings, x, y, z))
                    && noSharpCornerCut(current, candidate)) {
                    neighbors.add(candidate);
                    break;
                }
            }
        }
        return neighbors;
    }

    private boolean noSharpCornerCut(PathNode from, PathNode to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx == 0 || dz == 0) {
            return true;
        }
        return NPCWorkstationRegistry.NPCPathRules.isStandable(game, settings, from.x() + dx, to.y(), from.z())
            && NPCWorkstationRegistry.NPCPathRules.isStandable(game, settings, from.x(), to.y(), from.z() + dz);
    }

    private double movementCost(PathNode from, PathNode to) {
        double cost = 1.0 + Math.abs(to.y() - from.y()) * 0.35;
        int verticalDelta = to.y() - from.y();
        boolean passage = isPassage(to.x(), to.y(), to.z()) || isPassage(to.x(), to.y() + 1, to.z());
        if (passage) {
            cost -= 0.35;
        }
        if (verticalDelta > 0 && !passage) {
            cost += 4.0;
        }
        if (verticalDelta < 0) {
            cost += 0.7;
        }
        return cost;
    }

    private void openNearbyDoors(PathNode node) {
        openDoor(node.x(), node.y(), node.z());
        openDoor(node.x(), node.y() + 1, node.z());
        for (int[] direction : CARDINALS) {
            openDoor(node.x() + direction[0], node.y(), node.z() + direction[1]);
            openDoor(node.x() + direction[0], node.y() + 1, node.z() + direction[1]);
        }
    }

    private boolean isPassage(int x, int y, int z) {
        return NPCWorkstationRegistry.NPCPathRules.isPassage(game, settings, x, y, z)
            && game.world().blockAt(new GameLocation(game.world(), x, y, z)).blockData() instanceof Openable;
    }

    private void openDoor(int x, int y, int z) {
        GameBlock block = game.world().blockAt(new GameLocation(game.world(), x, y, z));
        BlockData data = block.blockData();
        if (data instanceof Openable openable && !openable.isOpen()) {
            openable.setOpen(true);
            block.setBlockData(data);
        }
    }

    private double heuristic(PathNode current, PathNode goal) {
        return Math.abs(goal.x() - current.x()) + Math.abs(goal.y() - current.y()) + Math.abs(goal.z() - current.z());
    }

    private double angleBetween(GameLocation location, PathNode first, PathNode second) {
        if (first == null || second == null) {
            return 0.0;
        }
        GameLocation firstCenter = first.center(game);
        GameLocation secondCenter = second.center(game);
        double firstYaw = Math.toDegrees(Math.atan2(-(firstCenter.x() - location.x()), firstCenter.z() - location.z()));
        double secondYaw = Math.toDegrees(Math.atan2(-(secondCenter.x() - firstCenter.x()), secondCenter.z() - firstCenter.z()));
        return wrapDegrees(secondYaw - firstYaw);
    }

    private float rotateToward(float current, float target, float maxStep) {
        float delta = (float) wrapDegrees(target - current);
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
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

    private List<PathNode> rebuildPath(Map<PathNode, PathNode> parent, PathNode end) {
        ArrayDeque<PathNode> route = new ArrayDeque<>();
        PathNode current = end;
        while (current != null) {
            route.addFirst(current);
            current = parent.get(current);
        }
        if (!route.isEmpty()) {
            route.removeFirst();
        }
        return List.copyOf(route);
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

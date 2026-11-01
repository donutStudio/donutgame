package com.donutsforlife11.donutgame.api.entity.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.api.map.GameLocation;

public final class GameNpcController {
    private final GameNpc npc;
    private final PathfindSettings settings = new PathfindSettings();
    private List<GameLocation> route = List.of();
    private int waypoint;
    private Supplier<GameLocation> following;
    private GameLocation target;
    private boolean pathfinding;
    private boolean navigating;
    private boolean paused;
    private int elapsed;
    private int repathTicks;
    private int retryTicks;
    private int timeout = 200;
    private double arrivalDistance = 0.35;
    private double navigationRange = 32;
    private double verticalTolerance = 1;
    private Consumer<GameNpc> successAction;
    private Consumer<GameNpc> failAction;
    private final Map<org.bukkit.util.BlockVector, List<BoundingBox>> collisionShapes = new HashMap<>();

    GameNpcController(GameNpc npc) {
        this.npc = npc;
    }

    public void moveTo(GameLocation location) {
        navigate(List.of(Objects.requireNonNull(location, "location")), null, false);
    }

    public void moveAlong(List<GameLocation> locations) {
        navigate(List.copyOf(locations), null, false);
    }

    public void follow(Supplier<GameLocation> location) {
        navigate(List.of(), Objects.requireNonNull(location, "location"), false);
    }

    public void follow(GameEntityBase entity) {
        Objects.requireNonNull(entity, "entity");
        follow(() -> entity.isDead() || (entity instanceof com.donutsforlife11.donutgame.api.player.GamePlayer player
            && !player.isOnline()) ? null : entity.location());
    }

    public void pathfindTo(GameLocation location) {
        navigate(List.of(Objects.requireNonNull(location, "location")), null, true);
    }

    public void pathfindFollowing(Supplier<GameLocation> location) {
        navigate(List.of(), Objects.requireNonNull(location, "location"), true);
    }

    public void pathfindFollowing(GameEntityBase entity) {
        Objects.requireNonNull(entity, "entity");
        pathfindFollowing(() -> entity.isDead() || (entity instanceof com.donutsforlife11.donutgame.api.player.GamePlayer player
            && !player.isOnline()) ? null : entity.location());
    }

    private void navigate(List<GameLocation> locations, Supplier<GameLocation> following, boolean pathfinding) {
        for (GameLocation location : locations) {
            if ((location.world() != null && location.world() != npc.world())
                || !Double.isFinite(location.x()) || !Double.isFinite(location.y()) || !Double.isFinite(location.z())) {
                throw new IllegalArgumentException("Navigation locations must be finite and in the NPC's world.");
            }
        }
        cancelNavigation();
        this.following = following;
        this.pathfinding = pathfinding;
        target = locations.isEmpty() ? null : locations.getLast().inWorld(npc.world());
        navigating = true;
        if (npc.isImmovable()) {
            this.following = null;
            finishNavigation(false);
        } else if (following == null) {
            route = pathfinding ? findPath(target) : locations;
            if (route.isEmpty()) {
                finishNavigation(locations.isEmpty());
            }
        } else {
            navigating = false; // The first supplied destination starts the first attempt.
        }
    }

    public void cancelNavigation() {
        collisionShapes.clear();
        following = null;
        target = null;
        route = List.of();
        waypoint = 0;
        elapsed = 0;
        retryTicks = 0;
        repathTicks = 0;
        boolean wasNavigating = navigating;
        navigating = false;
        if (wasNavigating) {
            Vector velocity = npc.mannequin.getVelocity();
            npc.mannequin.setVelocity(velocity.setX(0).setZ(0));
            npc.mannequin.setJumping(false);
        }
    }

    public void pause() {
        paused = true;
        if (navigating) {
            npc.mannequin.setVelocity(npc.mannequin.getVelocity().setX(0).setZ(0));
            npc.mannequin.setJumping(false);
        }
    }

    public void resume() {
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isNavigating() {
        return navigating;
    }

    public GameLocation navigationTarget() {
        return target;
    }

    public void setArrivalDistance(double range) {
        if (!Double.isFinite(range) || range <= 0) {
            throw new IllegalArgumentException("Arrival distance must be finite and positive.");
        }
        arrivalDistance = range;
    }

    public double arrivalDistance() {
        return arrivalDistance;
    }

    public void setNavigationRange(double range) {
        if (!Double.isFinite(range) || range <= 0) {
            throw new IllegalArgumentException("Navigation range must be finite and positive.");
        }
        navigationRange = range;
    }

    public double navigationRange() {
        return navigationRange;
    }

    public void setNavigationTimeout(int ticks) {
        if (ticks < -1) {
            throw new IllegalArgumentException("Navigation timeout must be -1 or nonnegative.");
        }
        timeout = ticks;
    }

    public int navigationTimeout() {
        return timeout;
    }

    public void setVerticalTolerance(double height) {
        if (!Double.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("Vertical tolerance must be finite and nonnegative.");
        }
        verticalTolerance = height;
    }

    public double verticalTolerance() {
        return verticalTolerance;
    }

    public PathfindSettings pathfindSettings() {
        return settings;
    }

    public void onNavigationSuccess(Consumer<GameNpc> action) {
        successAction = action;
    }

    public void onNavigationFail(Consumer<GameNpc> action) {
        failAction = action;
    }

    void finishNavigation(boolean success) {
        navigating = false;
        target = null;
        route = List.of();
        waypoint = 0;
        elapsed = 0;
        retryTicks = success ? 0 : 40;
        npc.mannequin.setVelocity(npc.mannequin.getVelocity().setX(0).setZ(0));
        npc.mannequin.setJumping(false);
        Consumer<GameNpc> action = success ? successAction : failAction;
        if (action != null) {
            action.accept(npc);
        }
    }

    void tick() {
        collisionShapes.clear();
        if (paused || (!navigating && following == null)) {
            return;
        }
        if (npc.isImmovable()) {
            following = null;
            if (navigating) {
                finishNavigation(false);
            }
            return;
        }
        Location position = npc.mannequin.getLocation();
        if (following != null) {
            GameLocation supplied = following.get();
            boolean valid = supplied != null && (supplied.world() == null || supplied.world() == npc.world())
                && Double.isFinite(supplied.x()) && Double.isFinite(supplied.y()) && Double.isFinite(supplied.z());
            double distance = valid ? position.toVector().distanceSquared(new Vector(supplied.x(), supplied.y(), supplied.z()))
                : Double.POSITIVE_INFINITY;
            if (distance > navigationRange * navigationRange) {
                if (navigating) {
                    finishNavigation(false);
                }
                return;
            }
            if (!navigating && distance <= arrivalDistance * arrivalDistance) {
                return;
            }
            if (!navigating && retryTicks > 0) {
                retryTicks--;
                return;
            }
            boolean changed = target == null || new Vector(target.x(), target.y(), target.z())
                .distanceSquared(new Vector(supplied.x(), supplied.y(), supplied.z())) > 0.25;
            if (!navigating || (changed && (!pathfinding || --repathTicks <= 0))) {
                target = supplied.inWorld(npc.world());
                if (!navigating) {
                    elapsed = 0;
                }
                navigating = true;
                repathTicks = 10;
                route = pathfinding ? findPath(target) : List.of(target);
                waypoint = 0;
                if (route.isEmpty()) {
                    finishNavigation(false);
                    return;
                }
            }
        }
        if (!navigating) {
            return;
        }
        GameLocation next = route.get(waypoint);
        double dx = next.x() - position.getX();
        double dy = next.y() - position.getY();
        double dz = next.z() - position.getZ();
        double horizontal = Math.hypot(dx, dz);
        double range = waypoint == route.size() - 1 ? arrivalDistance : Math.min(0.2, arrivalDistance);
        if (horizontal * horizontal + dy * dy <= range * range) {
            if (++waypoint == route.size()) {
                finishNavigation(true);
            }
            return;
        }
        if (timeout != -1 && elapsed++ >= timeout) {
            finishNavigation(false);
            return;
        }
        if (horizontal < 1.0E-6) {
            return;
        }
        double ux = dx / horizontal;
        double uz = dz / horizontal;
        // Probe the full hitbox ahead. The same geometry is used by A* and direct steering.
        double probe = Math.min(horizontal, 0.45);
        double floor = standingHeight(position.getX() + ux * probe, position.getY(), position.getZ() + uz * probe);
        if (!Double.isFinite(floor) || !canTraverse(position.getX(), position.getY(), position.getZ(),
            position.getX() + ux * probe, floor, position.getZ() + uz * probe)) {
            npc.mannequin.setVelocity(npc.mannequin.getVelocity().setX(0).setZ(0));
            if (pathfinding && --repathTicks <= 0) {
                repathTicks = 10;
                route = findPath(target);
                waypoint = 0;
                if (route.isEmpty()) {
                    finishNavigation(false);
                }
            }
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        npc.mannequin.setRotation(yaw, position.getPitch());
        npc.mannequin.setBodyYaw(yaw);
        double rise = floor - position.getY();
        double step = Math.min(verticalTolerance, npc.attributeValue(Attribute.STEP_HEIGHT));
        if (rise > step + 1.0E-5 && npc.mannequin.isOnGround()) {
            npc.jump();
        }
        // Match vanilla ground acceleration; the mannequin applies native friction, gravity,
        // collisions and jumping. No teleports or replacement physics loop are used.
        Block below = position.getWorld().getBlockAt(position.getBlockX(), (int) Math.floor(position.getY() - 0.500001), position.getBlockZ());
        double friction = below.getType().getSlipperiness();
        double speed = npc.attributeValue(Attribute.MOVEMENT_SPEED) * (npc.isSprinting() && !npc.isSneaking() ? 1.3 : 1.0);
        double acceleration = npc.mannequin.isInWater() ? 0.02 : npc.mannequin.isOnGround()
            ? speed * 0.216 / (friction * friction * friction) : npc.isSprinting() ? 0.026 : 0.02;
        acceleration *= 0.98 * (npc.isSneaking() ? npc.attributeValue(Attribute.SNEAKING_SPEED) : 1.0);
        Vector velocity = npc.mannequin.getVelocity();
        velocity.setX(velocity.getX() + ux * acceleration).setZ(velocity.getZ() + uz * acceleration);
        // Brake near a waypoint without replacing vertical momentum or overshooting a turn.
        double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
        if (horizontalSpeed > horizontal) {
            velocity.setX(ux * horizontal).setZ(uz * horizontal);
        }
        npc.mannequin.setVelocity(velocity);
    }

    /** Bounded A*: cardinal edges avoid diagonal corner cutting; no chunks are loaded. */
    public List<GameLocation> findPath(GameLocation location) {
        Objects.requireNonNull(location, "location");
        if ((location.world() != null && location.world() != npc.world()) || !Double.isFinite(location.x())
            || !Double.isFinite(location.y()) || !Double.isFinite(location.z())) {
            throw new IllegalArgumentException("Path destination must be finite and in the NPC's world.");
        }
        Location start = npc.mannequin.getLocation();
        collisionShapes.clear();
        double destinationHeight = standingHeight(location.x(), location.y(), location.z());
        if (!Double.isFinite(destinationHeight) || Math.abs(destinationHeight - location.y()) > arrivalDistance) {
            collisionShapes.clear();
            return List.of();
        }
        Vector destination = new Vector(location.x(), location.y(), location.z());
        double radiusSquared = settings.radius() * settings.radius();
        if (start.toVector().distanceSquared(destination) > radiusSquared) {
            collisionShapes.clear();
            return List.of();
        }
        // Each cell's collision surface is evaluated once per search, including failed cells.
        record Cell(int x, long y, int z) { }
        record Node(GameLocation location, Cell cell, Node parent, double cost, double score) { }
        Map<Cell, Double> heights = new HashMap<>();
        Map<Cell, Double> costs = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Cell startCell = new Cell(start.getBlockX(), Math.round(start.getY() * 16), start.getBlockZ());
        open.add(new Node(new GameLocation(npc.world(), start.getX(), start.getY(), start.getZ()), startCell,
            null, 0, start.toVector().distance(destination)));
        costs.put(startCell, 0.0);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 4096) {
            Node current = open.poll();
            if (current.cost() > costs.getOrDefault(current.cell(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            GameLocation at = current.location();
            // Connect to the exact destination rather than silently accepting a nearby block.
            if (current.cell().x() == location.blockX() && current.cell().z() == location.blockZ()
                && Math.abs(at.y() - destinationHeight) <= arrivalDistance
                && canTraverse(at.x(), at.y(), at.z(), location.x(), destinationHeight, location.z())) {
                List<GameLocation> path = new ArrayList<>();
                path.add(new GameLocation(npc.world(), location.x(), destinationHeight, location.z()));
                for (Node node = current; node.parent() != null; node = node.parent()) {
                    path.add(node.location());
                }
                Collections.reverse(path);
                collisionShapes.clear();
                return List.copyOf(path);
            }
            for (int direction = 0; direction < 4; direction++) {
                int x = current.cell().x() + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                int z = current.cell().z() + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                double px = x + 0.5;
                double pz = z + 0.5;
                if ((px - start.getX()) * (px - start.getX()) + (pz - start.getZ()) * (pz - start.getZ()) > radiusSquared) {
                    continue;
                }
                Cell probe = new Cell(x, Math.round(at.y() * 16), z);
                double y = heights.computeIfAbsent(probe, ignored -> standingHeight(px, at.y(), pz));
                if (!Double.isFinite(y) || (px - start.getX()) * (px - start.getX())
                    + (y - start.getY()) * (y - start.getY()) + (pz - start.getZ()) * (pz - start.getZ()) > radiusSquared
                    || !canTraverse(at.x(), at.y(), at.z(), px, y, pz)) {
                    continue;
                }
                Cell cell = new Cell(x, Math.round(y * 16), z);
                double cost = current.cost() + Math.hypot(Math.hypot(px - at.x(), pz - at.z()), y - at.y());
                if (cost >= costs.getOrDefault(cell, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                costs.put(cell, cost);
                double heuristic = Math.sqrt((px - location.x()) * (px - location.x())
                    + (y - location.y()) * (y - location.y()) + (pz - location.z()) * (pz - location.z()));
                open.add(new Node(new GameLocation(npc.world(), px, y, pz), cell, current, cost, cost + heuristic));
            }
        }
        collisionShapes.clear();
        return List.of();
    }

    /** Find a safe collision surface under the NPC's footprint, including partial blocks. */
    private double standingHeight(double x, double fromY, double z) {
        World world = npc.mannequin.getWorld();
        double halfWidth = npc.mannequin.getWidth() / 2 - 1.0E-5;
        double maximum = fromY + Math.min(verticalTolerance, 1.25);
        double minimum = fromY - 3;
        double highest = Double.NEGATIVE_INFINITY;
        boolean safe = false;
        for (int bx = (int) Math.floor(x - halfWidth); bx <= (int) Math.floor(x + halfWidth); bx++) {
            for (int bz = (int) Math.floor(z - halfWidth); bz <= (int) Math.floor(z + halfWidth); bz++) {
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    return Double.NaN;
                }
                for (int by = Math.max(world.getMinHeight(), (int) Math.floor(minimum) - 1);
                     by <= Math.min(world.getMaxHeight() - 1, (int) Math.floor(maximum)); by++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    var shapes = collisionShapes.computeIfAbsent(new org.bukkit.util.BlockVector(bx, by, bz),
                        ignored -> List.copyOf(block.getCollisionShape().getBoundingBoxes()));
                    for (BoundingBox shape : shapes) {
                        double top = by + shape.getMaxY();
                        if (top < minimum || top > maximum + 1.0E-5 || top < highest - 1.0E-5
                            || bx + shape.getMaxX() <= x - halfWidth || bx + shape.getMinX() >= x + halfWidth
                            || bz + shape.getMaxZ() <= z - halfWidth || bz + shape.getMinZ() >= z + halfWidth) {
                            continue;
                        }
                        // Unsafe supports must win over a safe floor underneath them.
                        if (top > highest + 1.0E-5) {
                            highest = top;
                            safe = settings.walkable.contains(block.getType());
                        } else {
                            safe &= settings.walkable.contains(block.getType());
                        }
                    }
                }
            }
        }
        return safe && Double.isFinite(highest) && canTraverse(x, highest, z, x, highest, z) ? highest : Double.NaN;
    }

    /** Sweep a standing hitbox over an edge, checking headroom, hazards and real shapes. */
    private boolean canTraverse(double x, double y, double z, double tx, double ty, double tz) {
        if (ty - y > Math.min(verticalTolerance, 1.25) + 1.0E-5 || y - ty > 3.0) {
            return false;
        }
        World world = npc.mannequin.getWorld();
        double halfWidth = npc.mannequin.getWidth() / 2 - 1.0E-5;
        double height = npc.mannequin.getHeight();
        double topY = Math.max(y, ty);
        if (Math.min(y, ty) < world.getMinHeight() || topY + height >= world.getMaxHeight()) {
            return false;
        }
        BoundingBox swept = new BoundingBox(Math.min(x, tx) - halfWidth, topY + 1.0E-5, Math.min(z, tz) - halfWidth,
            Math.max(x, tx) + halfWidth, topY + height - 1.0E-5, Math.max(z, tz) + halfWidth);
        BoundingBox landing = new BoundingBox(tx - halfWidth, ty + 1.0E-5, tz - halfWidth,
            tx + halfWidth, ty + height - 1.0E-5, tz + halfWidth);
        BoundingBox lift = new BoundingBox(x - halfWidth, y + 1.0E-5, z - halfWidth,
            x + halfWidth, topY + height - 1.0E-5, z + halfWidth);
        for (int bx = (int) Math.floor(swept.getMinX()); bx <= (int) Math.floor(swept.getMaxX()); bx++) {
            for (int bz = (int) Math.floor(swept.getMinZ()); bz <= (int) Math.floor(swept.getMaxZ()); bz++) {
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    return false;
                }
                for (int by = Math.max(world.getMinHeight(), (int) Math.floor(Math.min(y, ty)) - 1);
                     by <= Math.min(world.getMaxHeight() - 1, (int) Math.floor(swept.getMaxY())); by++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    var shapes = collisionShapes.computeIfAbsent(new org.bukkit.util.BlockVector(bx, by, bz),
                        ignored -> List.copyOf(block.getCollisionShape().getBoundingBoxes()));
                    BoundingBox blockBox = new BoundingBox(bx, by, bz, bx + 1, by + 1, bz + 1);
                    if ((blockBox.overlaps(swept) || blockBox.overlaps(landing) || blockBox.overlaps(lift))
                        && !settings.crossable.contains(block.getType())) {
                        return false;
                    }
                    for (BoundingBox shape : shapes) {
                        BoundingBox box = shape.clone().shift(bx, by, bz);
                        if (box.overlaps(swept) || box.overlaps(landing) || box.overlaps(lift)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}

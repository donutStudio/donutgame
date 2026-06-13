package com.donutsforlife11.donutgame.api.border;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.game.GameContext;

public class BorderManager {
    private static final int PARTICLE_INTERVAL = 4;
    private static final int STATIONARY_RENDER_MULTIPLIER = 2;
    private static final double SURFACE_HEIGHT_OFFSET = 0.02;

    /*
     * Smaller spacing = denser shell = more visible, but more particles.
     *
     * These values mean:
     * - moving borders try to place particles around every 6 blocks
     * - stationary borders try to place particles around every 8 blocks
     *
     * If a border is huge, the budget below will automatically loosen the spacing
     * rather than allowing particle counts to explode.
     */
    private static final double MOVING_PARTICLE_SPACING = 6.0;
    private static final double STATIONARY_PARTICLE_SPACING = 8.0;

    /*
     * Hard performance caps per border per render pass.
     *
     * Stationary borders render less often, but still get a lower budget because
     * they do not need to communicate motion.
     */
    private static final int MAX_MOVING_PARTICLES_PER_BORDER = 2500;
    private static final int MAX_STATIONARY_PARTICLES_PER_BORDER = 1600;

    /*
     * Per-axis caps prevent one enormous face from generating absurd grid sizes.
     */
    private static final int MAX_FACE_SAMPLES_PER_AXIS = 48;
    private static final int MAX_EDGE_SAMPLES_PER_AXIS = 96;

    private static final BorderParticle DEFAULT_STATIONARY_PARTICLE = BorderParticle.of(Particle.TRIAL_OMEN);
    private static final BorderParticle DEFAULT_MOVING_PARTICLE = BorderParticle.of(Particle.RAID_OMEN);

    private final GameContext context;
    private final MapManager mapManager;
    private final PlayerManager playerManager;
    private final TimeManager timeManager;
    private final Set<GameBorder> borders = ConcurrentHashMap.newKeySet();

    private BukkitTask particleTask;
    private BukkitTask damageTask;
    private int particleTicks;
    private double damageAmount;
    private int damageInterval;
    private boolean shutdown;

    private BorderParticle stationaryParticle = DEFAULT_STATIONARY_PARTICLE;
    private BorderParticle movingParticle = DEFAULT_MOVING_PARTICLE;

    public enum BorderShape {
        CUBOID(vector -> Math.max(Math.abs(vector.getX()), Math.max(Math.abs(vector.getY()), Math.abs(vector.getZ())))),
        CYLINDROID(vector -> Math.max(Math.abs(vector.getY()), Math.hypot(vector.getX(), vector.getZ()))),
        ELLIPSOID(vector -> Math.sqrt(square(vector.getX()) + square(vector.getY()) + square(vector.getZ())));

        private final ShapeMetric metric;

        BorderShape(ShapeMetric metric) {
            this.metric = metric;
        }

        double metric(Vector vector) {
            return metric.measure(vector);
        }
    }

    public BorderManager(GameContext context, MapManager mapManager, PlayerManager playerManager, TimeManager timeManager) {
        this.context = Objects.requireNonNull(context, "context");
        this.mapManager = Objects.requireNonNull(mapManager, "mapManager");
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
        this.timeManager = Objects.requireNonNull(timeManager, "timeManager");
    }

    public Collection<GameBorder> getBorders() {
        return List.copyOf(borders);
    }

    public void setStationaryParticle(BorderParticle particle) {
        ensureActive();
        stationaryParticle = Objects.requireNonNull(particle, "particle");
    }

    public void setMovingParticle(BorderParticle particle) {
        ensureActive();
        movingParticle = Objects.requireNonNull(particle, "particle");
    }

    public void setBorderParticles(BorderParticle stationaryParticle, BorderParticle movingParticle) {
        ensureActive();
        this.stationaryParticle = Objects.requireNonNull(stationaryParticle, "stationaryParticle");
        this.movingParticle = Objects.requireNonNull(movingParticle, "movingParticle");
    }

    public GameBorder createBorder(BorderShape shape, Location center, Vector dimensions) {
        ensureActive();

        GameBorder border = new GameBorder(
            this,
            Objects.requireNonNull(shape, "shape"),
            requireCenter(center),
            requireDimensions(dimensions)
        );

        borders.add(border);
        ensureParticleTask();
        return border;
    }

    public boolean borderContainsLocation(GameBorder border, Location location) {
        if (border == null || location == null) {
            return false;
        }

        Location center = border.getCenter();
        if (center.getWorld() == null || location.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
            return false;
        }

        Vector radii = border.getDimensions().multiply(0.5);
        Vector offset = location.toVector().subtract(center.toVector());

        Vector normalized = new Vector(
            offset.getX() / radii.getX(),
            offset.getY() / radii.getY(),
            offset.getZ() / radii.getZ()
        );

        return border.getShape().metric(normalized) <= 1.0;
    }

    public boolean locationInAnyBorder(Location location) {
        for (GameBorder border : borders) {
            if (borderContainsLocation(border, location)) {
                return true;
            }
        }
        return false;
    }

    public void setBorderDamage(double amount, int interval) {
        ensureActive();

        if (amount < 0) {
            throw new IllegalArgumentException("Border damage cannot be negative.");
        }
        if (interval < 0) {
            throw new IllegalArgumentException("Border damage interval cannot be negative.");
        }

        damageAmount = amount;
        damageInterval = interval;

        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }

        if (amount == 0 || interval == 0) {
            return;
        }

        damageTask = context.getPlugin().getServer().getScheduler().runTaskTimer(
            context.getPlugin(),
            this::applyBorderDamage,
            interval,
            interval
        );
    }

    protected void deleteBorder(GameBorder border) {
        if (border == null) {
            return;
        }

        borders.remove(border);
        stopParticleTaskIfIdle();
    }

    GameTimer createTransitionTimer(int time, Consumer<Double> step, Runnable onEnd) {
        return timeManager.createTimer(time)
            .whileRunning(timer -> step.accept(timer.getElapsedTicks() / (double) time))
            .onEnd(timer -> onEnd.run())
            .start();
    }

    Location requireCenter(Location center) {
        if (center == null || center.getWorld() == null) {
            throw new IllegalArgumentException("Border center must have a world.");
        }

        return center.clone();
    }

    Vector requireDimensions(Vector dimensions) {
        if (dimensions == null) {
            throw new IllegalArgumentException("Border dimensions cannot be null.");
        }
        if (dimensions.getX() <= 0 || dimensions.getY() <= 0 || dimensions.getZ() <= 0) {
            throw new IllegalArgumentException("Border dimensions must be greater than zero on every axis.");
        }

        return dimensions.clone();
    }

    public void shutdown() {
        shutdown = true;

        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }

        borders.clear();
    }

    private void ensureActive() {
        if (shutdown || !context.getPlugin().isEnabled()) {
            throw new IllegalStateException("BorderManager is not active.");
        }
    }

    private void ensureParticleTask() {
        if (particleTask != null) {
            return;
        }

        particleTask = context.getPlugin().getServer().getScheduler().runTaskTimer(
            context.getPlugin(),
            this::renderParticles,
            PARTICLE_INTERVAL,
            PARTICLE_INTERVAL
        );
    }

    private void stopParticleTaskIfIdle() {
        if (particleTask == null || !borders.isEmpty()) {
            return;
        }

        particleTask.cancel();
        particleTask = null;
    }

    private void applyBorderDamage() {
        if (borders.isEmpty() || damageAmount <= 0 || damageInterval <= 0) {
            return;
        }

        GameWorld gameWorld = mapManager.currentWorld();
        if (gameWorld == null) {
            return;
        }

        World world = gameWorld.getBukkitWorld();

        for (Player player : playerManager.getNonSpectators()) {
            if (!world.equals(player.getWorld())) {
                continue;
            }
            if (!locationInAnyBorder(player.getLocation())) {
                player.damage(damageAmount);
            }
        }
    }

    private void renderParticles() {
        if (borders.isEmpty()) {
            stopParticleTaskIfIdle();
            return;
        }

        particleTicks++;

        for (GameBorder border : borders) {
            if (!border.isMoving() && particleTicks % STATIONARY_RENDER_MULTIPLIER != 0) {
                continue;
            }

            renderBorder(border);
        }
    }

    private void renderBorder(GameBorder border) {
        Location center = border.getCenter();
        World world = center.getWorld();
        GameWorld gameWorld = mapManager.currentWorld();

        if (world == null || gameWorld == null || !world.equals(gameWorld.getBukkitWorld())) {
            return;
        }

        Vector radii = border.getDimensions().multiply(0.5);

        boolean moving = border.isMoving();
        BorderParticle particle = moving ? movingParticle : stationaryParticle;

        double targetSpacing = moving ? MOVING_PARTICLE_SPACING : STATIONARY_PARTICLE_SPACING;
        int maxPoints = moving ? MAX_MOVING_PARTICLES_PER_BORDER : MAX_STATIONARY_PARTICLES_PER_BORDER;

        for (Location point : sampleSurface(center, radii, border.getShape(), targetSpacing, maxPoints)) {
            particle.spawn(world, point);
        }
    }

    private List<Location> sampleSurface(
        Location center,
        Vector radii,
        BorderShape shape,
        double targetSpacing,
        int maxPoints
    ) {
        double spacing = chooseSpacingForBudget(radii, targetSpacing, maxPoints);

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Location> points = new ArrayList<>();

        /*
         * Face sampling.
         *
         * This preserves your current look: particles are spread over the faces,
         * not just the outline.
         */
        for (int axis = 0; axis < 3; axis++) {
            int axisA = (axis + 1) % 3;
            int axisB = (axis + 2) % 3;

            int samplesA = samplesForAxis(radii, axisA, spacing, MAX_FACE_SAMPLES_PER_AXIS);
            int samplesB = samplesForAxis(radii, axisB, spacing, MAX_FACE_SAMPLES_PER_AXIS);

            for (int sign : new int[] {-1, 1}) {
                for (int u = 0; u <= samplesA; u++) {
                    for (int v = 0; v <= samplesB; v++) {
                        double a = lerp(-1.0, 1.0, u / (double) samplesA);
                        double b = lerp(-1.0, 1.0, v / (double) samplesB);

                        addSurfacePoint(
                            points,
                            seen,
                            center,
                            radii,
                            shape,
                            axisDirection(axis, sign, a, b)
                        );
                    }
                }
            }
        }

        /*
         * Edge sampling.
         *
         * This makes the bounds easier to read from far away because the silhouette
         * of the shape receives extra particles.
         */
        for (int varyingAxis = 0; varyingAxis < 3; varyingAxis++) {
            int fixedAxisA = (varyingAxis + 1) % 3;
            int fixedAxisB = (varyingAxis + 2) % 3;

            int samples = samplesForAxis(radii, varyingAxis, spacing, MAX_EDGE_SAMPLES_PER_AXIS);

            for (int signA : new int[] {-1, 1}) {
                for (int signB : new int[] {-1, 1}) {
                    for (int step = 0; step <= samples; step++) {
                        double varying = lerp(-1.0, 1.0, step / (double) samples);

                        Vector direction = new Vector();
                        setAxis(direction, varyingAxis, varying);
                        setAxis(direction, fixedAxisA, signA);
                        setAxis(direction, fixedAxisB, signB);

                        addSurfacePoint(points, seen, center, radii, shape, direction);
                    }
                }
            }
        }

        return points;
    }

    private double chooseSpacingForBudget(Vector radii, double targetSpacing, int maxPoints) {
        double spacing = Math.max(0.5, targetSpacing);

        /*
         * Increase spacing until the estimated number of samples is within budget.
         * This keeps large borders symmetric instead of simply cutting off particles
         * after maxPoints is reached.
         */
        while (estimatePointCount(radii, spacing) > maxPoints) {
            spacing *= 1.2;
        }

        return spacing;
    }

    private int estimatePointCount(Vector radii, double spacing) {
        int total = 0;

        for (int axis = 0; axis < 3; axis++) {
            int axisA = (axis + 1) % 3;
            int axisB = (axis + 2) % 3;

            int samplesA = samplesForAxis(radii, axisA, spacing, MAX_FACE_SAMPLES_PER_AXIS);
            int samplesB = samplesForAxis(radii, axisB, spacing, MAX_FACE_SAMPLES_PER_AXIS);

            total += 2 * (samplesA + 1) * (samplesB + 1);
        }

        for (int varyingAxis = 0; varyingAxis < 3; varyingAxis++) {
            int samples = samplesForAxis(radii, varyingAxis, spacing, MAX_EDGE_SAMPLES_PER_AXIS);
            total += 4 * (samples + 1);
        }

        return total;
    }

    private int samplesForAxis(Vector radii, int axis, double spacing, int maxSamples) {
        double diameter = getAxis(radii, axis) * 2.0;
        int samples = (int) Math.ceil(diameter / spacing);

        return Math.max(1, Math.min(maxSamples, samples));
    }

    private void addSurfacePoint(
        List<Location> points,
        Set<String> seen,
        Location center,
        Vector radii,
        BorderShape shape,
        Vector direction
    ) {
        double metric = shape.metric(direction);
        if (metric <= 0) {
            return;
        }

        Vector normalizedSurface = direction.clone().multiply(1.0 / metric);

        Vector localOffset = new Vector(
            normalizedSurface.getX() * radii.getX(),
            normalizedSurface.getY() * radii.getY(),
            normalizedSurface.getZ() * radii.getZ()
        );

        Location point = center.clone().add(localOffset);
        point.add(0.0, SURFACE_HEIGHT_OFFSET, 0.0);

        String key = quantize(point.getX()) + ":" + quantize(point.getY()) + ":" + quantize(point.getZ());

        if (seen.add(key)) {
            points.add(point);
        }
    }

    private Vector axisDirection(int axis, int sign, double a, double b) {
        return switch (axis) {
            case 0 -> new Vector(sign, a, b);
            case 1 -> new Vector(a, sign, b);
            default -> new Vector(a, b, sign);
        };
    }

    private double getAxis(Vector vector, int axis) {
        return switch (axis) {
            case 0 -> vector.getX();
            case 1 -> vector.getY();
            default -> vector.getZ();
        };
    }

    private void setAxis(Vector vector, int axis, double value) {
        switch (axis) {
            case 0 -> vector.setX(value);
            case 1 -> vector.setY(value);
            default -> vector.setZ(value);
        }
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static String quantize(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private static double square(double value) {
        return value * value;
    }

    @FunctionalInterface
    private interface ShapeMetric {
        double measure(Vector vector);
    }

    public static final class BorderParticle {
        private final Particle particle;
        private final Object data;
        private final int count;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final double extra;

        private BorderParticle(
            Particle particle,
            Object data,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra
        ) {
            this.particle = Objects.requireNonNull(particle, "particle");
            this.data = data;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.extra = extra;
        }

        public static BorderParticle of(Particle particle) {
            return new BorderParticle(particle, null, 1, 0.0, 0.0, 0.0, 0.0);
        }

        public static BorderParticle of(Particle particle, Object data) {
            return new BorderParticle(particle, data, 1, 0.0, 0.0, 0.0, 0.0);
        }

        public static BorderParticle of(
            Particle particle,
            Object data,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra
        ) {
            return new BorderParticle(particle, data, count, offsetX, offsetY, offsetZ, extra);
        }

        public static BorderParticle dust(Color color, float size) {
            return of(Particle.DUST, new Particle.DustOptions(color, size));
        }

        public void spawn(World world, Location location) {
            if (data == null) {
                world.spawnParticle(
                    particle,
                    location,
                    count,
                    offsetX,
                    offsetY,
                    offsetZ,
                    extra
                );
            } else {
                world.spawnParticle(
                    particle,
                    location,
                    count,
                    offsetX,
                    offsetY,
                    offsetZ,
                    extra,
                    data
                );
            }
        }
    }
}
package com.donutsforlife11.donutgame.api.border;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.util.Vector;

final class BorderParticleSampler {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final int SURFACE_SEARCH_STEPS = 10;

    private BorderParticleSampler() {
    }

    static List<Vector> sample(GameBorder border, double spacing, int maximumParticles) {
        if (border.shape() == BorderManager.BorderShape.CUBOID) {
            return sampleCuboid(border.dimensions(), spacing, maximumParticles);
        }
        return sampleCurved(border, spacing, maximumParticles);
    }

    private static List<Vector> sampleCuboid(Vector dimensions, double spacing, int maximumParticles) {
        List<Vector> points = new ArrayList<>(maximumParticles);
        Set<GridCell> occupied = new HashSet<>();
        double halfX = dimensions.getX() / 2.0;
        double halfY = dimensions.getY() / 2.0;
        double halfZ = dimensions.getZ() / 2.0;
        int perFace = Math.max(1, maximumParticles / 6);

        addFace(points, occupied, spacing, perFace, -halfX, -halfY, -halfZ, -halfX, halfY, halfZ);
        addFace(points, occupied, spacing, perFace, halfX, -halfY, -halfZ, halfX, halfY, halfZ);
        addFace(points, occupied, spacing, perFace, -halfX, -halfY, -halfZ, halfX, -halfY, halfZ);
        addFace(points, occupied, spacing, perFace, -halfX, halfY, -halfZ, halfX, halfY, halfZ);
        addFace(points, occupied, spacing, perFace, -halfX, -halfY, -halfZ, halfX, halfY, -halfZ);
        addFace(points, occupied, spacing, perFace, -halfX, -halfY, halfZ, halfX, halfY, halfZ);
        return points;
    }

    private static void addFace(
        List<Vector> points,
        Set<GridCell> occupied,
        double spacing,
        int maximumParticles,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        double widthA = minX == maxX ? maxY - minY : maxX - minX;
        double widthB = minZ == maxZ ? maxY - minY : maxZ - minZ;
        double faceSpacing = Math.max(spacing, Math.sqrt(Math.max(1.0, widthA * widthB) / maximumParticles));
        int added = 0;

        for (double x = minX; x <= maxX + 1.0e-6; x += axisStep(minX, maxX, faceSpacing)) {
            for (double y = minY; y <= maxY + 1.0e-6; y += axisStep(minY, maxY, faceSpacing)) {
                for (double z = minZ; z <= maxZ + 1.0e-6; z += axisStep(minZ, maxZ, faceSpacing)) {
                    Vector point = new Vector(clamp(x, minX, maxX), clamp(y, minY, maxY), clamp(z, minZ, maxZ));
                    if (!occupied.add(GridCell.from(point, faceSpacing))) {
                        continue;
                    }
                    points.add(point);
                    if (++added >= maximumParticles) {
                        return;
                    }
                }
            }
        }
    }

    private static List<Vector> sampleCurved(GameBorder border, double spacing, int maximumParticles) {
        Vector dimensions = border.dimensions();
        int targetCount = estimateParticleCount(dimensions, spacing, maximumParticles);
        int candidateCount = Math.max(targetCount * 2, 24);
        double maximumDistance = dimensions.clone().multiply(0.5).length() * 1.05;
        List<Vector> points = new ArrayList<>(targetCount);
        Set<GridCell> occupied = new HashSet<>();

        for (int index = 0; index < candidateCount && points.size() < targetCount; index++) {
            Vector direction = fibonacciDirection(index, candidateCount);
            Vector point = findSurface(border, direction, maximumDistance);
            if (!occupied.add(GridCell.from(point, spacing))) {
                continue;
            }
            points.add(point);
        }
        return points;
    }

    private static Vector fibonacciDirection(int index, int count) {
        double y = 1.0 - 2.0 * ((index + 0.5) / count);
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double angle = GOLDEN_ANGLE * index;
        return new Vector(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
    }

    private static Vector findSurface(GameBorder border, Vector direction, double maximumDistance) {
        double insideDistance = 0.0;
        double outsideDistance = maximumDistance;
        for (int step = 0; step < SURFACE_SEARCH_STEPS; step++) {
            double distance = (insideDistance + outsideDistance) / 2.0;
            Vector point = direction.clone().multiply(distance);
            if (border.containsLocation(point.getX(), point.getY(), point.getZ())) {
                insideDistance = distance;
            } else {
                outsideDistance = distance;
            }
        }
        return direction.clone().multiply(insideDistance);
    }

    private static int estimateParticleCount(Vector dimensions, double spacing, int maximumParticles) {
        double sizeX = dimensions.getX();
        double sizeY = dimensions.getY();
        double sizeZ = dimensions.getZ();
        double estimatedArea = 2.0 * (sizeX * sizeY + sizeX * sizeZ + sizeY * sizeZ);
        int count = (int) Math.ceil(estimatedArea / (spacing * spacing));
        return Math.clamp(count, 12, maximumParticles);
    }

    private static double axisStep(double min, double max, double spacing) {
        return min == max ? Double.POSITIVE_INFINITY : Math.max(0.25, spacing);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record GridCell(long x, long y, long z) {
        static GridCell from(Vector point, double spacing) {
            return new GridCell(
                Math.round(point.getX() / spacing),
                Math.round(point.getY() / spacing),
                Math.round(point.getZ() / spacing)
            );
        }
    }
}

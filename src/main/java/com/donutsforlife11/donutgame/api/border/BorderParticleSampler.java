package com.donutsforlife11.donutgame.api.border;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.util.Vector;

public class BorderParticleSampler {
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static int BINARY_SEARCH_STEPS = 10;

    static List<Vector> sample(GameBorder border, double spacing, int maxParticles) {
        Vector dimensions = border.dimensions();

        double sizeX = dimensions.getX();
        double sizeY = dimensions.getY();
        double sizeZ = dimensions.getZ();

        if (sizeX < 0.0 || sizeY < 0.0 || sizeZ < 0.0) {
            return List.of();
        }

        int targetCount = estimateParticleCount(sizeX, sizeY, sizeZ, spacing, maxParticles);
        int candidateCount = targetCount * 2;
        List<Vector> points = new ArrayList<>(targetCount);
        Set<GridCell> occupiedCells = new HashSet<>();

        double halfX = sizeX / 2;
        double halfY = sizeY / 2;
        double halfZ = sizeZ / 2;

        double maxDistance = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ) * 1.05;
        for (int i = 0; i < candidateCount; i++) {
            Vector direction = fibonacciDirection(i, candidateCount);
            Vector surfacePoint = findSurface(border, direction, maxDistance);
            GridCell cell = GridCell.from(surfacePoint, spacing);
            if (!occupiedCells.add(cell)) {
                continue;
            }
            points.add(surfacePoint);
            if (points.size() >= targetCount) {
                break;
            }
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
        for (int i = 0; i < BINARY_SEARCH_STEPS; i++) {
            double middleDistance = (insideDistance + outsideDistance) / 2.0;
            double x = direction.getX() * middleDistance;
            double y = direction.getY() * middleDistance;
            double z = direction.getZ() * middleDistance;
            if (border.containsLocation(x, y, z)) {
                insideDistance = middleDistance;
            } else {
                outsideDistance = middleDistance;
            }
        }
        return direction.clone().multiply(insideDistance);
    }

    private static int estimateParticleCount(double sizeX, double sizeY, double sizeZ, double spacing, int maximumParticles) {
        double estimatedArea = 2.0 * (sizeX * sizeY + sizeX * sizeZ + sizeY * sizeZ);
        int count = (int) Math.ceil(
                estimatedArea / (spacing * spacing)
        );
        return Math.clamp(count, 12, maximumParticles);
    }

    private record GridCell(long x, long y, long z) {
        static GridCell from(Vector point, double spacing) {
            return new GridCell(Math.round(point.getX() / spacing), Math.round(point.getY() / spacing), Math.round(point.getZ() / spacing));
        }
    }
}

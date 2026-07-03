package com.donutsforlife11.donutgame.api.map;

public enum MapRotation {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270);

    private final int degrees;

    MapRotation(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    public static MapRotation fromDegrees(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 0 -> DEG_0;
            case 90 -> DEG_90;
            case 180 -> DEG_180;
            case 270 -> DEG_270;
            default -> throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees.");
        };
    }
}

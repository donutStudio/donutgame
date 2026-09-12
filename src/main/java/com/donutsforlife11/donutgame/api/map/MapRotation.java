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
        int clamped = Math.floorMod((int) Math.round(degrees / 90.0) * 90, 360);
        return switch (clamped) {
            case 0 -> DEG_0;
            case 90 -> DEG_90;
            case 180 -> DEG_180;
            case 270 -> DEG_270;
            default -> DEG_0;
        };
    }
}

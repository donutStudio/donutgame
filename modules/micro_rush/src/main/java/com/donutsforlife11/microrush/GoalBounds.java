package com.donutsforlife11.microrush;

public final class GoalBounds {
    private GoalBounds() {}
    public static boolean near(double x,double y,double z,double minX,double minY,double minZ,double maxX,double maxY,double maxZ,double radius) {
        double dx=x-Math.clamp(x,minX,maxX),dy=y-Math.clamp(y,minY,maxY),dz=z-Math.clamp(z,minZ,maxZ);
        return dx*dx+dy*dy+dz*dz<=radius*radius;
    }
}

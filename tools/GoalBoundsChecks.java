import com.donutsforlife11.microrush.GoalBounds;
class GoalBoundsChecks {
    public static void main(String[] args) {
        if(!GoalBounds.near(0.6,2,10,0,1,6,5,6,11,1.5)) throw new AssertionError("Court goal must cover the exported four-block volume");
        if(!GoalBounds.near(5.7,2,9,0,1,6,5,6,11,1)) throw new AssertionError("Goal proximity must register before wall reflection");
        if(GoalBounds.near(10,2,9,0,1,6,5,6,11,1)) throw new AssertionError("Midcourt must not score");
        if(GoalBounds.near(2,10,9,0,1,6,5,6,11,1)) throw new AssertionError("Out-of-height balls must not score");
        System.out.println("Passed goal volume, wall proximity, midcourt and height checks.");
    }
}

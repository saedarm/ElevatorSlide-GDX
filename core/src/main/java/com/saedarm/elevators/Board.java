package com.saedarm.elevators;

/**
 * The rules, with no rendering. Ten squares per floor, so square n sits directly
 * above square n - 10. That is why every elevator and chute jumps by a multiple of 10.
 */
public final class Board {
    public static final int ROOF = 100;

    public static final int[][] ELEVATORS = {
            {3, 23}, {8, 38}, {14, 44}, {27, 57}, {41, 71}, {52, 72}, {66, 96}, {79, 89}};
    public static final int[][] CHUTES = {
            {19, 9}, {34, 4}, {48, 18}, {63, 33}, {75, 45}, {87, 47}, {93, 73}, {99, 69}};

    public enum Kind { NONE, ELEVATOR, CHUTE }

    /** Both paces average 4.5 squares a turn; they differ only in spread. */
    public enum Pace {
        STAIRS(3, 6, "d4+2"), HUSTLE(1, 8, "d8");
        public final int min, max;
        public final String dice;
        Pace(int min, int max, String dice) { this.min = min; this.max = max; this.dice = dice; }
        public int roll() { return com.badlogic.gdx.math.MathUtils.random(min, max); }
    }

    private static final Kind[] KIND = new Kind[ROOF + 1];
    private static final int[] TARGET = new int[ROOF + 1];

    static {
        for (int i = 0; i <= ROOF; i++) { KIND[i] = Kind.NONE; TARGET[i] = i; }
        for (int[] e : ELEVATORS) { KIND[e[0]] = Kind.ELEVATOR; TARGET[e[0]] = e[1]; }
        for (int[] c : CHUTES) { KIND[c[0]] = Kind.CHUTE; TARGET[c[0]] = c[1]; }
    }

    private Board() {}

    public static Kind kindAt(int sq) { return KIND[sq]; }

    /** Where an elevator or chute on this square takes you; the square itself if neither. */
    public static int resolve(int sq) { return TARGET[sq]; }

    /** Square you land on after walking. Overshooting the roof bounces back by the extra. */
    public static int land(int sq, int roll) {
        int t = sq + roll;
        return t > ROOF ? 2 * ROOF - t : t;
    }

    public static int floorOf(int sq) { return sq <= 0 ? 0 : (sq + 9) / 10; }

    public static String placeName(int sq) {
        if (sq == 0) return "Lobby";
        if (sq == ROOF) return "Roof";
        return "Floor " + floorOf(sq);
    }

    /** Chance of hitting an elevator or chute this turn, plus the average square you end on. */
    public static final class Odds {
        public final float elevator, chute, expected;
        Odds(float elevator, float chute, float expected) {
            this.elevator = elevator; this.chute = chute; this.expected = expected;
        }
    }

    public static Odds odds(int sq, Pace pace) {
        int up = 0, down = 0, n = 0;
        float total = 0;
        for (int r = pace.min; r <= pace.max; r++, n++) {
            int landed = land(sq, r);
            Kind k = kindAt(landed);
            if (k == Kind.ELEVATOR) up++;
            if (k == Kind.CHUTE) down++;
            int end = resolve(landed);
            total += end == ROOF ? 130 : end; // winning outright is worth more than "square 100"
        }
        return new Odds(up / (float) n, down / (float) n, total / n);
    }

    /** CPU tenants pick whichever pace has the better average outcome; ties go to the stairs. */
    public static Pace cpuChoice(int sq) {
        return odds(sq, Pace.HUSTLE).expected > odds(sq, Pace.STAIRS).expected + 0.25f ? Pace.HUSTLE : Pace.STAIRS;
    }
}

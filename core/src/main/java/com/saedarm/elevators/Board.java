package com.saedarm.elevators;

/**
 * The fixed board, with no rendering. Ten squares per floor, so square n sits directly
 * above square n - 10. That is why every elevator and chute jumps by a multiple of 10.
 */
public final class Board {
    public static final int ROOF = 100;

    public static final int[][] ELEVATORS = {
            {3, 23}, {8, 38}, {14, 44}, {27, 57}, {41, 71}, {52, 72}, {66, 96}, {79, 89}};
    public static final int[][] CHUTES = {
            {19, 9}, {34, 4}, {48, 18}, {63, 33}, {75, 45}, {87, 47}, {93, 73}, {99, 69}};
    /** Two mailboxes per floor. Land on one to draw a card. */
    public static final int[] MAILBOXES = {6, 10, 12, 16, 25, 29, 31, 36, 43, 50, 55, 59, 65, 68, 77, 80, 82, 85, 92, 97};

    public enum Kind { NONE, ELEVATOR, CHUTE }

    /** Both paces average 4.5 squares a turn; they differ only in spread. Wet Floor widens the stairs. */
    public enum Pace { STAIRS, HUSTLE }

    private static final Kind[] KIND = new Kind[ROOF + 1];
    private static final int[] TARGET = new int[ROOF + 1];
    private static final boolean[] MAIL = new boolean[ROOF + 1];

    static {
        for (int i = 0; i <= ROOF; i++) { KIND[i] = Kind.NONE; TARGET[i] = i; }
        for (int[] e : ELEVATORS) { KIND[e[0]] = Kind.ELEVATOR; TARGET[e[0]] = e[1]; }
        for (int[] c : CHUTES) { KIND[c[0]] = Kind.CHUTE; TARGET[c[0]] = c[1]; }
        for (int m : MAILBOXES) MAIL[m] = true;
    }

    private Board() {}

    public static Kind kindAt(int sq) { return KIND[sq]; }

    /** Where an elevator or chute on this square normally takes you; the square itself if neither. */
    public static int resolve(int sq) { return TARGET[sq]; }

    public static boolean isMailbox(int sq) { return MAIL[sq]; }

    public static boolean isElevatorTop(int sq) {
        for (int[] e : ELEVATORS) if (e[1] == sq) return true;
        return false;
    }

    public static int elevatorBottomFor(int top) {
        for (int[] e : ELEVATORS) if (e[1] == top) return e[0];
        return top;
    }

    public static int chuteIndex(int start) {
        for (int i = 0; i < CHUTES.length; i++) if (CHUTES[i][0] == start) return i;
        return -1;
    }

    /** Square you land on after walking. Overshooting the roof bounces back by the extra. */
    public static int land(int sq, int roll) {
        int t = sq + roll;
        return t > ROOF ? 2 * ROOF - t : t;
    }

    public static int floorOf(int sq) { return sq <= 0 ? 0 : (sq + 9) / 10; }

    /** 0..9 position around the tower; squares in the same column are stacked vertically. */
    public static int columnOf(int sq) { return (sq - 1 + 10) % 10; }

    public static String placeName(int sq) {
        if (sq == 0) return "Lobby";
        if (sq == ROOF) return "Roof";
        return "Floor " + floorOf(sq);
    }
}

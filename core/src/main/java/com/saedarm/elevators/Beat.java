package com.saedarm.elevators;

/**
 * One thing for the screen to show. The Engine decides what happens and records beats;
 * Match plays them back as animations, in order. That split is what lets the simulator
 * play thousands of games without drawing anything.
 */
public final class Beat {
    public enum Type {
        BANNER,          // text
        CARD,            // a: player, b: target or -1, card
        EVENT,           // event
        DRAW,            // a: player, card, text
        WALK,            // a: player, path
        FREIGHT,         // a: player, b: to
        ELEVATOR,        // a: player, b: from, c: normal top, d: final (boosted) top
        CHUTE,           // a: player, b: chute index, c: to, d: 1 if halfway
        CHUTE_UP,        // a: player, b: chute index, c: to
        GRIPPY,          // a: player, b: chute index
        DROP,            // a: player, b: to, c: reason (DROP_*)
        EXTINGUISH,      // a: player
        SWAP,            // a, b: players
        SHUSH,           // a: target
        STEAL,           // a: thief, b: victim
        BOMB,            // a: square, b: 1 if it exploded, moves: who fell
        FIRE_ON,         // a: floor
        FIRE_OUT,        // a: floor
        HOLE_CLOSE,      // a: square
        PIANO,           // a: column, moves
        CABLE_SNAP,      // moves
        CABLE_FIXED,
        WATER,           // moves
        POPCORN,         // a: player who popped it, moves
        RACCOONS_IN,     // a: chute index
        RACCOONS_OUT,    // a: chute index
        UFO,             // a: player, b: to
        QUAKE,           // moves
        WARP,            // moves
        WIFI,            // moves (player, -1)
        SKIP,            // a: player
        FIRE_DRILL,      // moves
        POWER_OFF, POWER_ON,
        EXPRESS_ON, EXPRESS_OFF,
        WET_ON, WET_OFF,
        PILEUP_ON,       // a: chute index
        PILEUP_OFF       // a: chute index
    }

    public static final int DROP_HOLE = 0, DROP_FIRE = 1;

    public final Type type;
    public int a = -1, b = -1, c = -1, d = -1;
    public int[] path;
    /** {player, newSquare}; newSquare is -1 when a Fire Extinguisher blocked it. */
    public int[][] moves;
    public Card card;
    public Engine.Event event;
    public String text;

    Beat(Type type) { this.type = type; }
}

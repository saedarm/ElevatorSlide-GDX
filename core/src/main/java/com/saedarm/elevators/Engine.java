package com.saedarm.elevators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The whole game as plain logic: positions, hands, timed effects, events and card effects.
 * Every change it makes is also recorded as a {@link Beat} so the screen can animate it.
 * Nothing here touches libGDX, so the simulator runs it headless.
 */
public final class Engine {

    public enum Event {
        FIRE_DRILL("Fire Drill", "Everyone on the top two floors goes down one floor."),
        POWER_OUTAGE("Power Outage", "Elevators are out for one round."),
        EXPRESS_HOUR("Express Hour", "Every elevator goes one extra floor this round."),
        WET_FLOOR("Wet Floor", "The stairs are slippery. Stairs moves 2–7 this round."),
        LAUNDRY_PILEUP("Laundry Pileup", "One chute is clogged. Falling in only drops you halfway."),
        RENT_DAY("Rent Day", "The tenant lowest in the building gets a free card.");

        public final String title, text;
        Event(String title, String text) { this.title = title; this.text = text; }
    }

    public static final class Tenant {
        public final int id;
        public final String name;
        public final boolean cpu;
        public int sq;
        public final List<Card> hand = new ArrayList<>();
        public boolean doormanTip, stairsOnly, offline;

        public Tenant(int id, String name, boolean cpu) { this.id = id; this.name = name; this.cpu = cpu; }

        public boolean has(Card c) { return hand.contains(c); }

        Tenant copy() {
            Tenant t = new Tenant(id, name, cpu);
            t.sq = sq;
            t.hand.addAll(hand);
            t.doormanTip = doormanTip; t.stairsOnly = stairsOnly; t.offline = offline;
            return t;
        }
    }

    public static final class Odds {
        public final float up, down, expected;
        Odds(float up, float down, float expected) { this.up = up; this.down = down; this.expected = expected; }
    }

    public static final int HAND_MAX = 3, EVENT_EVERY = 5;

    public final List<Tenant> tenants = new ArrayList<>();
    public final Random rng;
    public int turn, turnCount;
    public boolean cardPlayed, movedByCard, skipping;
    public Tenant winner;

    /** Effects are active while turnCount < their "until" value. 0 means off. */
    public int powerUntil, expressUntil, wetUntil, cableUntil, pileupUntil, raccoonUntil;
    public int pileupChute = -1, raccoonChute = -1;
    public final int[] holeUntil = new int[Board.ROOF + 1];
    public final int[] fireUntil = new int[11];
    private final List<int[]> history = new ArrayList<>();
    private List<Beat> out = new ArrayList<>();

    public Engine(long seed) { rng = new Random(seed); }

    public Tenant add(String name, boolean cpu) {
        Tenant t = new Tenant(tenants.size(), name, cpu);
        tenants.add(t);
        return t;
    }

    public Tenant current() { return tenants.get(turn); }

    private int n() { return tenants.size(); }

    private int rounds(int r) { return turnCount + r * n(); }

    private boolean on(int until) { return turnCount < until; }

    public int roundsLeft(int until) { return Math.max(0, (until - turnCount + n() - 1) / n()); }

    public boolean powerOut() { return on(powerUntil); }
    public boolean cableBroken() { return on(cableUntil); }
    public boolean elevatorsDown() { return powerOut() || cableBroken(); }
    public boolean express() { return on(expressUntil); }
    public boolean wet() { return on(wetUntil); }
    public boolean pileup() { return on(pileupUntil) && pileupChute >= 0; }
    public boolean raccoons() { return on(raccoonUntil) && raccoonChute >= 0; }
    public boolean hole(int sq) { return sq > 0 && sq < Board.ROOF && on(holeUntil[sq]); }
    public boolean fire(int floor) { return floor > 0 && floor <= 10 && on(fireUntil[floor]); }

    // ------------------------------------------------------------------ beats

    private Beat beat(Beat.Type type) { Beat b = new Beat(type); out.add(b); return b; }

    private void say(String text) { beat(Beat.Type.BANNER).text = text; }

    private static int[][] moves(List<int[]> list) { return list.toArray(new int[0][]); }

    // ------------------------------------------------------------------ turn flow

    /** Expire old effects, run a building event at the top of every fifth round, then check for a skipped turn. */
    public List<Beat> beginTurn() {
        out = new ArrayList<>();
        expire();
        if (turn == 0 && turnCount > 0 && (turnCount / n()) % EVENT_EVERY == 0) {
            Event[] all = Event.values();
            runEvent(all[rng.nextInt(all.length)]);
        }
        int[] snap = new int[n()];
        for (int i = 0; i < n(); i++) snap[i] = tenants.get(i).sq;
        history.add(snap);
        if (history.size() > n() + 2) history.remove(0);

        Tenant t = current();
        skipping = t.offline;
        if (skipping) {
            t.offline = false;
            beat(Beat.Type.SKIP).a = t.id;
            say(t.name + " is still resetting the router. Turn skipped.");
        }
        return out;
    }

    public void endTurn() {
        Tenant t = current();
        t.stairsOnly = false;
        cardPlayed = false;
        movedByCard = false;
        turnCount++;
        turn = (turn + 1) % n();
    }

    private void expire() {
        if (powerUntil != 0 && !on(powerUntil)) { powerUntil = 0; beat(Beat.Type.POWER_ON); say("The power is back on."); }
        if (cableUntil != 0 && !on(cableUntil)) { cableUntil = 0; beat(Beat.Type.CABLE_FIXED); say("Maintenance fixed the elevator cables."); }
        if (expressUntil != 0 && !on(expressUntil)) { expressUntil = 0; beat(Beat.Type.EXPRESS_OFF); }
        if (wetUntil != 0 && !on(wetUntil)) { wetUntil = 0; beat(Beat.Type.WET_OFF); }
        if (pileupUntil != 0 && !on(pileupUntil)) { pileupUntil = 0; beat(Beat.Type.PILEUP_OFF).a = pileupChute; pileupChute = -1; }
        if (raccoonUntil != 0 && !on(raccoonUntil)) {
            raccoonUntil = 0;
            beat(Beat.Type.RACCOONS_OUT).a = raccoonChute;
            say("The raccoons moved out of chute " + Board.CHUTES[raccoonChute][0] + ".");
            raccoonChute = -1;
        }
        for (int s = 1; s < Board.ROOF; s++) {
            if (holeUntil[s] != 0 && !on(holeUntil[s])) { holeUntil[s] = 0; beat(Beat.Type.HOLE_CLOSE).a = s; }
        }
        for (int f = 1; f <= 10; f++) {
            if (fireUntil[f] != 0 && !on(fireUntil[f])) { fireUntil[f] = 0; beat(Beat.Type.FIRE_OUT).a = f; say("The fire on floor " + f + " is out."); }
        }
    }

    private void runEvent(Event e) {
        beat(Beat.Type.EVENT).event = e;
        switch (e) {
            case FIRE_DRILL: {
                List<int[]> m = new ArrayList<>();
                for (Tenant t : tenants) if (t.sq > 80 && t.sq < Board.ROOF) { t.sq -= 10; m.add(new int[]{t.id, t.sq}); }
                beat(Beat.Type.FIRE_DRILL).moves = moves(m);
                break;
            }
            case POWER_OUTAGE: powerUntil = rounds(1); beat(Beat.Type.POWER_OFF); break;
            case EXPRESS_HOUR: expressUntil = rounds(1); beat(Beat.Type.EXPRESS_ON); break;
            case WET_FLOOR: wetUntil = rounds(1); beat(Beat.Type.WET_ON); break;
            case LAUNDRY_PILEUP:
                if (pileupChute >= 0) beat(Beat.Type.PILEUP_OFF).a = pileupChute;
                pileupChute = rng.nextInt(Board.CHUTES.length);
                pileupUntil = rounds(1);
                beat(Beat.Type.PILEUP_ON).a = pileupChute;
                say("Chute " + Board.CHUTES[pileupChute][0] + " is clogged with laundry.");
                break;
            case RENT_DAY: {
                Tenant low = null;
                for (Tenant t : tenants) if (low == null || t.sq < low.sq) low = t;
                draw(low, "Rent Day: " + low.name + " is lowest and gets a free card");
                break;
            }
        }
    }

    private void draw(Tenant t, String why) {
        if (t.hand.size() >= HAND_MAX) { say(why + ", but their hands are full."); return; }
        Card c = Card.draw(rng);
        t.hand.add(c);
        Beat b = beat(Beat.Type.DRAW);
        b.a = t.id;
        b.card = c;
        b.text = why;
    }

    // ------------------------------------------------------------------ rolling and moving

    public boolean canHustle() { return !current().stairsOnly; }

    public int paceMin(Board.Pace p) { return p == Board.Pace.STAIRS ? (wet() ? 2 : 3) : 1; }

    public int paceMax(Board.Pace p) { return p == Board.Pace.STAIRS ? (wet() ? 7 : 6) : 8; }

    public int roll(Board.Pace p) { return paceMin(p) + rng.nextInt(paceMax(p) - paceMin(p) + 1); }

    /** With a Spare Key you may move roll-1, roll or roll+1; otherwise just the roll. */
    public int[] stepOptions(Tenant t, int roll) {
        if (!t.has(Card.SPARE_KEY)) return new int[]{roll};
        return roll > 1 ? new int[]{roll - 1, roll, roll + 1} : new int[]{roll, roll + 1};
    }

    /** The option that ends highest; ties keep the roll so the key isn't wasted. */
    public int bestSteps(Tenant t, int roll) {
        int best = roll;
        float bestV = value(preview(t, roll));
        for (int s : stepOptions(t, roll)) {
            float v = value(preview(t, s));
            if (v > bestV) { bestV = v; best = s; }
        }
        return best;
    }

    private static float value(int sq) { return sq == Board.ROOF ? 130 : sq; }

    /** Walk the steps and resolve the landing. usedKey spends the Spare Key. */
    public List<Beat> move(int steps, boolean usedKey) {
        out = new ArrayList<>();
        Tenant t = current();
        if (usedKey) {
            t.hand.remove(Card.SPARE_KEY);
            say(t.name + " used the Spare Key to move " + steps + ".");
        }
        walk(t, steps);
        afterLanding(t);
        return out;
    }

    private void walk(Tenant t, int steps) {
        int target = t.sq + steps;
        List<Integer> path = new ArrayList<>();
        for (int s = t.sq + 1; s <= Math.min(target, Board.ROOF); s++) path.add(s);
        if (target > Board.ROOF) for (int s = Board.ROOF - 1; s >= 2 * Board.ROOF - target; s--) path.add(s);
        Beat w = beat(Beat.Type.WALK);
        w.a = t.id;
        w.path = new int[path.size()];
        for (int i = 0; i < path.size(); i++) w.path[i] = path.get(i);
        if (target > Board.ROOF) say(t.name + " overshot the roof by " + (target - Board.ROOF) + " and bounced back.");
        resolve(t, Board.land(t.sq, steps), true);
    }

    /** Fire on the floor, then the mailbox, then the roof. */
    private void afterLanding(Tenant t) {
        int f = Board.floorOf(t.sq);
        if (t.sq > 0 && t.sq < Board.ROOF && fire(f)) {
            if (t.has(Card.FIRE_EXTINGUISHER)) {
                t.hand.remove(Card.FIRE_EXTINGUISHER);
                beat(Beat.Type.EXTINGUISH).a = t.id;
                say(t.name + " put out the fire under their feet with the extinguisher.");
            } else {
                int to = Math.max(0, t.sq - 10);
                Beat d = beat(Beat.Type.DROP);
                d.a = t.id; d.b = to; d.c = Beat.DROP_FIRE;
                say(t.name + " ended the turn on a burning floor and jumped down to " + (to == 0 ? "the lobby" : String.valueOf(to)) + ".");
                t.sq = to;
            }
        }
        if (Board.isMailbox(t.sq)) draw(t, t.name + " checked the mailbox");
        if (t.sq == Board.ROOF) winner = t;
    }

    /**
     * What happens when t lands on s. With commit=false nothing changes and no beats are written,
     * which is how the odds on the buttons are computed.
     */
    private int resolve(Tenant t, int s, boolean commit) {
        if (hole(s)) {
            int to = Math.max(0, s - 10);
            if (commit) {
                Beat d = beat(Beat.Type.DROP);
                d.a = t.id; d.b = to; d.c = Beat.DROP_HOLE;
                say(t.name + " stepped into the bomb hole at " + s + ".");
                t.sq = to;
            }
            return to;
        }
        Board.Kind k = Board.kindAt(s);
        if (k == Board.Kind.ELEVATOR) {
            if (elevatorsDown()) {
                if (commit) { t.sq = s; say("The elevator at " + s + " is out of service."); }
                return s;
            }
            int top = Board.resolve(s);
            boolean boost = express() || t.doormanTip;
            int fin = boost ? Math.min(98, top + 10) : top;
            if (commit) {
                boolean tip = t.doormanTip;
                t.doormanTip = false;
                Beat e = beat(Beat.Type.ELEVATOR);
                e.a = t.id; e.b = s; e.c = top; e.d = fin;
                say(t.name + " caught the elevator: " + s + " up to " + fin + (fin != top ? (tip ? " (the doorman's tip paid off)" : " (Express Hour)") : "") + "!");
                t.sq = fin;
            }
            return fin;
        }
        if (k == Board.Kind.CHUTE) {
            int ci = Board.chuteIndex(s);
            if (raccoons() && raccoonChute == ci) {
                if (commit) { t.sq = s; say("Raccoons are living in chute " + s + ". It's too full to fall into."); }
                return s;
            }
            if (t.has(Card.GRIPPY_SOCKS)) {
                if (commit) {
                    t.hand.remove(Card.GRIPPY_SOCKS);
                    Beat g = beat(Beat.Type.GRIPPY);
                    g.a = t.id; g.b = ci;
                    say(t.name + " caught the edge of the chute in Grippy Socks!");
                    t.sq = s;
                }
                return s;
            }
            int floors = (s - Board.resolve(s)) / 10;
            boolean pile = pileup() && pileupChute == ci && floors >= 2;
            boolean bag = !pile && floors >= 2 && t.has(Card.LAUNDRY_BAG);
            int to = (pile || bag) ? s - 10 * (floors / 2) : Board.resolve(s);
            if (commit) {
                if (bag) t.hand.remove(Card.LAUNDRY_BAG);
                Beat c = beat(Beat.Type.CHUTE);
                c.a = t.id; c.b = ci; c.c = to; c.d = (pile || bag) ? 1 : 0;
                say(t.name + " fell down the laundry chute: " + s + " down to " + to
                        + (bag ? " (the Laundry Bag broke the fall)" : pile ? " (landed on the laundry pile)" : "") + ".");
                t.sq = to;
                draw(t, t.name + " found a card in the laundry");
            }
            return to;
        }
        if (raccoons() && s == Board.CHUTES[raccoonChute][1]) {
            int to = Board.CHUTES[raccoonChute][0];
            if (commit) {
                Beat c = beat(Beat.Type.CHUTE_UP);
                c.a = t.id; c.b = raccoonChute; c.c = to;
                say("The raccoons flung " + t.name + " up the chute to " + to + "!");
                t.sq = to;
            }
            return to;
        }
        if (commit) t.sq = s;
        return s;
    }

    /** Where t would end the turn after moving the given steps, including a burning floor. */
    public int preview(Tenant t, int steps) {
        int end = resolve(t, Board.land(t.sq, steps), false);
        if (end > 0 && end < Board.ROOF && fire(Board.floorOf(end)) && !t.has(Card.FIRE_EXTINGUISHER)) end = Math.max(0, end - 10);
        return end;
    }

    /** Chance of ending higher or lower than where you land, using a Spare Key well if you hold one. */
    public Odds odds(Tenant t, Board.Pace p) {
        int up = 0, down = 0, n = 0;
        float total = 0;
        for (int r = paceMin(p); r <= paceMax(p); r++, n++) {
            int steps = bestSteps(t, r);
            int landed = Board.land(t.sq, steps);
            int end = preview(t, steps);
            if (end > landed) up++;
            if (end < landed) down++;
            total += value(end);
        }
        return new Odds(up / (float) n, down / (float) n, total / n);
    }

    public Board.Pace cpuPace() {
        if (!canHustle()) return Board.Pace.STAIRS;
        Tenant t = current();
        return odds(t, Board.Pace.HUSTLE).expected > odds(t, Board.Pace.STAIRS).expected + .25f ? Board.Pace.HUSTLE : Board.Pace.STAIRS;
    }

    // ------------------------------------------------------------------ cards

    public List<Tenant> targets(Tenant me, Card c) {
        List<Tenant> list = new ArrayList<>();
        if (c.use != Card.Use.TENANT) return list;
        for (Tenant o : tenants) {
            if (o == me) continue;
            switch (c) {
                case SUBLET:
                    if (o.sq != me.sq && Math.abs(Board.floorOf(o.sq) - Board.floorOf(me.sq)) <= 1) list.add(o);
                    break;
                case NOISE_COMPLAINT: if (!o.stairsOnly) list.add(o); break;
                case PACKAGE_THEFT: if (!o.hand.isEmpty()) list.add(o); break;
                case CHERRY_BOMB: if (o.sq >= 1 && o.sq < Board.ROOF && !hole(o.sq)) list.add(o); break;
                case GREASE_FIRE: if (o.sq >= 1 && !fire(Board.floorOf(o.sq))) list.add(o); break;
                case PIANO_DELIVERY: if (o.sq >= 1) list.add(o); break;
                default: break;
            }
        }
        return list;
    }

    public boolean playable(Tenant me, Card c) {
        if (cardPlayed || c.use == Card.Use.PASSIVE || !me.has(c)) return false;
        if (c.use == Card.Use.TENANT) return !targets(me, c).isEmpty();
        if (c == Card.DOORMAN_TIP) return !me.doormanTip;
        return true;
    }

    /** Short, plain description of what playing this card would do right now, for the confirm panel. */
    public String preview(Tenant me, Card c, Tenant target) {
        switch (c) {
            case TAKE_THE_FREIGHT: {
                int to = preview(me, 10);
                return "You'd end on " + (to == 0 ? "the lobby" : String.valueOf(to)) + ".";
            }
            case PIANO_DELIVERY: {
                int col = Board.columnOf(target.sq), hits = 0;
                boolean self = false;
                for (Tenant o : tenants) if (o.sq >= 1 && Board.columnOf(o.sq) == col) { hits++; if (o == me) self = true; }
                return hits + (hits == 1 ? " tenant" : " tenants") + " in that column" + (self ? ", including you." : ".");
            }
            case WATER_TOWER_BURST: {
                int hits = 0;
                boolean self = false;
                for (Tenant o : tenants) if (o.sq > 80 && o.sq < Board.ROOF) { hits++; if (o == me) self = true; }
                return hits == 0 ? "Nobody is on floors 9 or 10 right now." : hits + " would get washed down" + (self ? ", including you." : ".");
            }
            case MICROWAVE_INCIDENT: {
                int hits = 0;
                for (Tenant o : tenants) if (o != me && o.sq > 0 && Board.floorOf(o.sq) == Board.floorOf(me.sq)) hits++;
                return hits == 0 ? "Nobody else is on your floor." : hits + " on your floor would get knocked back.";
            }
            case SUBLET: return "You'd move to " + (target.sq == 0 ? "the lobby" : String.valueOf(target.sq)) + ", they'd move to " + (me.sq == 0 ? "the lobby" : String.valueOf(me.sq)) + ".";
            default: return "";
        }
    }

    /** Play a card from the current tenant's hand. Freight and Alien Abduction replace the roll. */
    public List<Beat> play(Card c, Tenant target) {
        out = new ArrayList<>();
        Tenant me = current();
        me.hand.remove(c);
        cardPlayed = true;
        Beat r = beat(Beat.Type.CARD);
        r.a = me.id; r.b = target == null ? -1 : target.id; r.card = c;
        switch (c) {
            case DOORMAN_TIP:
                me.doormanTip = true;
                say(me.name + " tipped the doorman. Next elevator goes one floor higher.");
                break;
            case TAKE_THE_FREIGHT:
                movedByCard = true;
                if (me.sq + 10 <= Board.ROOF) {
                    Beat f = beat(Beat.Type.FREIGHT);
                    f.a = me.id; f.b = me.sq + 10;
                    say(me.name + " took the freight elevator up one floor.");
                    resolve(me, me.sq + 10, true);
                } else {
                    walk(me, 10);
                }
                afterLanding(me);
                break;
            case SUBLET: {
                int a = me.sq;
                me.sq = target.sq;
                target.sq = a;
                Beat s = beat(Beat.Type.SWAP);
                s.a = me.id; s.b = target.id;
                say(me.name + " sublet " + target.name + "'s unit. They swapped places.");
                break;
            }
            case NOISE_COMPLAINT:
                target.stairsOnly = true;
                beat(Beat.Type.SHUSH).a = target.id;
                say(target.name + " got a noise complaint and has to take the stairs next turn.");
                break;
            case PACKAGE_THEFT: {
                Card stolen = target.hand.remove(rng.nextInt(target.hand.size()));
                Beat s = beat(Beat.Type.STEAL);
                s.a = me.id; s.b = target.id;
                if (me.hand.size() < HAND_MAX) {
                    me.hand.add(stolen);
                    s.card = stolen;
                    say(me.name + " stole a package from " + target.name + ".");
                } else {
                    say(me.name + " stole a package from " + target.name + " and threw it away. No room.");
                }
                break;
            }
            case CHERRY_BOMB: {
                int sq = target.sq;
                Beat b = beat(Beat.Type.BOMB);
                b.a = sq;
                if (target.has(Card.FIRE_EXTINGUISHER)) {
                    target.hand.remove(Card.FIRE_EXTINGUISHER);
                    b.b = 0;
                    b.moves = new int[0][];
                    beat(Beat.Type.EXTINGUISH).a = target.id;
                    say(target.name + " put out the fuse with a Fire Extinguisher.");
                } else {
                    b.b = 1;
                    holeUntil[sq] = rounds(3);
                    List<int[]> m = new ArrayList<>();
                    for (Tenant o : tenants) if (o.sq == sq) { o.sq = Math.max(0, sq - 10); m.add(new int[]{o.id, o.sq}); }
                    b.moves = moves(m);
                    say("Boom. There's a hole at " + sq + " for 3 rounds.");
                }
                break;
            }
            case GREASE_FIRE: {
                int f = Board.floorOf(target.sq);
                fireUntil[f] = rounds(2);
                beat(Beat.Type.FIRE_ON).a = f;
                say("Floor " + f + " is on fire for 2 rounds. Don't end a turn there.");
                break;
            }
            case PIANO_DELIVERY: {
                int col = Board.columnOf(target.sq);
                List<int[]> m = new ArrayList<>();
                List<Tenant> hit = new ArrayList<>();
                for (Tenant o : tenants) if (o.sq >= 1 && Board.columnOf(o.sq) == col) hit.add(o);
                Collections.sort(hit, (x, y) -> y.sq - x.sq);
                for (Tenant o : hit) m.add(new int[]{o.id, blocked(o) ? -1 : (o.sq = Math.max(0, o.sq - 10))});
                Beat p = beat(Beat.Type.PIANO);
                p.a = col; p.moves = moves(m);
                say("Piano delivery! It came down column " + (col + 1) + ".");
                break;
            }
            case SNAPPED_CABLE: {
                cableUntil = rounds(2);
                List<int[]> m = new ArrayList<>();
                for (Tenant o : tenants) {
                    if (Board.isElevatorTop(o.sq)) m.add(new int[]{o.id, blocked(o) ? -1 : (o.sq = Board.elevatorBottomFor(o.sq))});
                }
                beat(Beat.Type.CABLE_SNAP).moves = moves(m);
                say("Snap! The elevators are broken for 2 rounds.");
                break;
            }
            case WATER_TOWER_BURST: {
                List<int[]> m = new ArrayList<>();
                for (Tenant o : tenants) if (o.sq > 80 && o.sq < Board.ROOF) m.add(new int[]{o.id, blocked(o) ? -1 : (o.sq -= 10)});
                beat(Beat.Type.WATER).moves = moves(m);
                say(m.isEmpty() ? "The water tower burst. Nobody was up there." : "The water tower burst all over the top floors.");
                break;
            }
            case MICROWAVE_INCIDENT: {
                List<int[]> m = new ArrayList<>();
                int f = Board.floorOf(me.sq);
                for (Tenant o : tenants) {
                    if (o == me || o.sq <= 0 || Board.floorOf(o.sq) != f) continue;
                    int knock = 1 + rng.nextInt(3);
                    m.add(new int[]{o.id, blocked(o) ? -1 : (o.sq = Math.max(0, o.sq - knock))});
                }
                Beat p = beat(Beat.Type.POPCORN);
                p.a = me.id; p.moves = moves(m);
                say(me.name + " microwaved popcorn for 20 minutes.");
                break;
            }
            case RACCOON_INFESTATION: {
                if (raccoons()) beat(Beat.Type.RACCOONS_OUT).a = raccoonChute;
                raccoonChute = chuteAhead(me.sq);
                raccoonUntil = rounds(2);
                beat(Beat.Type.RACCOONS_IN).a = raccoonChute;
                say("Raccoons moved into chute " + Board.CHUTES[raccoonChute][0] + ". Land on " + Board.CHUTES[raccoonChute][1] + " and they fling you up.");
                break;
            }
            case ALIEN_ABDUCTION: {
                movedByCard = true;
                int to = 71 + rng.nextInt(29);
                Beat u = beat(Beat.Type.UFO);
                u.a = me.id; u.b = to;
                say(me.name + " was abducted and dropped off at " + to + ".");
                resolve(me, to, true);
                afterLanding(me);
                break;
            }
            case EARTHQUAKE: {
                List<int[]> m = new ArrayList<>();
                for (Tenant o : tenants) {
                    int delta = (1 + rng.nextInt(6)) * (rng.nextBoolean() ? 1 : -1);
                    o.sq = Math.max(0, Math.min(Board.ROOF - 1, o.sq + delta));
                    m.add(new int[]{o.id, o.sq});
                }
                beat(Beat.Type.QUAKE).moves = moves(m);
                say("Earthquake! Everyone got shaken loose.");
                for (Tenant o : tenants) if (o.sq > 0) resolve(o, o.sq, true);
                break;
            }
            case TIME_WARP: {
                int[] snap = history.get(Math.max(0, history.size() - 1 - n()));
                List<int[]> m = new ArrayList<>();
                for (Tenant o : tenants) { o.sq = snap[o.id]; m.add(new int[]{o.id, o.sq}); }
                beat(Beat.Type.WARP).moves = moves(m);
                say("Time Warp. Everyone is back where they were a round ago.");
                break;
            }
            case BAD_WIFI: {
                List<int[]> m = new ArrayList<>();
                for (Tenant o : tenants) if (o != me) { o.offline = true; m.add(new int[]{o.id, -1}); }
                beat(Beat.Type.WIFI).moves = moves(m);
                say("The Wi-Fi went down. Everyone else loses their next turn.");
                break;
            }
            default: break;
        }
        return out;
    }

    /** Fire Extinguisher stops physical harm once. */
    private boolean blocked(Tenant o) {
        if (!o.has(Card.FIRE_EXTINGUISHER)) return false;
        o.hand.remove(Card.FIRE_EXTINGUISHER);
        beat(Beat.Type.EXTINGUISH).a = o.id;
        return true;
    }

    /** The chute whose bottom is closest ahead of you, so the raccoons can actually help. */
    private int chuteAhead(int sq) {
        int best = -1, bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < Board.CHUTES.length; i++) {
            int gap = Board.CHUTES[i][1] - sq;
            if (gap > 0 && gap < bestGap) { bestGap = gap; best = i; }
        }
        return best >= 0 ? best : rng.nextInt(Board.CHUTES.length);
    }

    // ------------------------------------------------------------------ CPU

    public static final class Choice {
        public final Card card;
        public final Tenant target;
        Choice(Card card, Tenant target) { this.card = card; this.target = target; }
    }

    /** CPU tenants try each card in a few simulated futures and play it if it clearly helps. */
    public Choice cpuCard() {
        Tenant me = current();
        if (cardPlayed) return null;
        float base = 0;
        int trials = 6;
        for (int i = 0; i < trials; i++) base += simulate(null, null);
        base /= trials;
        Choice best = null;
        float bestGain = me.hand.size() >= HAND_MAX ? 1.5f : 3.5f;
        List<Card> seen = new ArrayList<>();
        for (Card c : me.hand) {
            if (seen.contains(c) || !playable(me, c)) continue;
            seen.add(c);
            List<Tenant> options = c.use == Card.Use.TENANT ? targets(me, c) : Collections.singletonList(null);
            for (Tenant target : options) {
                float v = 0;
                for (int i = 0; i < trials; i++) v += simulate(c, target);
                float gain = v / trials - base + bonus(me, c, target);
                if (gain > bestGain) { bestGain = gain; best = new Choice(c, target); }
            }
        }
        return best;
    }

    /** Effects that only pay off on later turns, which a one-turn simulation can't see. */
    private float bonus(Tenant me, Card c, Tenant target) {
        switch (c) {
            case BAD_WIFI: {
                float b = 0;
                for (Tenant o : tenants) if (o != me) b += o.sq >= me.sq ? 4 : 1.5f;
                return b;
            }
            case NOISE_COMPLAINT: return target.sq >= me.sq ? 2 : 0;
            case DOORMAN_TIP: {
                for (int[] e : Board.ELEVATORS) if (e[0] - me.sq >= 1 && e[0] - me.sq <= 8) return 4;
                return 0;
            }
            case GREASE_FIRE: {
                int f = Board.floorOf(target.sq);
                float b = 0;
                for (Tenant o : tenants) if (Board.floorOf(o.sq) == f) b += o == me ? -4 : 3;
                return b;
            }
            case RACCOON_INFESTATION: {
                int gap = Board.CHUTES[chuteAhead(me.sq)][1] - me.sq;
                return gap > 0 && gap <= 8 ? 5 : 0;
            }
            case PACKAGE_THEFT: return 2;
            default: return 0;
        }
    }

    /** Play out this turn on a copy (optionally with a card first) and score the result. */
    private float simulate(Card c, Tenant target) {
        Engine e = copy();
        Tenant me = e.current();
        if (c != null) e.play(c, target == null ? null : e.tenants.get(target.id));
        if (!e.movedByCard) {
            int r = e.roll(e.cpuPace());
            int steps = e.bestSteps(me, r);
            e.move(steps, steps != r);
        }
        return e.score(me.id);
    }

    private float score(int id) {
        Tenant me = tenants.get(id);
        if (me.sq == Board.ROOF) return 1000;
        float best = 0;
        for (Tenant o : tenants) if (o.id != id) best = Math.max(best, value(o.sq));
        return value(me.sq) - .7f * best + me.hand.size() * 1.5f;
    }

    public Engine copy() {
        Engine e = new Engine(rng.nextLong());
        for (Tenant t : tenants) e.tenants.add(t.copy());
        e.turn = turn; e.turnCount = turnCount;
        e.cardPlayed = cardPlayed; e.movedByCard = movedByCard;
        e.powerUntil = powerUntil; e.expressUntil = expressUntil; e.wetUntil = wetUntil; e.cableUntil = cableUntil;
        e.pileupUntil = pileupUntil; e.raccoonUntil = raccoonUntil;
        e.pileupChute = pileupChute; e.raccoonChute = raccoonChute;
        System.arraycopy(holeUntil, 0, e.holeUntil, 0, holeUntil.length);
        System.arraycopy(fireUntil, 0, e.fireUntil, 0, fireUntil.length);
        for (int[] h : history) e.history.add(h.clone());
        return e;
    }
}

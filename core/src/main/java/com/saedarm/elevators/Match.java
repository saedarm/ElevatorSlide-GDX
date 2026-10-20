package com.saedarm.elevators;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.List;

/**
 * Runs a game on screen. The Engine decides everything up front; this class turns each
 * {@link Beat} into animation steps and waits for the player's input between them.
 */
public final class Match {
    public interface Listener {
        void changed();
        void banner(String message);
        void die(int value, String label);
        void cardReveal(Player by, Card card, Player target);
        void eventReveal(Engine.Event event);
        void drewCard(Player p, Card card, String why);
        void nudge(int roll, int[] options, int[] ends);
        void floatText(Vector3 at, String text, Color color);
        void flash(Color color, float seconds);
        void vhs(float seconds);
        void alarm(float seconds);
        void won(Player winner);
    }

    public enum Phase { SETUP, WAITING, NUDGE, BUSY, OVER }

    private final Tower tower;
    private final Fx fx;
    private final Audio audio;
    /** True while the pause menu is open: nothing moves. */
    public boolean paused;
    /** 1 normally; the Fast animations setting raises it. */
    public float speed = 1;
    private final Seq seq = new Seq();
    public final Array<Player> players = new Array<>();
    private Engine engine;
    private Listener listener;
    private Phase phase = Phase.SETUP;
    private int pendingRoll;
    /** Where the camera should look while an effect plays; null means follow the current player. */
    private Vector3 focus;

    // visual state, driven only by beats so it changes when the animation says so
    private final boolean[] burning = new boolean[11];
    private boolean wet;
    private final Array<ModelInstance>[] raccoons;
    private final float[] raccoonPhase = new float[3];
    private ModelInstance pile;
    private float fireAcc, dripAcc;
    private final Vector3 tmp = new Vector3(), tmp2 = new Vector3();

    @SuppressWarnings("unchecked")
    public Match(Tower tower, Fx fx, Audio audio) {
        this.tower = tower;
        this.fx = fx;
        this.audio = audio;
        raccoons = new Array[Board.CHUTES.length];
        for (int i = 0; i < raccoons.length; i++) raccoons[i] = new Array<>();
        for (int i = 0; i < 3; i++) raccoonPhase[i] = i * 2.1f;
    }

    public void setListener(Listener l) { listener = l; }

    public Engine engine() { return engine; }

    public Phase phase() { return phase; }

    public Vector3 focus() { return focus; }

    private void focusOn(final Vector3 at) { seq.call(() -> focus = at); }

    public Player current() { return engine == null || players.isEmpty() ? null : players.get(engine.turn); }

    public Player player(int id) { return players.get(id); }

    public boolean humanCanAct() { return phase == Phase.WAITING && current() != null && !current().cpu(); }

    // ------------------------------------------------------------------ lifecycle

    public void start(String[] names, boolean[] cpu, int[] colorIndex) {
        seq.clear();
        resetVisuals();
        players.clear();
        engine = new Engine(System.nanoTime());
        for (int i = 0; i < names.length; i++) {
            Engine.Tenant t = engine.add(names[i], cpu[i]);
            Player p = new Player(t, Ui.PLAYERS[colorIndex[i]], tower.newToken(colorIndex[i]));
            p.yaw = Tower.facingDeg(0);
            players.add(p);
        }
        if (ElevatorsGame.testHand != null) {
            for (String c : ElevatorsGame.testHand) {
                if (players.get(0).t.hand.size() < Engine.HAND_MAX) players.get(0).t.hand.add(Card.valueOf(c.trim()));
            }
        }
        settle();
        listener.changed();
        listener.banner("Everyone's in the lobby. " + players.get(0).name() + " goes first.");
        startTurn();
    }

    public void abandon() { seq.clear(); phase = Phase.SETUP; }

    private void resetVisuals() {
        for (int i = 0; i <= Board.ROOF; i++) tower.hideTile(i, false);
        tower.setPower(true);
        tower.setExpress(false);
        tower.setCableBroken(false);
        java.util.Arrays.fill(burning, false);
        wet = false;
        for (Array<ModelInstance> a : raccoons) { for (ModelInstance m : a) fx.remove(m); a.clear(); }
        if (pile != null) { fx.remove(pile); pile = null; }
    }

    public void update(float dt) {
        if (paused) return;
        dt *= speed;
        seq.update(dt);
        fireAcc += dt * 16;
        while (fireAcc >= 1) {
            fireAcc -= 1;
            for (int f = 1; f <= 10; f++) {
                if (!burning[f]) continue;
                fx.emit(Fx.Kind.FIRE, Tower.randomOnFloor(f, tmp), 1, .05f);
                if (MathUtils.randomBoolean(.15f)) fx.emit(Fx.Kind.SMOKE, tmp.add(0, .5f, 0), 1, .1f);
            }
        }
        if (wet) {
            dripAcc += dt * 24;
            while (dripAcc >= 1) {
                dripAcc -= 1;
                Tower.randomOnFloor(1 + MathUtils.random(9), tmp);
                tmp.y -= Tower.THICK;
                fx.emit(Fx.Kind.DRIP, tmp, 1, .02f);
            }
        }
        for (int i = 0; i < raccoonPhase.length; i++) raccoonPhase[i] += dt * 5;
        for (int ci = 0; ci < raccoons.length; ci++) {
            Array<ModelInstance> a = raccoons[ci];
            for (int k = 0; k < a.size; k++) {
                float bob = Math.abs(MathUtils.sin(raccoonPhase[k % 3])) * .12f;
                placeRaccoon(a.get(k), ci, k, bob, 1);
            }
        }
        for (int i = 0; i < players.size; i++) players.get(i).syncToken();
    }

    // ------------------------------------------------------------------ turn flow

    private void startTurn() {
        phase = Phase.BUSY;
        focus = null;
        play(engine.beginTurn());
        seq.call(() -> {
            listener.changed();
            if (engine.skipping) { seq.pause(.9f).call(this::endTurn); return; }
            if (current().cpu()) seq.pause(.8f).call(this::cpuAct);
            else { phase = Phase.WAITING; listener.changed(); }
        });
    }

    private void cpuAct() {
        Engine.Choice c = engine.cpuCard();
        if (c != null) playCardNow(c.card, c.target);
        else rollAndMove(engine.cpuPace());
    }

    /** Human taps a pace button. */
    public void takeTurn(Board.Pace pace) {
        if (!humanCanAct()) return;
        if (pace == Board.Pace.HUSTLE && !engine.canHustle()) return;
        rollAndMove(pace);
    }

    /** Human plays a card from the confirm panel. */
    public void playCard(Card card, Engine.Tenant target) {
        if (!humanCanAct() || !engine.playable(engine.current(), card)) return;
        playCardNow(card, target);
    }

    private void playCardNow(Card card, Engine.Tenant target) {
        phase = Phase.BUSY;
        listener.changed();
        play(engine.play(card, target));
        seq.call(() -> {
            focus = null;
            listener.changed();
            if (engine.winner != null) { win(); return; }
            if (engine.movedByCard) { seq.pause(.4f).call(this::endTurn); return; }
            if (current().cpu()) seq.pause(.5f).call(() -> rollAndMove(engine.cpuPace()));
            else { phase = Phase.WAITING; listener.changed(); }
        });
    }

    private void rollAndMove(final Board.Pace pace) {
        phase = Phase.BUSY;
        final Player p = current();
        final int roll = engine.roll(pace);
        final String label = engine.paceMin(pace) + "–" + engine.paceMax(pace);
        listener.changed();
        listener.banner(p.name() + (pace == Board.Pace.STAIRS ? " takes the stairs…" : " hustles…"));
        final int[] last = {-1};
        seq.then(.5f, () -> audio.play("dice_rattle", .8f, 1f), t -> {
            int bucket = (int) (t * 9);
            if (bucket != last[0]) {
                last[0] = bucket;
                listener.die(engine.paceMin(pace) + MathUtils.random(engine.paceMax(pace) - engine.paceMin(pace)), label);
            }
        }, () -> { listener.die(roll, label); audio.play("dice_land", .7f, 1f); });
        seq.call(() -> {
            int[] options = engine.stepOptions(p.t, roll);
            if (options.length == 1) { doMove(roll, false); return; }
            if (p.cpu()) {
                int s = engine.bestSteps(p.t, roll);
                doMove(s, s != roll);
                return;
            }
            pendingRoll = roll;
            int[] ends = new int[options.length];
            for (int i = 0; i < options.length; i++) ends[i] = engine.preview(p.t, options[i]);
            phase = Phase.NUDGE;
            listener.nudge(roll, options, ends);
        });
    }

    /** Human picks from the Spare Key prompt. */
    public void chooseSteps(int steps) {
        if (phase != Phase.NUDGE) return;
        doMove(steps, steps != pendingRoll);
    }

    private void doMove(int steps, boolean usedKey) {
        phase = Phase.BUSY;
        listener.changed();
        play(engine.move(steps, usedKey));
        seq.call(() -> {
            listener.changed();
            if (engine.winner != null) win();
            else seq.pause(.35f).call(this::endTurn);
        });
    }

    private void endTurn() {
        engine.endTurn();
        startTurn();
    }

    private void win() {
        phase = Phase.OVER;
        final Player w = players.get(engine.winner.id);
        listener.changed();
        seq.then(1.6f, () -> { listener.flash(Ui.BRASS, .4f); audio.play("win_fanfare"); audio.buzz(220); }, t -> {
            if (MathUtils.randomBoolean(.5f)) fx.emit(Fx.Kind.CONFETTI, tmp.set(w.pos).add(0, 1.5f, 0), 2, .6f);
        }, () -> listener.won(w));
    }

    // ------------------------------------------------------------------ positions

    /** Spread tenants who share a square so they don't overlap. */
    private Vector3 slot(Player p, int sq, Vector3 out) {
        Tower.tilePos(sq, out);
        int n = 0, idx = 0;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            int qs = q == p ? sq : q.shownSq;
            if (q == p) idx = n;
            if (qs == sq) n++;
        }
        if (n < 2) return out;
        float a = Tower.angleOf(sq);
        float spread = (idx - (n - 1) / 2f) * .6f, radial = idx % 2 == 1 ? .35f : -.35f;
        return out.add(-MathUtils.sin(a) * spread + MathUtils.cos(a) * radial, 0,
                MathUtils.cos(a) * spread + MathUtils.sin(a) * radial);
    }

    private void settle() {
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
            p.shownSq = p.t.sq;
        }
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
            slot(p, p.shownSq, p.pos);
            p.yaw = Tower.facingDeg(p.shownSq);
        }
    }

    private Vector3 head(Player p) { return new Vector3(p.pos).add(0, 1.4f, 0); }

    // ------------------------------------------------------------------ animation building blocks

    private void hop(final Player p, final int toSq, final float height, final float dur) {
        final Vector3 from = new Vector3(), to = new Vector3();
        seq.then(dur, () -> {
            from.set(p.pos);
            p.shownSq = toSq;
            slot(p, toSq, to);
            p.yaw = Tower.facingDeg(toSq);
            audio.vary("step", .35f);
        }, t -> {
            p.pos.set(from).lerp(to, t);
            p.pos.y += MathUtils.sin(MathUtils.PI * t) * height;
        }, null);
    }

    /** Several tenants move at once. style 0 = fall off the ledge, 1 = straight jump with arc, 2 = straight warp, 3 = through the floor. */
    private void moveMany(final int[][] moves, final int style, final float dur, final Runnable start) {
        if (moves == null || moves.length == 0) return;
        if (moves.length == 1 || style == 0) focusOn(players.get(moves[0][0]).pos);
        final int k = moves.length;
        final Player[] who = new Player[k];
        final Vector3[] from = new Vector3[k], to = new Vector3[k];
        final float[] spinDir = new float[k];
        seq.then(dur, () -> {
            if (start != null) start.run();
            for (int i = 0; i < k; i++) {
                if (moves[i][1] < 0) continue;
                who[i] = players.get(moves[i][0]);
                from[i] = new Vector3(who[i].pos);
                who[i].shownSq = moves[i][1];
            }
            for (int i = 0; i < k; i++) {
                if (who[i] == null) {
                    Player b = players.get(moves[i][0]);
                    fx.emit(Fx.Kind.FOAM, head(b), 26, .4f);
                    listener.floatText(head(b), "BLOCKED", Ui.TEAL);
                    audio.play("blocked");
                    audio.play("foam", .6f, 1f);
                    continue;
                }
                to[i] = slot(who[i], who[i].shownSq, new Vector3());
                spinDir[i] = MathUtils.randomSign();
            }
        }, t -> {
            for (int i = 0; i < k; i++) {
                Player p = who[i];
                if (p == null) continue;
                switch (style) {
                    case 0: ledge(from[i], to[i], t, p.pos); break;
                    case 1:
                        p.pos.set(from[i]).lerp(to[i], Seq.ease(t));
                        p.pos.y += MathUtils.sin(MathUtils.PI * t) * 2f;
                        p.spin = spinDir[i] * 360 * t;
                        break;
                    case 2:
                        p.pos.set(from[i]).lerp(to[i], Seq.smooth(t));
                        p.spin = -720 * t;
                        break;
                    default:
                        p.pos.set(from[i]);
                        p.pos.y = MathUtils.lerp(from[i].y, to[i].y, t * t);
                        if (t > .9f) p.pos.lerp(to[i], (t - .9f) * 10);
                        break;
                }
            }
        }, () -> {
            for (int i = 0; i < k; i++) {
                if (who[i] == null) continue;
                who[i].spin = 0;
                who[i].pos.set(to[i]);
                who[i].yaw = Tower.facingDeg(who[i].shownSq);
                fx.emit(Fx.Kind.DUST, to[i], 6, .2f);
            }
            if (style == 0 || style == 3) { audio.vary("splat", .6f); audio.buzz(90); }
        });
    }

    /** Out to the railing, a cartoon fall straight down, then back in to the square. */
    private static void ledge(Vector3 from, Vector3 to, float t, Vector3 out) {
        float r0 = (float) Math.sqrt(from.x * from.x + from.z * from.z);
        float scale = (Tower.ROUT + .7f) / Math.max(.01f, r0);
        float ex = from.x * scale, ez = from.z * scale;
        if (t < .22f) {
            float u = t / .22f;
            out.set(MathUtils.lerp(from.x, ex, u), from.y + MathUtils.sin(MathUtils.PI * u) * .6f, MathUtils.lerp(from.z, ez, u));
        } else if (t < .75f) {
            float u = (t - .22f) / .53f;
            out.set(ex, MathUtils.lerp(from.y, to.y, u * u), ez);
        } else {
            float u = (t - .75f) / .25f;
            out.set(MathUtils.lerp(ex, to.x, u), to.y + MathUtils.sin(MathUtils.PI * u) * .4f, MathUtils.lerp(ez, to.z, u));
        }
    }

    private int[][] one(int id, int sq) { return new int[][]{{id, sq}}; }

    // ------------------------------------------------------------------ beats

    private void play(List<Beat> beats) {
        for (int i = 0; i < beats.size(); i++) {
            Beat b = beats.get(i);
            if (b.type == Beat.Type.EXTINGUISH && blockedLater(beats, i, b.a)) continue;
            beat(b);
        }
    }

    /** A blocked hit already shows foam when the hit lands, so skip the early standalone beat. */
    private static boolean blockedLater(List<Beat> beats, int from, int player) {
        for (int i = from + 1; i < beats.size(); i++) {
            int[][] m = beats.get(i).moves;
            if (m == null) continue;
            for (int[] mv : m) if (mv[0] == player && mv[1] < 0) return true;
        }
        return false;
    }

    /** The sound (and buzz) that starts each beat. Moments inside a beat are voiced where they happen. */
    private void startSound(Beat b) {
        switch (b.type) {
            case CARD: audio.play("card_reveal"); break;
            case EVENT: audio.play("event_chime"); break;
            case DRAW: audio.play("mailbox"); break;
            case FREIGHT: audio.play("elevator_hum"); break;
            case CHUTE: audio.play("chute_whoosh"); audio.buzz(120); break;
            case CHUTE_UP: audio.play("raccoon"); audio.play("chute_whoosh", .8f, 1.35f); break;
            case GRIPPY: audio.play("blocked"); break;
            case DROP: if (b.c == Beat.DROP_FIRE) audio.play("fire_whoosh"); else audio.play("boing", .8f, .7f); break;
            case EXTINGUISH: audio.play("foam"); break;
            case SWAP: audio.play("swap"); break;
            case SHUSH: audio.play("shush"); break;
            case STEAL: audio.play("steal"); break;
            case FIRE_ON: audio.play("fire_whoosh"); break;
            case FIRE_OUT: audio.play("foam", .4f, .8f); break;
            case HOLE_CLOSE: audio.play("blocked", .5f, 1.2f); break;
            case PIANO: audio.play("piano_whistle"); break;
            case CABLE_SNAP: audio.play("power_down"); audio.play("bomb_fuse", .5f, 1.6f); audio.buzz(150); break;
            case CABLE_FIXED: audio.play("power_up", .7f, 1f); break;
            case WATER: audio.play("water_splash"); audio.buzz(200); break;
            case POPCORN: audio.play("popcorn"); break;
            case RACCOONS_IN: audio.play("raccoon"); break;
            case RACCOONS_OUT: audio.play("raccoon", .4f, 1.2f); break;
            case UFO: audio.play("ufo"); break;
            case QUAKE: audio.play("quake"); audio.buzz(600); break;
            case WARP: audio.play("rewind"); break;
            case WIFI: audio.play("wifi_drop"); break;
            case SKIP: audio.play("wifi_drop", .5f, 1f); break;
            case FIRE_DRILL: audio.play("alarm"); break;
            case POWER_OFF: audio.play("power_down"); break;
            case POWER_ON: audio.play("power_up"); break;
            case EXPRESS_ON: audio.play("elevator_ding", .6f, 1.2f); break;
            case PILEUP_ON: audio.play("boing"); break;
            default: break;
        }
    }

    private void beat(final Beat b) {
        if (b.type != Beat.Type.BANNER && b.type != Beat.Type.WALK) seq.call(() -> startSound(b));
        switch (b.type) {
            case BANNER: seq.call(() -> listener.banner(b.text)); break;
            case CARD:
                seq.then(1.9f, () -> listener.cardReveal(players.get(b.a), b.card, b.b < 0 ? null : players.get(b.b)), null, null);
                break;
            case EVENT: seq.then(2.3f, () -> listener.eventReveal(b.event), null, null); break;
            case DRAW: {
                final Player p = players.get(b.a);
                seq.then(.9f, () -> {
                    listener.drewCard(p, b.card, b.text);
                    fx.emit(Fx.Kind.CONFETTI, head(p), 10, .2f);
                    listener.floatText(head(p), "+ CARD", Ui.TEAL);
                }, null, null);
                break;
            }
            case WALK: {
                Player p = players.get(b.a);
                for (int s : b.path) hop(p, s, .55f, .23f);
                break;
            }
            case FREIGHT: freight(players.get(b.a), b.b); break;
            case ELEVATOR: elevator(players.get(b.a), b.b, b.c, b.d); break;
            case CHUTE: chute(players.get(b.a), b.b, b.c, b.d == 1); break;
            case CHUTE_UP: chuteUp(players.get(b.a), b.b, b.c); break;
            case GRIPPY: grippy(players.get(b.a), b.b); break;
            case DROP: {
                final Player p = players.get(b.a);
                if (b.c == Beat.DROP_FIRE) {
                    seq.call(() -> {
                        fx.emit(Fx.Kind.FIRE, p.pos, 18, .3f);
                        listener.floatText(head(p), "HOT HOT HOT", Ui.CORAL);
                    });
                    seq.pause(.3f);
                    moveMany(one(b.a, b.b), 0, 1.1f, null);
                } else {
                    seq.call(() -> listener.floatText(head(p), "WHOOPS", Ui.CORAL));
                    moveMany(one(b.a, b.b), 3, .7f, null);
                }
                break;
            }
            case EXTINGUISH: {
                final Player p = players.get(b.a);
                seq.then(.6f, () -> {
                    fx.emit(Fx.Kind.FOAM, head(p), 30, .4f);
                    listener.floatText(head(p), "FOAM!", Ui.TEAL);
                }, null, null);
                break;
            }
            case SWAP: swap(players.get(b.a), players.get(b.b)); break;
            case SHUSH: {
                final Player p = players.get(b.a);
                focusOn(p.pos);
                seq.then(.9f, () -> listener.floatText(head(p), "SHHH!", Ui.MUTED),
                        t -> p.spin = MathUtils.sin(t * 40) * 14 * (1 - t), () -> p.spin = 0);
                break;
            }
            case STEAL: {
                final Player thief = players.get(b.a), victim = players.get(b.b);
                focusOn(victim.pos);
                seq.then(.9f, () -> {
                    listener.floatText(head(victim), "-1 CARD", Ui.CORAL);
                    listener.floatText(head(thief), b.card == null ? "TOSSED IT" : "+1 CARD", Ui.TEAL);
                    fx.emit(Fx.Kind.CONFETTI, head(thief), 8, .2f);
                }, null, null);
                break;
            }
            case BOMB: bomb(b.a, b.b == 1, b.moves); break;
            case FIRE_ON: {
                final int f = b.a;
                focusOn(Tower.tilePos((f - 1) * 10 + 1, new Vector3()));
                seq.then(1f, () -> {
                    burning[f] = true;
                    listener.flash(Ui.CORAL, .35f);
                    for (int k = 0; k < 40; k++) fx.emit(Fx.Kind.FIRE, Tower.randomOnFloor(f, tmp), 1, .1f);
                }, null, null);
                break;
            }
            case FIRE_OUT: {
                final int f = b.a;
                seq.call(() -> {
                    burning[f] = false;
                    for (int k = 0; k < 16; k++) fx.emit(Fx.Kind.SMOKE, Tower.randomOnFloor(f, tmp), 1, .1f);
                });
                break;
            }
            case HOLE_CLOSE: {
                final int sq = b.a;
                seq.call(() -> {
                    tower.hideTile(sq, false);
                    Tower.tilePos(sq, tmp);
                    fx.emit(Fx.Kind.DUST, tmp, 10, .3f);
                    listener.floatText(tmp.cpy().add(0, 1, 0), "PATCHED", Ui.MUTED);
                });
                break;
            }
            case PIANO: piano(b.a, b.moves); break;
            case CABLE_SNAP: {
                seq.then(.7f, () -> {
                    tower.setCableBroken(true);
                    listener.flash(Color.WHITE, .25f);
                    fx.shake = .4f;
                    for (int[] e : Board.ELEVATORS) fx.emit(Fx.Kind.SPARK, Tower.shaftTop(e, tmp), 14, .3f);
                }, null, null);
                moveMany(b.moves, 0, 1.2f, null);
                break;
            }
            case CABLE_FIXED:
                seq.call(() -> {
                    tower.setCableBroken(false);
                    for (int[] e : Board.ELEVATORS) fx.emit(Fx.Kind.SPARK, Tower.shaftTop(e, tmp), 4, .2f);
                });
                break;
            case WATER: water(b.moves); break;
            case POPCORN: {
                final Player p = players.get(b.a);
                seq.then(.8f, () -> {
                    listener.floatText(head(p), "POP POP POP", Ui.INK);
                    fx.emit(Fx.Kind.POPCORN, head(p), 50, .3f);
                }, t -> { if (MathUtils.randomBoolean(.4f)) fx.emit(Fx.Kind.POPCORN, head(p), 2, .2f); }, null);
                moveMany(b.moves, 1, .7f, null);
                break;
            }
            case RACCOONS_IN: raccoonsIn(b.a); break;
            case RACCOONS_OUT: raccoonsOut(b.a); break;
            case UFO: ufo(players.get(b.a), b.b); break;
            case QUAKE:
                seq.then(.6f, () -> { fx.shake = 1.4f; listener.floatText(new Vector3(0, Tower.TOP_Y / 2, 0), "RUMBLE", Ui.CORAL); }, null, null);
                moveMany(b.moves, 1, 1.1f, null);
                break;
            case WARP:
                moveMany(b.moves, 2, 1.6f, () -> listener.vhs(1.8f));
                break;
            case WIFI:
                seq.then(1f, () -> {
                    for (int[] mv : b.moves) listener.floatText(head(players.get(mv[0])), "NO SIGNAL", Ui.MUTED);
                }, null, null);
                break;
            case SKIP: {
                final Player p = players.get(b.a);
                seq.call(() -> listener.floatText(head(p), "REBOOTING…", Ui.MUTED));
                break;
            }
            case FIRE_DRILL:
                seq.then(.4f, () -> listener.alarm(2.4f), null, null);
                moveMany(b.moves, 0, 1.2f, null);
                break;
            case POWER_OFF: flicker(false); break;
            case POWER_ON: flicker(true); break;
            case EXPRESS_ON: seq.call(() -> tower.setExpress(true)); break;
            case EXPRESS_OFF: seq.call(() -> tower.setExpress(false)); break;
            case WET_ON: seq.call(() -> wet = true); break;
            case WET_OFF: seq.call(() -> wet = false); break;
            case PILEUP_ON: {
                final int ci = b.a;
                seq.call(() -> {
                    if (pile != null) fx.remove(pile);
                    pile = fx.prop("pile");
                    Tower.chutePoint(ci, .5f, tmp);
                    pile.transform.setToTranslation(tmp.add(0, .25f, 0)).scale(1.4f, 1.4f, 1.4f);
                    fx.emit(Fx.Kind.SOCK, tmp, 16, .4f);
                });
                break;
            }
            case PILEUP_OFF:
                seq.call(() -> { if (pile != null) { fx.remove(pile); pile = null; } });
                break;
        }
    }

    // ------------------------------------------------------------------ individual animations

    private void freight(final Player p, final int to) {
        final Vector3 from = new Vector3(), dest = new Vector3();
        seq.then(1.2f, () -> {
            from.set(p.pos);
            p.shownSq = to;
            slot(p, to, dest);
            fx.emit(Fx.Kind.DUST, from, 8, .2f);
            listener.floatText(head(p), "GOING UP", Ui.BRASS);
        }, t -> {
            float u = Seq.ease(t);
            p.pos.set(from).lerp(dest, u);
            p.spin = 360 * u;
        }, () -> { p.spin = 0; audio.play("elevator_ding"); });
    }

    private void elevator(final Player p, final int at, final int top, final int fin) {
        final Tower.Elevator e = tower.elevator(at);
        final Vector3 from = new Vector3(), inside = new Vector3(e.bx, e.y0 + .05f, e.bz);
        seq.pause(.2f);
        seq.then(.3f, () -> from.set(p.pos), t -> {
            p.pos.set(from).lerp(inside, t);
            p.pos.y += MathUtils.sin(MathUtils.PI * t) * .4f;
        }, null);
        seq.then(1.3f, () -> audio.play("elevator_hum"), t -> {
            float y = MathUtils.lerp(e.y0, e.y1, Seq.ease(t));
            e.setCarY(y);
            p.pos.set(e.bx, y + .05f, e.bz);
        }, null);
        seq.call(() -> { audio.play("elevator_ding"); audio.buzz(40); });
        hop(p, top, .4f, .3f);
        if (fin != top) {
            seq.call(() -> { listener.floatText(head(p), "+1 FLOOR", Ui.BRASS); audio.play("boing", 1f, 1.3f); });
            final Vector3 a = new Vector3(), c = new Vector3();
            seq.then(.7f, () -> { a.set(p.pos); p.shownSq = fin; slot(p, fin, c); }, t -> {
                p.pos.set(a).lerp(c, t);
                p.pos.y += MathUtils.sin(MathUtils.PI * t) * 1.2f;
                p.spin = 360 * t;
            }, () -> { p.spin = 0; fx.emit(Fx.Kind.CONFETTI, c, 8, .2f); });
        }
        seq.call(() -> seq.alongside(1.6f, t -> e.setCarY(MathUtils.lerp(e.y1, e.y0, Seq.ease(t)))));
    }

    private void chute(final Player p, final int ci, final int to, final boolean half) {
        final Vector3 ahead = new Vector3();
        float targetY = Tower.yOf(to) + .35f;
        float tEnd = 1f;
        if (half) {
            float lo = 0, hi = 1;
            for (int i = 0; i < 20; i++) {
                float mid = (lo + hi) / 2;
                if (Tower.chutePoint(ci, mid, tmp).y > targetY) lo = mid; else hi = mid;
            }
            tEnd = hi;
        }
        final float end = tEnd;
        seq.pause(.2f);
        seq.then(Tower.chuteSeconds(ci) * (.35f + .65f * end), null, t -> {
            float u = Math.min(end, Seq.smooth(t) * .97f * end + t * .03f * end);
            Tower.chutePoint(ci, u, p.pos);
            Tower.chutePoint(ci, Math.min(1f, u + .01f), ahead);
            p.yaw = MathUtils.atan2(ahead.x - p.pos.x, ahead.z - p.pos.z) * MathUtils.radDeg;
            p.pos.y += .25f;
        }, null);
        if (half) seq.call(() -> {
            fx.emit(Fx.Kind.SOCK, p.pos, 18, .3f);
            listener.floatText(head(p), "SOFT LANDING", Ui.TEAL);
            audio.play("boing");
        });
        hop(p, to, half ? 1f : .5f, half ? .55f : .28f);
    }

    private void chuteUp(final Player p, final int ci, final int to) {
        seq.call(() -> listener.floatText(head(p), "WHEEE", Ui.BRASS));
        seq.then(1.3f, null, t -> {
            float u = 1 - Seq.smooth(t);
            Tower.chutePoint(ci, u, p.pos);
            p.pos.y += .3f;
            p.spin = 720 * t;
        }, () -> p.spin = 0);
        hop(p, to, .6f, .35f);
    }

    private void grippy(final Player p, final int ci) {
        final Vector3 from = new Vector3(), lip = new Vector3();
        seq.then(.3f, () -> { from.set(p.pos); Tower.chutePoint(ci, .04f, lip); }, t -> p.pos.set(from).lerp(lip, t * .7f), null);
        seq.then(.8f, () -> listener.floatText(head(p), "GRIP!", Ui.TEAL),
                t -> p.spin = MathUtils.sin(t * 30) * 25, () -> p.spin = 0);
        seq.then(.3f, null, t -> p.pos.set(lip).lerp(from, .7f + .3f * t), null);
    }

    private void swap(final Player a, final Player b) {
        focusOn(a.pos);
        final Vector3 fa = new Vector3(), fb = new Vector3(), ta = new Vector3(), tb = new Vector3();
        seq.then(1.1f, () -> {
            fa.set(a.pos);
            fb.set(b.pos);
            a.shownSq = a.t.sq;
            b.shownSq = b.t.sq;
            slot(a, a.shownSq, ta);
            slot(b, b.shownSq, tb);
            listener.floatText(head(a), "SUBLET", Ui.BRASS);
        }, t -> {
            float u = Seq.ease(t);
            a.pos.set(fa).lerp(ta, u);
            b.pos.set(fb).lerp(tb, u);
            a.pos.y += MathUtils.sin(MathUtils.PI * t) * 2.5f;
            b.pos.y += MathUtils.sin(MathUtils.PI * t) * 1.5f;
            a.spin = 360 * u;
            b.spin = -360 * u;
        }, () -> { a.spin = 0; b.spin = 0; a.yaw = Tower.facingDeg(a.shownSq); b.yaw = Tower.facingDeg(b.shownSq); });
    }

    private void bomb(final int sq, final boolean exploded, final int[][] moves) {
        final ModelInstance[] bomb = new ModelInstance[1];
        final Vector3 at = Tower.tilePos(sq, new Vector3());
        focusOn(at);
        seq.then(.55f, () -> bomb[0] = fx.prop("bomb"), t -> {
            bomb[0].transform.setToTranslation(at.x, at.y + .3f + 8 * (1 - t * t), at.z).rotate(Vector3.X, t * 300);
        }, null);
        seq.then(.7f, () -> { listener.floatText(new Vector3(at).add(0, 1.2f, 0), "TSSSSS", Ui.BRASS); audio.play("bomb_fuse"); }, t -> {
            bomb[0].transform.setToTranslation(at.x, at.y + .3f, at.z).scale(1 + t * .3f, 1 + t * .3f, 1 + t * .3f);
            if (MathUtils.randomBoolean(.6f)) fx.emit(Fx.Kind.SPARK, tmp.set(at).add(0, .7f, 0), 2, .05f);
        }, null);
        if (!exploded) {
            seq.call(() -> {
                fx.remove(bomb[0]);
                fx.emit(Fx.Kind.FOAM, tmp.set(at).add(0, .4f, 0), 30, .3f);
                fx.emit(Fx.Kind.SMOKE, tmp, 4, .2f);
                listener.floatText(new Vector3(at).add(0, 1.4f, 0), "FIZZLE", Ui.TEAL);
                audio.play("foam");
            });
            return;
        }
        seq.then(1f, () -> {
            fx.remove(bomb[0]);
            listener.flash(Color.WHITE, .3f);
            fx.shake = .8f;
            tmp.set(at).add(0, .3f, 0);
            fx.emit(Fx.Kind.SPARK, tmp, 30, .2f);
            fx.emit(Fx.Kind.DEBRIS, tmp, 18, .4f);
            fx.emit(Fx.Kind.SMOKE, tmp, 14, .4f);
            fx.emit(Fx.Kind.FIRE, tmp, 16, .3f);
            listener.floatText(new Vector3(at).add(0, 1.6f, 0), "BOOM", Ui.CORAL);
            audio.play("bomb_boom");
            audio.buzz(320);
        }, t -> tower.flyTile(sq, t), () -> tower.hideTile(sq, true));
        moveMany(moves, 3, .8f, null);
    }

    private void piano(final int col, final int[][] moves) {
        final int k = moves.length;
        final Vector3 base = Tower.tilePos(col + 1, new Vector3());
        final float startY = Tower.TOP_Y + 16, groundY = -.7f, dur = 2f;
        final Vector3 pianoAt = new Vector3(base);
        focusOn(pianoAt);
        final ModelInstance[] piano = new ModelInstance[1];
        final boolean[] hit = new boolean[k];
        final float deg = Tower.facingDeg(col + 1);
        final int[] floorsSmashed = {11};
        seq.then(dur, () -> {
            piano[0] = fx.prop("piano");
            listener.floatText(new Vector3(base.x, Tower.TOP_Y + 3, base.z), "LOOK OUT", Ui.CORAL);
        }, t -> {
            float y = MathUtils.lerp(startY, groundY, t * t);
            pianoAt.y = Math.max(-.7f, y - 3f);
            piano[0].transform.setToTranslation(base.x, y, base.z).rotate(Vector3.Y, deg + t * 40).scale(1.3f, 1.3f, 1.3f);
            // crash through each floor of this column on the way down
            while (floorsSmashed[0] > 1 && y < Tower.yOf((floorsSmashed[0] - 2) * 10 + col + 1)) {
                floorsSmashed[0]--;
                Vector3 hitAt = Tower.tilePos((floorsSmashed[0] - 1) * 10 + col + 1, new Vector3());
                fx.emit(Fx.Kind.DEBRIS, hitAt, 6, .5f);
                fx.emit(Fx.Kind.DUST, hitAt, 3, .3f);
                fx.shake = Math.max(fx.shake, .25f);
            }
            for (int i = 0; i < k; i++) {
                if (hit[i]) continue;
                final Player p = players.get(moves[i][0]);
                if (y > p.pos.y + 1.6f) continue;
                hit[i] = true;
                fx.shake = Math.max(fx.shake, .5f);
                if (moves[i][1] < 0) {
                    fx.emit(Fx.Kind.FOAM, head(p), 26, .4f);
                    listener.floatText(head(p), "BLOCKED", Ui.TEAL);
                    audio.play("blocked");
                    continue;
                }
                listener.floatText(head(p), "SPLAT", Ui.CORAL);
                audio.play("splat");
                audio.buzz(160);
                fx.emit(Fx.Kind.DEBRIS, p.pos, 8, .3f);
                final Vector3 from = new Vector3(p.pos), to = new Vector3();
                final int dest = moves[i][1];
                p.shownSq = dest;
                slot(p, dest, to);
                seq.alongside(.9f, u -> {
                    if (u < .15f) p.squash = 1 - u / .15f * .75f;
                    else if (u < .8f) {
                        p.squash = .25f;
                        float v = (u - .15f) / .65f;
                        p.pos.set(from).lerp(to, v);
                    } else {
                        p.pos.set(to);
                        float v = (u - .8f) / .2f;
                        p.squash = .25f + .75f * v + MathUtils.sin(v * MathUtils.PI) * .3f;
                    }
                    if (u >= 1) { p.squash = 1; p.yaw = Tower.facingDeg(dest); }
                });
            }
        }, () -> {
            fx.shake = 1f;
            fx.emit(Fx.Kind.DUST, tmp.set(base.x, groundY, base.z), 20, .6f);
            fx.emit(Fx.Kind.DEBRIS, tmp, 10, .4f);
            listener.floatText(new Vector3(base.x, 1, base.z), "CLANG", Ui.BRASS);
            audio.play("piano_crash");
            audio.buzz(260);
            final ModelInstance pz = piano[0];
            seq.alongside(1.4f, u -> {
                pz.transform.setToTranslation(base.x, groundY - u * 1.6f, base.z).rotate(Vector3.Y, deg + 40).scale(1.3f, 1.3f, 1.3f);
                if (u >= 1) fx.remove(pz);
            });
        });
        seq.pause(.5f);
    }

    private void water(final int[][] moves) {
        final Vector3 tank = new Vector3(0, Tower.TOP_Y + 3.2f, 0);
        focusOn(new Vector3(Tower.RMID, Tower.TOP_Y - 2, 0));
        seq.then(1.2f, () -> {
            listener.flash(Color.valueOf("6fb7ff"), .4f);
            listener.floatText(new Vector3(tank).add(0, 1.5f, 0), "SPLOOSH", Ui.TEAL);
            fx.shake = .4f;
        }, t -> fx.emit(Fx.Kind.WATER, tank, 4, 1.2f), null);
        moveMany(moves, 0, 1.2f, () -> {
            for (int[] mv : moves) fx.emit(Fx.Kind.WATER, head(players.get(mv[0])), 12, .3f);
        });
    }

    private void placeRaccoon(ModelInstance r, int ci, int k, float bob, float scale) {
        float u = .18f + k * .3f;
        Tower.chutePoint(ci, u, tmp);
        Tower.chutePoint(ci, u + .02f, tmp2);
        float yaw = MathUtils.atan2(tmp2.z - tmp.z, tmp2.x - tmp.x) * -MathUtils.radDeg;
        r.transform.setToTranslation(tmp.x, tmp.y + .3f + bob, tmp.z).rotate(Vector3.Y, yaw + 90).scale(scale * 1.4f, scale * 1.4f, scale * 1.4f);
    }

    private void raccoonsIn(final int ci) {
        focusOn(Tower.chutePoint(ci, .5f, new Vector3()));
        seq.then(.8f, () -> {
            for (int k = 0; k < 3; k++) raccoons[ci].add(fx.prop("raccoon"));
            Tower.chutePoint(ci, .5f, tmp);
            listener.floatText(tmp.cpy().add(0, 1.4f, 0), "CHITTER CHITTER", Ui.MUTED);
            fx.emit(Fx.Kind.SOCK, tmp, 10, .4f);
        }, null, null);
    }

    private void raccoonsOut(final int ci) {
        seq.call(() -> {
            for (ModelInstance m : raccoons[ci]) {
                m.transform.getTranslation(tmp);
                fx.emit(Fx.Kind.DUST, tmp, 4, .1f);
                fx.remove(m);
            }
            raccoons[ci].clear();
        });
    }

    private void ufo(final Player p, final int to) {
        focusOn(p.pos);
        final ModelInstance[] ship = new ModelInstance[2];
        final Vector3 start = new Vector3(), over = new Vector3(), dest = new Vector3(), overDest = new Vector3();
        seq.then(1f, () -> {
            ship[0] = fx.prop("ufo");
            start.set(p.pos);
            over.set(p.pos).add(0, 3.4f, 0);
            listener.floatText(head(p), "WAIT WHAT", Ui.TEAL);
        }, t -> ship[0].transform.setToTranslation(over.x, over.y + 10 * (1 - Seq.ease(t)), over.z).rotate(Vector3.Y, t * 400), null);
        seq.then(1f, () -> { ship[1] = fx.prop("beam"); audio.play("beam"); }, t -> {
            ship[0].transform.setToTranslation(over).rotate(Vector3.Y, 400 + t * 400);
            ship[1].transform.setToTranslation(over);
            p.pos.set(start).lerp(over, Seq.ease(t));
            p.spin = 720 * t;
            p.scale = 1 - .6f * t;
        }, () -> fx.remove(ship[1]));
        seq.then(1.3f, () -> {
            p.shownSq = to;
            slot(p, to, dest);
            overDest.set(dest).add(0, 3.4f, 0);
        }, t -> {
            tmp.set(over).lerp(overDest, Seq.ease(t));
            tmp.y += MathUtils.sin(MathUtils.PI * t) * 4;
            ship[0].transform.setToTranslation(tmp).rotate(Vector3.Y, 800 + t * 600);
            p.pos.set(tmp);
        }, null);
        seq.then(.9f, () -> { ship[1] = fx.prop("beam"); audio.play("beam"); }, t -> {
            ship[0].transform.setToTranslation(overDest).rotate(Vector3.Y, 1400 + t * 300);
            ship[1].transform.setToTranslation(overDest);
            p.pos.set(overDest).lerp(dest, Seq.ease(t));
            p.scale = .4f + .6f * t;
            p.spin = 720 * (1 - t);
        }, () -> { fx.remove(ship[1]); p.spin = 0; p.scale = 1; p.yaw = Tower.facingDeg(to); });
        seq.then(.8f, null, t -> ship[0].transform.setToTranslation(overDest.x, overDest.y + t * t * 20, overDest.z).rotate(Vector3.Y, t * 900),
                () -> fx.remove(ship[0]));
    }

    private void flicker(final boolean on) {
        seq.then(1f, () -> listener.floatText(new Vector3(0, Tower.TOP_Y * .5f, 0), on ? "POWER BACK" : "BZZT", Ui.BRASS),
                t -> tower.setPower(((int) (t * 12)) % 2 == 0 == on), () -> tower.setPower(on));
    }
}

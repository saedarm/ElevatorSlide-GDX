package com.saedarm.elevators;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** One game: players, whose turn it is, and the animation queue that plays each turn out. */
public final class Match {
    public interface Listener {
        void changed();
        void banner(String message);
        void die(int value, Board.Pace pace);
        void won(Player winner);
    }

    private final Tower tower;
    private final Seq seq = new Seq();
    private final Vector3 tmp = new Vector3();
    public final Array<Player> players = new Array<>();
    private Listener listener;
    private int turn;
    private boolean rolling, over;

    public Match(Tower tower) { this.tower = tower; }

    public void setListener(Listener l) { listener = l; }

    public Player current() { return players.isEmpty() ? null : players.get(turn); }

    public boolean isRolling() { return rolling; }

    public boolean isOver() { return over; }

    public void start(Array<Player> lineup) {
        seq.clear();
        players.clear();
        players.addAll(lineup);
        for (int i = 0; i < players.size; i++) { Player p = players.get(i); p.sq = 0; p.yaw = Tower.facingDeg(0); }
        settle();
        turn = 0;
        over = false;
        rolling = false;
        listener.changed();
        listener.banner("Everyone's in the lobby. " + current().name + " goes first.");
        queueCpuIfNeeded();
    }

    public void update(float dt) {
        seq.update(dt);
        for (int i = 0; i < players.size; i++) players.get(i).syncToken();
    }

    /** Called by the Stairs / Hustle buttons. */
    public void takeTurn(Board.Pace pace) {
        if (rolling || over || current() == null || current().cpu) return;
        playTurn(pace);
    }

    private void playTurn(final Board.Pace pace) {
        rolling = true;
        final Player p = current();
        final int roll = pace.roll();
        listener.changed();
        listener.banner(p.name + (pace == Board.Pace.STAIRS ? " takes the stairs…" : " hustles…"));

        // die shuffle
        final int[] lastBucket = {-1};
        seq.then(.5f, null, t -> {
            int bucket = (int) (t * 9);
            if (bucket != lastBucket[0]) { lastBucket[0] = bucket; listener.die(pace.roll(), pace); }
        }, () -> listener.die(roll, pace));

        // walk square by square, bouncing off the roof if needed
        int target = p.sq + roll;
        for (int s = p.sq + 1; s <= Math.min(target, Board.ROOF); s++) hop(p, s, .55f, .23f);
        if (target > Board.ROOF) {
            for (int s = Board.ROOF - 1; s >= Board.land(p.sq, roll); s--) hop(p, s, .55f, .23f);
            final int extra = target - Board.ROOF;
            seq.call(() -> listener.banner(p.name + " overshot the roof by " + extra + " and bounced back to " + p.sq + "."));
        }
        seq.call(() -> { settle(); listener.changed(); });

        final int landed = Board.land(p.sq, roll);
        switch (Board.kindAt(landed)) {
            case ELEVATOR: rideElevator(p, landed); break;
            case CHUTE: rideChute(p, landed); break;
            default: break;
        }
        seq.call(() -> finishTurn(p));
    }

    private void hop(final Player p, final int toSq, final float height, final float dur) {
        final Vector3 from = new Vector3(), to = new Vector3();
        seq.then(dur, () -> {
            from.set(p.pos);
            p.sq = toSq;
            slotPos(p, to);
            p.yaw = Tower.facingDeg(toSq);
        }, t -> {
            p.pos.set(from).lerp(to, t);
            p.pos.y += MathUtils.sin(MathUtils.PI * t) * height;
        }, null);
    }

    private void rideElevator(final Player p, final int at) {
        final Tower.Elevator e = tower.elevator(at);
        final int dest = Board.resolve(at);
        final Vector3 from = new Vector3(), inside = new Vector3(e.bx, e.y0 + .05f, e.bz);
        seq.pause(.25f);
        seq.call(() -> listener.banner(p.name + " caught the elevator: " + at + " up to " + dest + "!"));
        seq.then(.3f, () -> from.set(p.pos), t -> {
            p.pos.set(from).lerp(inside, t);
            p.pos.y += MathUtils.sin(MathUtils.PI * t) * .4f;
        }, null);
        seq.then(1.3f, null, t -> {
            float y = MathUtils.lerp(e.y0, e.y1, Seq.ease(t));
            e.setCarY(y);
            p.pos.set(e.bx, y + .05f, e.bz);
        }, null);
        hop(p, dest, .4f, .3f);
        seq.call(() -> seq.alongside(1.6f, t -> e.setCarY(MathUtils.lerp(e.y1, e.y0, Seq.ease(t)))));
    }

    private void rideChute(final Player p, final int at) {
        final int ci = tower.chuteIndex(at);
        final int dest = Board.resolve(at);
        final Vector3 ahead = new Vector3();
        seq.pause(.25f);
        seq.call(() -> listener.banner(p.name + " fell down the laundry chute: " + at + " down to " + dest + "."));
        seq.then(Tower.chuteSeconds(ci), null, t -> {
            float u = Seq.smooth(t) * .97f + t * .03f;
            Tower.chutePoint(ci, u, p.pos);
            Tower.chutePoint(ci, Math.min(1f, u + .01f), ahead);
            p.yaw = MathUtils.atan2(ahead.x - p.pos.x, ahead.z - p.pos.z) * MathUtils.radDeg;
            p.pos.y += .25f;
        }, null);
        hop(p, dest, .5f, .28f);
    }

    private void finishTurn(Player p) {
        settle();
        if (p.sq == Board.ROOF) {
            over = true;
            rolling = false;
            listener.changed();
            seq.pause(.5f).call(() -> listener.won(p));
            return;
        }
        seq.pause(.38f).call(() -> {
            turn = (turn + 1) % players.size;
            rolling = false;
            listener.changed();
            queueCpuIfNeeded();
        });
    }

    private void queueCpuIfNeeded() {
        final Player p = current();
        if (p == null || !p.cpu || over) return;
        rolling = true;
        listener.changed();
        seq.pause(.8f).call(() -> playTurn(Board.cpuChoice(p.sq)));
    }

    /** Spread tenants who share a square so they don't overlap. */
    private void slotPos(Player p, Vector3 out) {
        Tower.tilePos(p.sq, out);
        int n = 0, idx = 0;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (q == p) idx = n;
            if (q.sq == p.sq) n++;
        }
        if (n < 2) return;
        float a = Tower.angleOf(p.sq);
        float spread = (idx - (n - 1) / 2f) * .6f, radial = idx % 2 == 1 ? .35f : -.35f;
        out.add(-MathUtils.sin(a) * spread + MathUtils.cos(a) * radial, 0,
                MathUtils.cos(a) * spread + MathUtils.sin(a) * radial);
    }

    private void settle() {
        for (int i = 0; i < players.size; i++) { Player p = players.get(i); slotPos(p, tmp); p.pos.set(tmp); }
    }
}

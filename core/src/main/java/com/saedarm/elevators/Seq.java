package com.saedarm.elevators;

import com.badlogic.gdx.utils.Array;

/** A tiny animation queue: steps run one after another; background steps run alongside. */
public final class Seq {
    public interface Update { void at(float t); }

    private static final class Step {
        float dur, time;
        Runnable start, end;
        Update update;
    }

    private final Array<Step> queue = new Array<>();
    private final Array<Step> background = new Array<>();
    private Step current;

    public Seq then(float dur, Runnable start, Update update, Runnable end) {
        Step s = new Step();
        s.dur = dur; s.start = start; s.update = update; s.end = end;
        queue.add(s);
        return this;
    }

    public Seq pause(float dur) { return then(dur, null, null, null); }

    public Seq call(Runnable r) { return then(0, r, null, null); }

    public void alongside(float dur, Update update) {
        Step s = new Step();
        s.dur = dur; s.update = update;
        background.add(s);
    }

    public void clear() { queue.clear(); background.clear(); current = null; }

    public void update(float dt) {
        for (int i = background.size - 1; i >= 0; i--) {
            Step s = background.get(i);
            s.time += dt;
            float t = Math.min(1f, s.time / s.dur);
            s.update.at(t);
            if (t >= 1f) background.removeIndex(i);
        }
        while (true) {
            if (current == null) {
                if (queue.isEmpty()) return;
                current = queue.removeIndex(0);
                if (current.start != null) current.start.run();
            }
            current.time += dt;
            dt = 0;
            float t = current.dur <= 0 ? 1f : Math.min(1f, current.time / current.dur);
            if (current.update != null) current.update.at(t);
            if (t < 1f) return;
            Step done = current;
            current = null;
            if (done.end != null) done.end.run();
        }
    }

    public static float ease(float t) { return t < .5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }

    public static float smooth(float t) { return t * t * (3 - 2 * t); }
}

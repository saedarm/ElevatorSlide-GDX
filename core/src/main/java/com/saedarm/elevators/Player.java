package com.saedarm.elevators;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;

/** The on-screen tenant: a token that animates toward wherever the Engine already put them. */
public final class Player {
    public final Engine.Tenant t;
    public final Color color;
    public final ModelInstance token;
    public final Vector3 pos = new Vector3();
    public float yaw, spin, squash = 1, scale = 1;
    public boolean visible = true;
    /** The square the token is shown on, which lags the Engine while animations play. */
    public int shownSq;

    public Player(Engine.Tenant t, Color color, ModelInstance token) {
        this.t = t; this.color = color; this.token = token;
    }

    public String name() { return t.name; }

    public boolean cpu() { return t.cpu; }

    void syncToken() {
        float s = 1.6f * scale;
        token.transform.setToTranslation(pos).rotate(Vector3.Y, yaw + spin).scale(s, s * squash, s);
    }
}

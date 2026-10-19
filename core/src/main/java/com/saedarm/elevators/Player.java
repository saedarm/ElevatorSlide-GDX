package com.saedarm.elevators;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;

public final class Player {
    public final String name;
    public final boolean cpu;
    public final Color color;
    public final ModelInstance token;
    public final Vector3 pos = new Vector3();
    public float yaw;
    public int sq;

    public Player(String name, boolean cpu, Color color, ModelInstance token) {
        this.name = name; this.cpu = cpu; this.color = color; this.token = token;
    }

    void syncToken() { token.transform.setToTranslation(pos).rotate(Vector3.Y, yaw).scl(1.6f); }
}

package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** One-finger drag orbits, pinch (or mouse wheel) zooms. The target height follows the active player. */
public final class OrbitCamera extends GestureDetector.GestureAdapter {
    public final PerspectiveCamera cam;
    public final Vector3 target = new Vector3(0, 1.5f, 0);
    public float yaw = 45, pitch = 24, dist = 26, minDist = 9, maxDist = 48;
    private float pinchStartDist;
    private float idle;

    public OrbitCamera(PerspectiveCamera cam) { this.cam = cam; }

    private float dp() { return Math.max(1f, Gdx.graphics.getDensity()); }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        pinchStartDist = dist;
        idle = 0;
        return false;
    }

    @Override
    public boolean pan(float x, float y, float dx, float dy) {
        yaw += dx / dp() * .35f;
        pitch = MathUtils.clamp(pitch + dy / dp() * .25f, 4, 80);
        idle = 0;
        return true;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        dist = MathUtils.clamp(pinchStartDist * initialDistance / distance, minDist, maxDist);
        idle = 0;
        return true;
    }

    public void scroll(float amount) { dist = MathUtils.clamp(dist * (1 + amount * .08f), minDist, maxDist); }

    /** Seconds since the player last touched the camera. */
    public float idle() { return idle; }

    /** Swing around (shortest way) so the camera looks at this angle of the tower, in radians. */
    public void faceAngle(float radians, float dt) {
        float want = radians * MathUtils.radDeg;
        float diff = ((want - yaw) % 360 + 540) % 360 - 180;
        yaw += diff * Math.min(1f, dt * 1.6f);
    }

    public void follow(float y, float dt) {
        target.y += (y - target.y) * Math.min(1f, dt * 3f);
    }

    public void update(float dt) {
        idle += dt;
        float cp = MathUtils.cosDeg(pitch);
        cam.position.set(target.x + cp * MathUtils.cosDeg(yaw) * dist,
                target.y + MathUtils.sinDeg(pitch) * dist,
                target.z + cp * MathUtils.sinDeg(yaw) * dist);
        cam.up.set(Vector3.Y);
        cam.lookAt(target);
        cam.update();
    }
}

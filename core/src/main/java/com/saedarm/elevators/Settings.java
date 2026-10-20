package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Player settings, saved on the device so they stick between launches. */
public final class Settings {
    private final Preferences prefs = Gdx.app.getPreferences("elevators-chutes-settings");

    public float music, sfx;
    public boolean vibrate, followAction, fastAnimations;

    public Settings() {
        music = prefs.getFloat("music", .6f);
        sfx = prefs.getFloat("sfx", .8f);
        vibrate = prefs.getBoolean("vibrate", true);
        followAction = prefs.getBoolean("followAction", true);
        fastAnimations = prefs.getBoolean("fastAnimations", false);
    }

    public void save() {
        prefs.putFloat("music", music);
        prefs.putFloat("sfx", sfx);
        prefs.putBoolean("vibrate", vibrate);
        prefs.putBoolean("followAction", followAction);
        prefs.putBoolean("fastAnimations", fastAnimations);
        prefs.flush();
    }
}

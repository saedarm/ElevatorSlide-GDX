package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Music, sound effects and phone vibration. Every file lives in assets/audio and is made by
 * tools/make_audio.py; drop in a recorded .ogg with the same name to replace any of them.
 */
public final class Audio implements Disposable {
    private static final String[] NAMES = {
            "ui_click", "ui_back", "dice_rattle", "dice_land", "step", "elevator_ding", "elevator_hum",
            "chute_whoosh", "mailbox", "card_reveal", "event_chime", "bomb_fuse", "bomb_boom", "piano_whistle",
            "piano_crash", "ufo", "beam", "raccoon", "rewind", "fire_whoosh", "water_splash", "alarm",
            "power_down", "power_up", "popcorn", "foam", "swap", "shush", "steal", "quake", "wifi_drop",
            "blocked", "boing", "splat", "win_fanfare"};

    private final Settings settings;
    private final ObjectMap<String, Sound> sounds = new ObjectMap<>();
    private Music music;
    private boolean inGame;

    public Audio(Settings settings) {
        this.settings = settings;
        for (String n : NAMES) {
            FileHandle f = Gdx.files.internal("audio/" + n + ".ogg");
            if (f.exists()) sounds.put(n, Gdx.audio.newSound(f));
        }
        FileHandle m = Gdx.files.internal("audio/music_lobby.ogg");
        if (m.exists()) {
            music = Gdx.audio.newMusic(m);
            music.setLooping(true);
        }
        apply();
    }

    /** Call after the settings change. The music sits a little lower during a game. */
    public void apply() {
        if (music == null) return;
        float v = settings.music * (inGame ? .45f : .7f);
        music.setVolume(v);
        if (v <= 0.001f) music.pause();
        else if (!music.isPlaying()) music.play();
    }

    public void setInGame(boolean game) {
        if (inGame == game) return;
        inGame = game;
        apply();
    }

    public void play(String name) { play(name, 1f, 1f); }

    public void play(String name, float volume, float pitch) {
        Sound s = sounds.get(name);
        if (s == null || settings.sfx <= 0) return;
        s.play(Math.min(1f, volume * settings.sfx), pitch, 0);
    }

    /** Slightly different pitch each time so repeated sounds (footsteps, clicks) don't drone. */
    public void vary(String name, float volume) { play(name, volume, MathUtils.random(.9f, 1.12f)); }

    public void buzz(int ms) {
        if (settings.vibrate) Gdx.input.vibrate(ms);
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
        if (music != null) music.dispose();
    }
}

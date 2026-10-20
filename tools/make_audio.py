"""
Generates every sound in the game: an original elevator-music loop and the sound effects.
Nothing is sampled or downloaded. Run it from the project root:

    python3 tools/make_audio.py

Needs numpy, scipy and ffmpeg (for the .ogg files). Output goes to assets/audio/.
Swap any file there for a recorded one with the same name and the game picks it up.
"""
import os
import subprocess
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter, fftconvolve

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "audio")
rng = np.random.default_rng(1997)


# ---------------------------------------------------------------- helpers

def t_(sec):
    return np.arange(int(sec * SR)) / SR


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def lp(x, hz, order=2):
    b, a = butter(order, min(hz, SR / 2 - 100) / (SR / 2), "low")
    return lfilter(b, a, x)


def hp(x, hz, order=2):
    b, a = butter(order, hz / (SR / 2), "high")
    return lfilter(b, a, x)


def bp(x, lo, hi, order=2):
    b, a = butter(order, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], "band")
    return lfilter(b, a, x)


def sweep(f0, f1, sec, curve=1.0):
    """Sine whose pitch glides from f0 to f1."""
    t = t_(sec)
    u = (t / sec) ** curve
    f = f0 + (f1 - f0) * u
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def env(n, attack=0.005, release=0.05):
    e = np.ones(n)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r)
    return e


def decay(sec, rate):
    return np.exp(-t_(sec) * rate)


def noise(sec):
    return rng.uniform(-1, 1, int(sec * SR))


def place(buf, x, at):
    i = int(at * SR)
    end = min(len(buf), i + len(x))
    buf[i:end] += x[: end - i]


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def reverb(x, sec=1.4, wet=0.2, loop=False):
    ir = noise(sec) * np.exp(-t_(sec) / (sec / 4))
    ir = lp(ir, 5000)
    ir /= np.sqrt(np.sum(ir ** 2))
    y = fftconvolve(x, ir)
    if loop:  # wrap the tail back to the start so the loop is seamless
        tail = y[len(x):]
        y = y[: len(x)].copy()
        y[: len(tail)] += tail
    else:
        y = y[: len(x) + int(sec * SR * .5)]
        x = np.concatenate([x, np.zeros(len(y) - len(x))])
    return x * (1 - wet) + y * wet


def save(name, x, quality=4):
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + ".wav")
    ogg = os.path.join(OUT, name + ".ogg")
    wavfile.write(wav, SR, (norm(x) * 32000).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", str(quality), ogg], check=True)
    os.remove(wav)


# ---------------------------------------------------------------- instruments

def epiano(f, sec):
    """FM electric piano: a bell-ish attack that mellows out."""
    t = t_(sec)
    idx = 1.8 * np.exp(-t * 4) + .25
    x = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t))
    x += .06 * np.sin(2 * np.pi * f * 14 * t) * np.exp(-t * 40)
    return x * np.exp(-t * 1.6) * env(len(t), .004, .08)


def vibes(f, sec):
    t = t_(sec)
    x = np.sin(2 * np.pi * f * t) + .28 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 6)
    trem = 1 - .22 * (0.5 + 0.5 * np.sin(2 * np.pi * 5.2 * t))
    return x * trem * np.exp(-t * 1.5) * env(len(t), .003, .1)


def bass(f, sec):
    t = t_(sec)
    x = np.sin(2 * np.pi * f * t) + .3 * np.sin(2 * np.pi * 2 * f * t) + .1 * np.sin(2 * np.pi * 3 * f * t)
    return lp(x * np.exp(-t * 2.2) * env(len(t), .006, .06), 900)


def shaker(sec=.08):
    return hp(noise(sec), 6000) * decay(sec, 60)


def rim(sec=.05):
    t = t_(sec)
    return (np.sin(2 * np.pi * 1800 * t) * .6 + bp(noise(sec), 2000, 5000)) * decay(sec, 90)


def kick(sec=.3):
    return sweep(110, 45, sec, .5) * decay(sec, 14)


# ---------------------------------------------------------------- music

CHORDS = {
    "Fmaj7": [5, 9, 0, 4], "Em7": [4, 7, 11, 2], "A7": [9, 1, 4, 7], "Dm7": [2, 5, 9, 0],
    "G7": [7, 11, 2, 5], "Cmaj7": [0, 4, 7, 11], "Am7": [9, 0, 4, 7], "E7": [4, 8, 11, 2], "D7": [2, 6, 9, 0],
}
ROOT = {"Fmaj7": 41, "Em7": 40, "A7": 45, "Dm7": 38, "G7": 43, "Cmaj7": 36, "Am7": 45, "E7": 40, "D7": 38}
BARS = ["Fmaj7", "Em7|A7", "Dm7", "G7", "Cmaj7", "Am7", "Dm7", "G7",
        "Fmaj7", "E7", "Am7", "D7", "Dm7", "G7", "Cmaj7", "Dm7|G7"]
# (beat, length in beats, midi note) per bar
MELODY = [
    [(0, 1.5, 76), (1.5, .5, 74), (2, 1, 72), (3, 1, 69)],
    [(0, 1.5, 79), (1.5, .5, 76), (2, 1.5, 73), (3.5, .5, 76)],
    [(0, 2, 77), (2, .5, 76), (2.5, .5, 74), (3, 1, 72)],
    [(0, 1.5, 71), (1.5, .5, 74), (2, 2, 77)],
    [(0, 3, 76), (3, .5, 74), (3.5, .5, 76)],
    [(0, 1.5, 72), (1.5, .5, 69), (2, 2, 76)],
    [(0, .5, 74), (.5, .5, 77), (1, 1.5, 81), (2.5, .5, 79), (3, 1, 77)],
    [(0, 1, 76), (1, 1, 74), (2, 2, 71)],
    [(0, 1.5, 81), (1.5, .5, 79), (2, 2, 76)],
    [(0, 1.5, 80), (1.5, .5, 83), (2, 1, 80), (3, 1, 76)],
    [(0, 2, 81), (2, .5, 79), (2.5, .5, 76), (3, 1, 72)],
    [(0, 1.5, 78), (1.5, .5, 81), (2, 2, 84)],
    [(0, 1, 81), (1, 1, 77), (2, 1, 74), (3, 1, 77)],
    [(0, 1.5, 79), (1.5, .5, 77), (2, 1, 74), (3, 1, 71)],
    [(0, 3, 72)],
    [(2, .5, 74), (2.5, .5, 76), (3, .5, 77), (3.5, .5, 79)],
]


def voicing(name):
    notes = []
    for pc in CHORDS[name]:
        n = 55 + ((pc - 55) % 12)
        notes.append(n)
    return sorted(notes)


def music(bpm=104):
    beat = 60 / bpm
    bar = beat * 4
    total = bar * len(BARS)
    keys = np.zeros(int(total * SR) + SR * 3)
    low = np.zeros_like(keys)
    lead = np.zeros_like(keys)
    drums = np.zeros_like(keys)
    for b, spec in enumerate(BARS):
        t0 = b * bar
        parts = spec.split("|")
        halves = [(parts[0], 0, 4)] if len(parts) == 1 else [(parts[0], 0, 2), (parts[1], 2, 2)]
        for name, start, length in halves:
            hits = [(0, 1.2), (1.5, .45), (3, .8)] if length == 4 else [(0, 1.2), (1.5, .45)]
            for hb, hl in hits:
                for n in voicing(name):
                    place(keys, epiano(midi(n), hl * beat + .3) * .22, t0 + (start + hb) * beat)
            r = ROOT[name]
            line = [(0, 1.5, r), (1.5, .5, r + 7), (2, 1.5, r + 7), (3.5, .5, r)] if length == 4 else [(0, 1.5, r), (1.5, .5, r + 7)]
            for bb, bl, n in line:
                place(low, bass(midi(n), bl * beat + .1) * .55, t0 + (start + bb) * beat)
        for mb, ml, n in MELODY[b]:
            place(lead, vibes(midi(n), ml * beat + .6) * .32, t0 + mb * beat)
        for e in range(8):
            place(drums, shaker() * (.10 if e % 2 else .16), t0 + e * beat / 2)
        clave = [0, 1.5, 3] if b % 2 == 0 else [1, 2.5]
        for c in clave:
            place(drums, rim() * .12, t0 + c * beat)
        for k in (0, 2):
            place(drums, kick() * .35, t0 + k * beat)
    n = int(total * SR)

    def fold(x):  # anything ringing past the end wraps to the start
        y = x[:n].copy()
        tail = x[n:]
        y[: len(tail)] += tail
        return y

    keys, low, lead, drums = fold(keys), fold(low), fold(lead), fold(drums)
    mix = reverb(keys, wet=.25, loop=True) + low + reverb(lead, wet=.3, loop=True) + drums
    # filter three copies end to end and keep the middle one, so the filter has no seam either
    mix = lp(np.tile(mix, 3), 9000)[n:2 * n]
    return norm(mix, .8)


# ---------------------------------------------------------------- sound effects

def bell(f, sec=1.2):
    t = t_(sec)
    x = np.sin(2 * np.pi * f * t) + .5 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 4) + .25 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 8)
    return x * np.exp(-t * 3) * env(len(t), .002, .1)


def sfx():
    s = {}
    s["ui_click"] = np.sin(2 * np.pi * 1400 * t_(.05)) * decay(.05, 80) + .3 * bp(noise(.05), 3000, 8000) * decay(.05, 120)
    s["ui_back"] = sweep(900, 500, .09) * decay(.09, 30)
    rattle = np.zeros(int(.5 * SR))
    for k in range(9):
        place(rattle, bp(noise(.03), 1500 + k * 120, 6000) * decay(.03, 120) * rng.uniform(.4, 1), k * .05 + rng.uniform(0, .015))
    s["dice_rattle"] = rattle
    s["dice_land"] = (lp(noise(.12), 1800) * .8 + np.sin(2 * np.pi * 220 * t_(.12)) * .5) * decay(.12, 35)
    s["step"] = (lp(noise(.06), 900) + np.sin(2 * np.pi * 160 * t_(.06)) * .6) * decay(.06, 60)
    ding = np.zeros(int(1.8 * SR))
    place(ding, bell(midi(84)), 0)
    place(ding, bell(midi(79)), .28)
    s["elevator_ding"] = reverb(ding, wet=.2)
    s["elevator_hum"] = (sweep(70, 140, 1.4) * .6 + sweep(140, 280, 1.4) * .2 + lp(noise(1.4), 400) * .3) * env(int(1.4 * SR), .15, .3)
    wh = bp(noise(1.6), 300, 3000)
    s["chute_whoosh"] = lp(wh, 2500) * np.sin(np.linspace(0, np.pi, len(wh))) ** 1.5 + sweep(600, 120, 1.6) * .25 * np.sin(np.linspace(0, np.pi, len(wh)))
    flap = np.zeros(int(.5 * SR))
    place(flap, lp(noise(.05), 2500) * decay(.05, 70), 0)
    place(flap, lp(noise(.06), 1800) * decay(.06, 60) * .9, .12)
    place(flap, bp(noise(.25), 2500, 9000) * decay(.25, 12) * .25, .14)
    s["mailbox"] = flap
    s["card_reveal"] = bp(noise(.35), 800, 6000) * np.sin(np.linspace(0, np.pi, int(.35 * SR))) * .7 + sweep(400, 1200, .35) * .2 * decay(.35, 5)
    chime = np.zeros(int(2 * SR))
    for k, n in enumerate([72, 76, 79]):
        place(chime, bell(midi(n), 1.4) * .7, k * .16)
    s["event_chime"] = reverb(chime, wet=.25)
    s["bomb_fuse"] = bp(noise(.8), 3000, 9000) * (0.7 + .3 * np.sin(2 * np.pi * 23 * t_(.8))) * env(int(.8 * SR), .02, .1)
    boom = lp(noise(1.6), 500) * decay(1.6, 3) + sweep(80, 30, 1.6, .5) * decay(1.6, 4) * 1.2
    s["bomb_boom"] = reverb(np.tanh(boom * 2.5), wet=.25)
    s["piano_whistle"] = sweep(1800, 500, 1.9, 1.3) * env(int(1.9 * SR), .05, .1) * .7
    crash = np.zeros(int(2.5 * SR))
    for n in [36, 37, 43, 48, 49, 55, 60, 61, 66]:
        place(crash, epiano(midi(n), 2.2) * .4, rng.uniform(0, .03))
    place(crash, lp(noise(.5), 1500) * decay(.5, 7) * .9, 0)
    place(crash, hp(noise(1.2), 4000) * decay(1.2, 4) * .4, 0)
    s["piano_crash"] = reverb(crash, wet=.3)
    t = t_(3)
    vib = 6 * np.sin(2 * np.pi * 5 * t)
    f = 520 + 160 * np.sin(2 * np.pi * .7 * t) + vib * 10
    s["ufo"] = (np.sin(2 * np.pi * np.cumsum(f) / SR) * .6 + np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * .2) * env(len(t), .3, .5)
    s["beam"] = (np.sin(2 * np.pi * 1200 * t_(1)) * np.sin(2 * np.pi * 9 * t_(1)) * .4 + hp(noise(1), 5000) * .3) * env(SR, .1, .3)
    chit = np.zeros(int(.9 * SR))
    for k in range(7):
        place(chit, sweep(rng.uniform(1800, 2600), rng.uniform(2800, 3600), .05) * decay(.05, 30) * .7, k * .11 + rng.uniform(0, .03))
    s["raccoon"] = chit
    t = t_(1.7)
    f = 300 + 2200 * (t / 1.7) ** 1.6 + 80 * np.sin(2 * np.pi * 13 * t)
    s["rewind"] = (np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * .25 + bp(noise(1.7), 1000, 6000) * .3) * env(len(t), .05, .15)
    fire = bp(noise(1.2), 200, 2500) * np.sin(np.linspace(0, np.pi, int(1.2 * SR))) ** .7
    s["fire_whoosh"] = lp(fire, 2000) + lp(noise(1.2), 300) * .4 * decay(1.2, 2)
    s["water_splash"] = bp(noise(1.5), 400, 7000) * decay(1.5, 3) + lp(noise(1.5), 600) * decay(1.5, 2) * .6
    alarm = np.zeros(int(2.4 * SR))
    for k in range(6):
        b = np.sin(2 * np.pi * (960 if k % 2 == 0 else 760) * t_(.3))
        place(alarm, np.sign(b) * .3 * env(len(b), .01, .03), k * .4)
    s["alarm"] = alarm
    s["power_down"] = (sweep(220, 30, 1.2) * .6 + sweep(440, 60, 1.2) * .2) * env(int(1.2 * SR), .01, .3)
    s["power_up"] = (sweep(40, 240, 1.0) * .6 + sweep(80, 480, 1.0) * .2) * env(SR, .1, .2)
    pops = np.zeros(int(1.2 * SR))
    for k in range(18):
        place(pops, bp(noise(.025), 800, 4000) * decay(.025, 160) * rng.uniform(.4, 1), rng.uniform(0, 1.1))
    s["popcorn"] = pops
    s["foam"] = bp(noise(.8), 2500, 10000) * env(int(.8 * SR), .02, .4) * .6
    s["swap"] = np.concatenate([sweep(300, 900, .25), sweep(900, 300, .25)]) * .6
    s["shush"] = bp(noise(.7), 2000, 7000) * env(int(.7 * SR), .08, .3)
    steal = np.zeros(int(.6 * SR))
    for k, n in enumerate([64, 67, 71]):
        place(steal, np.sin(2 * np.pi * midi(n) * t_(.12)) * decay(.12, 20) * .6, k * .09)
    s["steal"] = steal
    s["quake"] = lp(noise(2), 120) * env(2 * SR, .2, .6) * 2 + lp(noise(2), 40) * env(2 * SR, .2, .6)
    wifi = np.zeros(int(.8 * SR))
    for k, n in enumerate([88, 84, 79]):
        place(wifi, np.sign(np.sin(2 * np.pi * midi(n) * t_(.14))) * .2 * env(int(.14 * SR), .005, .03), k * .2)
    s["wifi_drop"] = wifi
    s["blocked"] = (np.sin(2 * np.pi * 180 * t_(.15)) + lp(noise(.15), 800) * .5) * decay(.15, 25)
    s["boing"] = sweep(180, 520, .35, .4) * decay(.35, 6) * (1 + .3 * np.sin(2 * np.pi * 18 * t_(.35)))
    s["splat"] = lp(noise(.25), 900) * decay(.25, 14) + sweep(200, 60, .25) * decay(.25, 12)
    fan = np.zeros(int(2.4 * SR))
    for k, (n, l) in enumerate([(72, .18), (76, .18), (79, .18), (84, 1.2)]):
        for h in (0, 4, 7):
            place(fan, epiano(midi(n + h - 12), l + .8) * .3, [0, .18, .36, .54][k])
        place(fan, vibes(midi(n), l + .8) * .5, [0, .18, .36, .54][k])
    s["win_fanfare"] = reverb(fan, wet=.3)
    return s


if __name__ == "__main__":
    save("music_lobby", music(), quality=4)
    for name, x in sfx().items():
        save(name, x, quality=3)
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)))

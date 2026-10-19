# Elevators & Laundry Chutes

A 3D race to the roof of a ten-floor apartment tower, built with libGDX for Android (tuned for the Galaxy S23) with a small desktop launcher for testing.

## Rules

- 100 squares spiral up the tower, ten per floor. Square 34 sits directly above 24, 14 and 4.
- Each turn, pick a pace:
  - **Take the stairs**: move 3–6 (a d4 + 2). Steady.
  - **Hustle**: move 1–8 (a d8). Risky.
  - Both average 4.5 squares a turn. The buttons show your exact chance of landing on an elevator or a chute, so the choice is about risk, not speed.
- **Brass shafts** are elevators. Land on the bottom and ride up.
- **Coral tubes** are laundry chutes. Land on the top and slide down.
- Land on exactly 100 to win. Overshoot and you bounce back by the extra.
- 2–4 tenants, any mix of human and CPU. CPU tenants pick the pace with the better average outcome.

In a 20,000-game simulation, always picking the same pace averages about 49 turns to the roof. Picking the better pace each turn averages about 31, and wins 67% of head-to-head games.

## Project layout

```
core/      all game code (shared)
android/   Android launcher, manifest, icon
lwjgl3/    desktop launcher for quick testing
assets/    fonts (Bungee, Bricolage Grotesque, both SIL OFL)
```

Everything 3D is built in code with `ModelBuilder`. There are no model files and no skin file.

| File | What it does |
| --- | --- |
| `Board.java` | Rules only: elevator and chute tables, bounce-back, odds, CPU choice. No rendering, so it's easy to test. |
| `Tower.java` | Builds the tower, tiles, number labels, elevator shafts and cars, chute tubes, skyline and tokens. |
| `Match.java` | Turn flow. Queues each turn as animation steps: die shuffle, hops, ride, next player. |
| `Seq.java` | Tiny animation queue used by `Match`. |
| `Hud.java` | Scene2D screens: tenant setup, in-game HUD, winner card. |
| `Ui.java` | Fonts and drawables generated at startup, sized for screen density so text is sharp on the S23. |
| `OrbitCamera.java` | One-finger orbit, pinch zoom, follows the active player. |

## Running it

Requires JDK 17+. Open the folder in Android Studio (or IntelliJ with the Android plugin) and let Gradle sync. The Android SDK it builds against is API 36.

**Desktop (fastest to iterate):**

```
./gradlew lwjgl3:run
```

Keys: `1` = stairs, `2` = hustle, `Esc` = back to setup, mouse wheel = zoom.

**On the S23 over USB:**

1. On the phone: Settings → About phone → Software information → tap **Build number** 7 times.
2. Settings → Developer options → turn on **USB debugging**.
3. Plug in, accept the prompt on the phone, then run:

```
./gradlew android:installDebug
```

Or press Run in Android Studio with the phone selected.

**Without a cable:** copy `android-debug.apk` to the phone, tap it in My Files, and allow your file app to install unknown apps when asked.

On the phone, the Back gesture returns to the setup screen from a game, and exits from setup.

## Changing the board

Edit `ELEVATORS` and `CHUTES` in `Board.java`. Keep each pair a multiple of 10 apart, since the elevator shafts are straight vertical columns, and don't let one jump's end square be another jump's start. The 3D model rebuilds from those tables automatically.

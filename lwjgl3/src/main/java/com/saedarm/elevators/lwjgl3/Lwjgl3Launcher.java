package com.saedarm.elevators.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.saedarm.elevators.ElevatorsGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        // Test any card's animation: ./gradlew lwjgl3:run -Dcards=PIANO_DELIVERY,CHERRY_BOMB,ALIEN_ABDUCTION
        String cards = System.getProperty("cards");
        if (cards != null && !cards.isEmpty()) ElevatorsGame.testHand = cards.split(",");
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Elevators & Laundry Chutes");
        // Roughly the S23's 9:19.5 portrait shape, scaled to fit a laptop screen.
        config.setWindowedMode(450, 975);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        new Lwjgl3Application(new ElevatorsGame(), config);
    }
}

package com.saedarm.elevators.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.saedarm.elevators.ElevatorsGame;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.renderUnderCutout = true;
        config.numSamples = 2;
        config.useAccelerometer = false;
        config.useCompass = false;
        initialize(new ElevatorsGame(), config);
    }
}

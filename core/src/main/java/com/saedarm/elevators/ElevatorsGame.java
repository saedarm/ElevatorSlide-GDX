package com.saedarm.elevators;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;

public class ElevatorsGame extends ApplicationAdapter {
    private Ui ui;
    private Tower tower;
    private Match match;
    private Hud hud;
    private PerspectiveCamera cam;
    private OrbitCamera orbit;
    private ModelBatch batch;
    private Environment env;
    private boolean wasSetup = true, introZoom;

    @Override
    public void create() {
        ui = new Ui();
        tower = new Tower(ui.label3d, Ui.PLAYERS);
        match = new Match(tower);
        hud = new Hud(ui, match, tower);
        match.setListener(hud);

        cam = new PerspectiveCamera(45, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = .1f;
        cam.far = 140f;
        orbit = new OrbitCamera(cam);
        orbit.dist = 36;
        orbit.target.y = 14;

        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, .42f, .42f, .52f, 1f));
        env.set(new ColorAttribute(ColorAttribute.Fog, Ui.SKY));
        env.add(new DirectionalLight().set(1f, .86f, .68f, -.45f, -1f, -.3f));
        env.add(new DirectionalLight().set(.22f, .24f, .42f, .6f, -.25f, .55f));
        batch = new ModelBatch();

        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        InputAdapter keys = new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Input.Keys.BACK || key == Input.Keys.ESCAPE) {
                    if (hud.inSetup()) Gdx.app.exit();
                    else showSetup();
                    return true;
                }
                if (key == Input.Keys.NUM_1) { match.takeTurn(Board.Pace.STAIRS); return true; }
                if (key == Input.Keys.NUM_2) { match.takeTurn(Board.Pace.HUSTLE); return true; }
                return false;
            }

            @Override public boolean scrolled(float amountX, float amountY) {
                orbit.scroll(amountY);
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(hud.stage, keys, new GestureDetector(orbit)));
    }

    private void showSetup() {
        hud.showSetup();
        orbit.dist = 36;
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        // Hold a ~54 degree horizontal view so portrait phones aren't looking through a keyhole.
        float aspect = width / (float) Math.max(1, height);
        float fovY = 2f * (float) Math.toDegrees(Math.atan(Math.tan(Math.toRadians(27)) / aspect));
        cam.fieldOfView = Math.max(45f, Math.min(82f, fovY));
        cam.update();
        hud.resize(width, height);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1 / 20f);
        match.update(dt);

        Player p = match.current();
        boolean setup = hud.inSetup();
        if (wasSetup && !setup) introZoom = true;
        wasSetup = setup;
        if (setup || p == null) {
            if (orbit.idle() > 1.5f) orbit.yaw += dt * 6f;
            orbit.follow(14f, dt);
        } else {
            // ease in from the overview the first moment of a game, unless the player grabs the camera
            if (introZoom) {
                orbit.dist += (24 - orbit.dist) * Math.min(1, dt * 2);
                if (orbit.idle() < .05f || Math.abs(orbit.dist - 24) < .3f) introZoom = false;
            }
            orbit.follow(Math.max(1.5f, p.pos.y + .6f), dt);
            if (orbit.idle() > 2.5f) orbit.faceAngle(MathUtils.atan2(p.pos.z, p.pos.x), dt);
        }
        orbit.update(dt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(Ui.SKY.r, Ui.SKY.g, Ui.SKY.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(cam);
        batch.render(tower.instances, env);
        for (int i = 0; i < match.players.size; i++) batch.render(match.players.get(i).token, env);
        batch.end();

        hud.render(dt);
    }

    @Override
    public void dispose() {
        batch.dispose();
        hud.stage.dispose();
        tower.dispose();
        ui.dispose();
    }
}

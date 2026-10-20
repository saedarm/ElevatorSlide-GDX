package com.saedarm.elevators;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** Particles (smoke, sparks, fire, water, popcorn...), cartoon props, and camera shake. */
public final class Fx implements Disposable {
    public enum Kind { SMOKE, SPARK, FIRE, WATER, POPCORN, FOAM, DEBRIS, CONFETTI, DRIP, DUST, SOCK }

    private static final int MAX = 240;
    private static final long PN = Usage.Position | Usage.Normal;

    private static final class P {
        ModelInstance inst;
        ColorAttribute diffuse, emissive;
        BlendingAttribute blend;
        final Vector3 pos = new Vector3(), vel = new Vector3(), axis = new Vector3();
        final Color c0 = new Color(), c1 = new Color();
        float life, max, size0, size1, grav, drag, spin, rot;
        boolean glow, alive;
    }

    private final P[] pool = new P[MAX];
    private final Array<Model> models = new Array<>();
    private final Array<ModelInstance> props = new Array<>();
    private final Model piano, bomb, ufo, beam, raccoon, pile;
    private final RandomXS128 rnd = new RandomXS128();
    private final Color tmp = new Color();
    private int next;
    public float shake;

    public Fx() {
        ModelBuilder mb = new ModelBuilder();
        Material m = new Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createEmissive(Color.BLACK),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f),
                new DepthTestAttribute(GL20.GL_LEQUAL, false));
        Model cube = mb.createBox(1, 1, 1, m, PN);
        models.add(cube);
        for (int i = 0; i < MAX; i++) {
            P p = new P();
            p.inst = new ModelInstance(cube);
            Material pm = p.inst.materials.get(0);
            p.diffuse = (ColorAttribute) pm.get(ColorAttribute.Diffuse);
            p.emissive = (ColorAttribute) pm.get(ColorAttribute.Emissive);
            p.blend = (BlendingAttribute) pm.get(BlendingAttribute.Type);
            pool[i] = p;
        }
        piano = buildPiano(mb);
        bomb = buildBomb(mb);
        ufo = buildUfo(mb);
        beam = buildBeam(mb);
        raccoon = buildRaccoon(mb);
        pile = buildPile(mb);
    }

    // ------------------------------------------------------------------ particles

    public void emit(Kind kind, Vector3 at, int count, float spread) {
        for (int i = 0; i < count; i++) spawn(kind, at, spread);
    }

    private float r(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    private void spawn(Kind k, Vector3 at, float spread) {
        P p = pool[next];
        next = (next + 1) % MAX;
        p.alive = true;
        p.life = 0;
        p.pos.set(at).add(r(-spread, spread), r(-spread * .3f, spread * .3f), r(-spread, spread));
        p.axis.set(r(-1, 1), r(-1, 1), r(-1, 1)).nor();
        p.rot = r(0, 360);
        p.spin = 0;
        p.drag = 1;
        p.glow = false;
        float ang = r(0, MathUtils.PI2);
        switch (k) {
            case SMOKE:
                p.vel.set(r(-.3f, .3f), r(.6f, 1.3f), r(-.3f, .3f));
                p.c0.set(.42f, .4f, .46f, .8f); p.c1.set(.3f, .3f, .34f, 0);
                p.size0 = r(.2f, .35f); p.size1 = r(.7f, 1.1f); p.max = r(1.2f, 2f); p.grav = -.25f; p.drag = .97f;
                break;
            case SPARK:
                p.vel.set(MathUtils.cos(ang) * r(2, 5), r(1, 5), MathUtils.sin(ang) * r(2, 5));
                p.c0.set(1, .95f, .55f, 1); p.c1.set(1, .45f, .1f, 0);
                p.size0 = .09f; p.size1 = .04f; p.max = r(.35f, .8f); p.grav = 9; p.glow = true;
                break;
            case FIRE:
                p.vel.set(r(-.25f, .25f), r(.9f, 2f), r(-.25f, .25f));
                p.c0.set(1, .72f, .2f, .95f); p.c1.set(.9f, .15f, .05f, 0);
                p.size0 = r(.18f, .32f); p.size1 = .05f; p.max = r(.45f, .85f); p.grav = -1.2f; p.glow = true; p.spin = r(-200, 200);
                break;
            case WATER:
                p.vel.set(MathUtils.cos(ang) * r(1.5f, 4), r(.5f, 3), MathUtils.sin(ang) * r(1.5f, 4));
                p.c0.set(.45f, .72f, 1, .85f); p.c1.set(.7f, .88f, 1, 0);
                p.size0 = r(.1f, .2f); p.size1 = .08f; p.max = r(.9f, 1.5f); p.grav = 9;
                break;
            case POPCORN:
                p.vel.set(MathUtils.cos(ang) * r(1, 3), r(3, 6), MathUtils.sin(ang) * r(1, 3));
                p.c0.set(1, .96f, .82f, 1); p.c1.set(1, .9f, .6f, 0);
                p.size0 = r(.12f, .18f); p.size1 = .12f; p.max = r(1.1f, 1.6f); p.grav = 9; p.spin = r(-500, 500);
                break;
            case FOAM:
                p.vel.set(MathUtils.cos(ang) * r(.8f, 2), r(.5f, 2), MathUtils.sin(ang) * r(.8f, 2));
                p.c0.set(1, 1, 1, .95f); p.c1.set(.9f, .95f, 1, 0);
                p.size0 = .15f; p.size1 = .45f; p.max = r(.8f, 1.2f); p.grav = 1.5f; p.drag = .94f;
                break;
            case DEBRIS:
                p.vel.set(MathUtils.cos(ang) * r(2, 4.5f), r(2.5f, 6), MathUtils.sin(ang) * r(2, 4.5f));
                p.c0.set(.86f, .78f, .64f, 1); p.c1.set(.5f, .45f, .4f, 0);
                p.size0 = r(.14f, .26f); p.size1 = .1f; p.max = r(1.2f, 1.8f); p.grav = 9; p.spin = r(-600, 600);
                break;
            case CONFETTI:
                p.vel.set(MathUtils.cos(ang) * r(1, 3), r(3, 7), MathUtils.sin(ang) * r(1, 3));
                p.c0.set(Ui.PLAYERS[rnd.nextInt(Ui.PLAYERS.length)]); p.c0.a = 1;
                p.c1.set(p.c0); p.c1.a = 0;
                p.size0 = .12f; p.size1 = .1f; p.max = r(1.8f, 2.6f); p.grav = 4; p.drag = .96f; p.spin = r(-700, 700);
                break;
            case DRIP:
                p.vel.set(0, r(-.5f, 0), 0);
                p.c0.set(.5f, .75f, 1, .8f); p.c1.set(.5f, .75f, 1, 0);
                p.size0 = .06f; p.size1 = .05f; p.max = r(.6f, 1f); p.grav = 7;
                break;
            case DUST:
                p.vel.set(MathUtils.cos(ang) * r(1.5f, 3), r(.1f, .6f), MathUtils.sin(ang) * r(1.5f, 3));
                p.c0.set(.6f, .55f, .5f, .7f); p.c1.set(.5f, .48f, .45f, 0);
                p.size0 = .3f; p.size1 = .9f; p.max = r(.8f, 1.3f); p.grav = 0; p.drag = .93f;
                break;
            case SOCK:
                p.vel.set(MathUtils.cos(ang) * r(1, 2.5f), r(2, 4), MathUtils.sin(ang) * r(1, 2.5f));
                p.c0.set(Ui.PLAYERS[rnd.nextInt(Ui.PLAYERS.length)]); p.c0.a = 1;
                p.c1.set(p.c0); p.c1.a = 0;
                p.size0 = .16f; p.size1 = .16f; p.max = r(1.2f, 1.8f); p.grav = 8; p.spin = r(-400, 400);
                break;
        }
    }

    public void update(float dt) {
        shake = Math.max(0, shake - dt * 1.6f);
        for (P p : pool) {
            if (!p.alive) continue;
            p.life += dt;
            if (p.life >= p.max) { p.alive = false; continue; }
            p.vel.y -= p.grav * dt;
            p.vel.scl((float) Math.pow(p.drag, dt * 60));
            p.pos.mulAdd(p.vel, dt);
            if (p.pos.y < -.65f && p.grav > 0) { p.pos.y = -.65f; p.vel.y *= -.3f; p.vel.x *= .6f; p.vel.z *= .6f; }
            p.rot += p.spin * dt;
            float t = p.life / p.max;
            float s = MathUtils.lerp(p.size0, p.size1, t);
            tmp.set(p.c0).lerp(p.c1, t);
            p.diffuse.color.set(tmp.r, tmp.g, tmp.b, 1);
            if (p.glow) p.emissive.color.set(tmp.r, tmp.g, tmp.b, 1);
            else p.emissive.color.set(0, 0, 0, 1);
            p.blend.opacity = tmp.a;
            p.inst.transform.setToTranslation(p.pos).rotate(p.axis, p.rot).scale(s, s, s);
        }
    }

    // ------------------------------------------------------------------ props

    public ModelInstance prop(String which) {
        Model m;
        switch (which) {
            case "piano": m = piano; break;
            case "bomb": m = bomb; break;
            case "ufo": m = ufo; break;
            case "beam": m = beam; break;
            case "raccoon": m = raccoon; break;
            default: m = pile; break;
        }
        ModelInstance i = new ModelInstance(m);
        props.add(i);
        return i;
    }

    public void remove(ModelInstance i) { props.removeValue(i, true); }

    public void render(ModelBatch batch, Environment env) {
        batch.render(props, env);
        for (P p : pool) if (p.alive) batch.render(p.inst, env);
    }

    private static Material mat(String hex) { return new Material(ColorAttribute.createDiffuse(Color.valueOf(hex))); }

    private static MeshPartBuilder part(ModelBuilder mb, String id, float x, float y, float z, Material m) {
        Node n = mb.node();
        n.id = id;
        n.translation.set(x, y, z);
        return mb.part(id, GL20.GL_TRIANGLES, PN, m);
    }

    private Model keep(Model m) { models.add(m); return m; }

    private Model buildPiano(ModelBuilder mb) {
        mb.begin();
        Material black = new Material(ColorAttribute.createDiffuse(Color.valueOf("1d1a22")), ColorAttribute.createSpecular(.6f, .6f, .6f, 1));
        BoxShapeBuilder.build(part(mb, "body", 0, .75f, 0, black), 1.7f, 1.3f, .75f);
        for (int side = -1; side <= 1; side += 2) {
            BoxShapeBuilder.build(part(mb, "keys" + side, 0, .6f, side * .5f, mat("f3ead8")), 1.55f, .12f, .3f);
            for (int k = 0; k < 9; k++)
                BoxShapeBuilder.build(part(mb, "sharp" + side + k, -.64f + k * .16f, .68f, side * .45f, mat("101014")), .07f, .06f, .16f);
        }
        BoxShapeBuilder.build(part(mb, "trim", 0, 1.42f, 0, mat("e4ab4c")), 1.74f, .05f, .79f);
        for (int k = 0; k < 2; k++) CylinderShapeBuilder.build(part(mb, "leg" + k, k == 0 ? -.7f : .7f, .05f, .5f, black), .12f, .2f, .12f, 8);
        return keep(mb.end());
    }

    private Model buildBomb(ModelBuilder mb) {
        mb.begin();
        SphereShapeBuilder.build(part(mb, "ball", 0, 0, 0,
                new Material(ColorAttribute.createDiffuse(Color.valueOf("d6283b")), ColorAttribute.createSpecular(.5f, .5f, .5f, 1))), .55f, .55f, .55f, 14, 10);
        CylinderShapeBuilder.build(part(mb, "fuse", 0, .38f, 0, mat("3a7d44")), .06f, .25f, .06f, 6);
        return keep(mb.end());
    }

    private Model buildUfo(ModelBuilder mb) {
        mb.begin();
        Material steel = new Material(ColorAttribute.createDiffuse(Color.valueOf("b8bccb")), ColorAttribute.createSpecular(.9f, .9f, .9f, 1));
        SphereShapeBuilder.build(part(mb, "saucer", 0, 0, 0, steel), 2.8f, .5f, 2.8f, 24, 8);
        SphereShapeBuilder.build(part(mb, "dome", 0, .25f, 0,
                new Material(ColorAttribute.createDiffuse(Color.valueOf("7fe0c8")), ColorAttribute.createEmissive(Color.valueOf("1f5e50")),
                        new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, .75f))), 1.1f, .8f, 1.1f, 16, 10);
        Material lamp = new Material(ColorAttribute.createDiffuse(Color.valueOf("ffe27a")), ColorAttribute.createEmissive(Color.valueOf("ffe27a")));
        mb.node();
        MeshPartBuilder lights = mb.part("lights", GL20.GL_TRIANGLES, PN, lamp);
        for (int k = 0; k < 10; k++) {
            float a = k / 10f * MathUtils.PI2;
            BoxShapeBuilder.build(lights, MathUtils.cos(a) * 1.25f, -.05f, MathUtils.sin(a) * 1.25f, .14f, .1f, .14f);
        }
        return keep(mb.end());
    }

    private Model buildBeam(ModelBuilder mb) {
        mb.begin();
        Material m = new Material(ColorAttribute.createDiffuse(Color.valueOf("9effc8")), ColorAttribute.createEmissive(Color.valueOf("4cd18f")),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, .32f),
                new DepthTestAttribute(GL20.GL_LEQUAL, false), IntAttribute.createCullFace(GL20.GL_NONE));
        ConeShapeBuilder.build(part(mb, "beam", 0, -2.5f, 0, m), 2.2f, 5f, 2.2f, 20);
        return keep(mb.end());
    }

    private Model buildRaccoon(ModelBuilder mb) {
        mb.begin();
        Material fur = mat("8a8794"), dark = mat("3a3640"), light = mat("d9d6df");
        BoxShapeBuilder.build(part(mb, "body", 0, .18f, 0, fur), .5f, .3f, .3f);
        BoxShapeBuilder.build(part(mb, "head", .32f, .3f, 0, light), .24f, .22f, .26f);
        BoxShapeBuilder.build(part(mb, "mask", .42f, .32f, 0, dark), .06f, .08f, .27f);
        BoxShapeBuilder.build(part(mb, "earL", .3f, .45f, .09f, dark), .06f, .08f, .06f);
        BoxShapeBuilder.build(part(mb, "earR", .3f, .45f, -.09f, dark), .06f, .08f, .06f);
        for (int k = 0; k < 4; k++) BoxShapeBuilder.build(part(mb, "tail" + k, -.3f - k * .1f, .2f + k * .04f, 0, k % 2 == 0 ? dark : fur), .1f, .1f, .1f);
        return keep(mb.end());
    }

    private Model buildPile(ModelBuilder mb) {
        mb.begin();
        RandomXS128 r = new RandomXS128(7);
        String[] cols = {"ef6f5e", "5cc6b8", "e4ab4c", "9b7be8", "f3ead8", "6a8bd6"};
        for (int k = 0; k < 14; k++) {
            float a = r.nextFloat() * MathUtils.PI2, d = r.nextFloat() * .55f;
            BoxShapeBuilder.build(part(mb, "c" + k, MathUtils.cos(a) * d, r.nextFloat() * .45f, MathUtils.sin(a) * d, mat(cols[k % cols.length])),
                    .25f + r.nextFloat() * .2f, .12f, .18f + r.nextFloat() * .15f);
        }
        return keep(mb.end());
    }

    @Override
    public void dispose() { for (Model m : models) m.dispose(); }
}

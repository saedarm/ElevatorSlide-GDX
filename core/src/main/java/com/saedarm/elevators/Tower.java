package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Builds the whole board in 3D with ModelBuilder, so there are no model files to load.
 * Squares spiral up around a brick core, ten per lap, rising STEP each square.
 */
public final class Tower implements Disposable {
    public static final float STEP = 0.34f, RIN = 4f, ROUT = 7.6f, RMID = 5.8f, RSHAFT = 8.5f, THICK = 0.28f;
    private static final float HALF = MathUtils.PI / 10f;
    public static final float LOBBY_ANGLE = ang(0);
    public static final float TOP_Y = yOf(Board.ROOF);

    private static final long PN = Usage.Position | Usage.Normal;
    private static final long PNC = Usage.Position | Usage.Normal | Usage.ColorPacked;

    public final Array<ModelInstance> instances = new Array<>();
    private final Array<Model> models = new Array<>();
    private final Model[] tokenModels;
    private final Elevator[] elevatorAt = new Elevator[Board.ROOF + 1];
    private Texture labelTexture;
    private final ModelBuilder mb = new ModelBuilder();

    /** One instance per square (0 is the lobby) so a Cherry Bomb can blow a single tile out. */
    public final ModelInstance[] tiles = new ModelInstance[Board.ROOF + 1], labels = new ModelInstance[Board.ROOF + 1];
    private final boolean[] tileHidden = new boolean[Board.ROOF + 1];
    private ModelInstance core, shafts;
    private static final Color WINDOW = Color.valueOf("ffcf86"), WINDOW_GLOW = Color.valueOf("b98a48"),
            GLASS = Color.valueOf("8fe0d4"), GLASS_EXPRESS = Color.valueOf("ffd36b"), GLASS_DEAD = Color.valueOf("4a4a58"),
            BRASS = Color.valueOf("e4ab4c"), BRASS_GLOW = Color.valueOf("3a2606"), BRASS_DEAD = Color.valueOf("6b5a3a");

    public static final class Elevator {
        public final float bx, bz, y0, y1, deg;
        public final ModelInstance car;

        Elevator(float bx, float bz, float y0, float y1, float deg, ModelInstance car) {
            this.bx = bx; this.bz = bz; this.y0 = y0; this.y1 = y1; this.deg = deg; this.car = car;
            setCarY(y0);
        }

        public void setCarY(float y) { car.transform.setToTranslation(bx, y, bz).rotate(Vector3.Y, deg); }
    }

    public static float ang(int i) { return -(i - 1) / 10f * MathUtils.PI2; }

    public static float yOf(int i) { return (i - 1) * STEP; }

    public Tower(BitmapFont labelFont, Color[] playerColors) {
        buildGround();
        buildSkyline();
        buildCore();
        buildTiles();
        buildLabels(labelFont);
        buildElevators();
        buildChutes();
        tokenModels = new Model[playerColors.length];
        for (int i = 0; i < playerColors.length; i++) tokenModels[i] = buildToken(playerColors[i]);
    }

    // ---------- queries used by Match ----------

    public static float angleOf(int sq) { return sq == 0 ? LOBBY_ANGLE : ang(sq); }

    public static float facingDeg(int sq) { return -angleOf(sq) * MathUtils.radDeg; }

    public static Vector3 tilePos(int sq, Vector3 out) {
        if (sq == 0) return out.set(MathUtils.cos(LOBBY_ANGLE) * RMID, yOf(0), MathUtils.sin(LOBBY_ANGLE) * RMID);
        float a = ang(sq);
        return out.set(MathUtils.cos(a) * RMID, yOf(sq), MathUtils.sin(a) * RMID);
    }

    public Elevator elevator(int sq) { return elevatorAt[sq]; }


    public ModelInstance newToken(int colorIndex) { return new ModelInstance(tokenModels[colorIndex]); }

    /**
     * Point along chute ci at t in [0,1]. Every chute drops straight down its own column of the
     * tower (from and to are a multiple of 10 apart), bulging outward and swinging toward the lower
     * squares so it never clips the floor above or an elevator shaft in the same column.
     */
    public static Vector3 chutePoint(int ci, float t, Vector3 out) {
        int from = Board.CHUTES[ci][0], to = Board.CHUTES[ci][1];
        float floors = (from - to) / 10f;
        float bulge = 3.2f + Math.min(3f, floors) * .9f + (ci % 3) * .45f;
        float swing = .5f + .12f * Math.min(3f, floors);
        float s = MathUtils.sin(MathUtils.PI * t);
        float th = ang(from) + swing * (float) Math.sqrt(s);
        float r = RMID + bulge * (float) Math.pow(s, .35);
        float y = yOf(from) + .35f + (yOf(to) - yOf(from)) * Seq.smooth(t) * .9f + (yOf(to) - yOf(from)) * t * .1f;
        return out.set(MathUtils.cos(th) * r, y, MathUtils.sin(th) * r);
    }

    public static float chuteSeconds(int ci) {
        float floors = (Board.CHUTES[ci][0] - Board.CHUTES[ci][1]) / 10f;
        return Math.min(3f, 1.1f + floors * .5f);
    }

    // ---------- live changes from cards and events ----------

    public void render(ModelBatch batch, Environment env) {
        batch.render(instances, env);
        for (int i = 0; i <= Board.ROOF; i++) {
            if (tileHidden[i]) continue;
            batch.render(tiles[i], env);
            batch.render(labels[i], env);
        }
    }

    public void hideTile(int sq, boolean hidden) {
        tileHidden[sq] = hidden;
        if (!hidden) { tiles[sq].transform.idt(); labels[sq].transform.idt(); }
    }

    /** Spins a tile (and its number) away from its spot; t runs 0 to 1. */
    public void flyTile(int sq, float t) {
        Vector3 c = tilePos(sq, new Vector3());
        float a = angleOf(sq);
        float out = t * 5f, up = t * 6f - t * t * 9f;
        Matrix4 m = tiles[sq].transform;
        m.idt().translate(c.x + MathUtils.cos(a) * out, c.y + up, c.z + MathUtils.sin(a) * out)
                .rotate(MathUtils.sin(a), .4f, -MathUtils.cos(a), t * 540f).scale(1 - t * .6f, 1 - t * .6f, 1 - t * .6f)
                .translate(-c.x, -c.y, -c.z);
        labels[sq].transform.set(m);
    }

    private boolean power = true, express, cableBroken;

    public void setPower(boolean on) { power = on; refreshLights(); }

    public void setExpress(boolean on) { express = on; refreshLights(); }

    public void setCableBroken(boolean broken) { cableBroken = broken; refreshLights(); }

    private void refreshLights() {
        Material w = core.getMaterial("coreWindows");
        ((ColorAttribute) w.get(ColorAttribute.Diffuse)).color.set(power ? WINDOW : Color.valueOf("2a2840"));
        ((ColorAttribute) w.get(ColorAttribute.Emissive)).color.set(power ? WINDOW_GLOW : Color.BLACK);
        boolean dead = !power || cableBroken;
        Material g = shafts.getMaterial("glass");
        ((ColorAttribute) g.get(ColorAttribute.Diffuse)).color.set(dead ? GLASS_DEAD : express ? GLASS_EXPRESS : GLASS);
        ((BlendingAttribute) g.get(BlendingAttribute.Type)).opacity = dead ? .1f : express ? .34f : .16f;
        setBrass(shafts.getMaterial("brass"), dead);
        for (Elevator e : elevatorAt) if (e != null) setBrass(e.car.getMaterial("brass"), dead);
    }

    private static void setBrass(Material m, boolean dead) {
        ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(dead ? BRASS_DEAD : BRASS);
        ((ColorAttribute) m.get(ColorAttribute.Emissive)).color.set(dead ? Color.BLACK : BRASS_GLOW);
    }

    /** Top of an elevator shaft, for sparks when the cable snaps. */
    public static Vector3 shaftTop(int[] elevator, Vector3 out) {
        float a = ang(elevator[0]);
        return out.set(MathUtils.cos(a) * RSHAFT, yOf(elevator[1]) + 1.2f, MathUtils.sin(a) * RSHAFT);
    }

    /** A random spot on a floor's ramp, for fire and drips. */
    public static Vector3 randomOnFloor(int floor, Vector3 out) {
        int sq = (floor - 1) * 10 + 1 + MathUtils.random(9);
        float a = ang(sq) + MathUtils.random(-HALF, HALF);
        float r = MathUtils.random(RIN + .3f, ROUT - .2f);
        return out.set(MathUtils.cos(a) * r, yOf(sq) + .05f, MathUtils.sin(a) * r);
    }

    // ---------- geometry helpers ----------

    private static Vector3 polar(float r, float a, float y) {
        return new Vector3(MathUtils.cos(a) * r, y, MathUtils.sin(a) * r);
    }

    private static final Vector3 T1 = new Vector3(), T2 = new Vector3();

    /** Adds a quad, flipping the winding if needed so it faces along n. */
    private static void quad(MeshPartBuilder b, Vector3 p00, Vector3 p10, Vector3 p11, Vector3 p01, Vector3 n) {
        T1.set(p10).sub(p00);
        T2.set(p11).sub(p00);
        if (T1.crs(T2).dot(n) >= 0) b.rect(p00, p10, p11, p01, n);
        else b.rect(p00, p01, p11, p10, n);
    }

    /** A ring-sector slab: one square of the spiral. */
    private static void wedge(MeshPartBuilder b, float r0, float r1, float a0, float a1, float yTop, float th, Color c) {
        b.setColor(c);
        float yb = yTop - th;
        int n = 6;
        for (int k = 0; k < n; k++) {
            float a = MathUtils.lerp(a0, a1, k / (float) n), a2 = MathUtils.lerp(a0, a1, (k + 1) / (float) n);
            quad(b, polar(r0, a, yTop), polar(r1, a, yTop), polar(r1, a2, yTop), polar(r0, a2, yTop), Vector3.Y);
            quad(b, polar(r0, a, yb), polar(r1, a, yb), polar(r1, a2, yb), polar(r0, a2, yb), new Vector3(0, -1, 0));
            float am = (a + a2) / 2;
            quad(b, polar(r1, a, yb), polar(r1, a2, yb), polar(r1, a2, yTop), polar(r1, a, yTop),
                    new Vector3(MathUtils.cos(am), 0, MathUtils.sin(am)));
        }
        quad(b, polar(r0, a0, yb), polar(r1, a0, yb), polar(r1, a0, yTop), polar(r0, a0, yTop),
                new Vector3(MathUtils.sin(a0), 0, -MathUtils.cos(a0)));
        quad(b, polar(r0, a1, yb), polar(r1, a1, yb), polar(r1, a1, yTop), polar(r0, a1, yTop),
                new Vector3(-MathUtils.sin(a1), 0, MathUtils.cos(a1)));
    }

    private static Material mat(String hex) { return new Material(ColorAttribute.createDiffuse(Color.valueOf(hex))); }

    private MeshPartBuilder partAt(String id, float x, float y, float z, Material m) {
        Node node = mb.node();
        node.id = id;
        node.translation.set(x, y, z);
        return mb.part(id, GL20.GL_TRIANGLES, PN, m);
    }

    private ModelInstance add(Model m) {
        models.add(m);
        ModelInstance i = new ModelInstance(m);
        instances.add(i);
        return i;
    }

    // ---------- scene pieces ----------

    private void buildGround() {
        mb.begin();
        CylinderShapeBuilder.build(partAt("street", 0, -.72f, 0, mat("262a4a")), 160, .04f, 160, 48);
        CylinderShapeBuilder.build(partAt("sidewalk", 0, -.69f, 0, mat("3b3f60")), 26, .04f, 26, 48);
        add(mb.end());
    }

    private void buildSkyline() {
        RandomXS128 rnd = new RandomXS128(1997);
        int count = 60;
        Matrix4[] xf = new Matrix4[count];
        float[] ws = new float[count], hs = new float[count];

        mb.begin();
        mb.node();
        MeshPartBuilder b = mb.part("blocks", GL20.GL_TRIANGLES, PNC, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        Color c = new Color();
        for (int k = 0; k < count; k++) {
            float r = 32 + rnd.nextFloat() * 32, a = rnd.nextFloat() * MathUtils.PI2;
            float h = 3 + rnd.nextFloat() * 16, w = 2 + rnd.nextFloat() * 3.5f;
            ws[k] = w; hs[k] = h;
            xf[k] = new Matrix4().setToTranslation(MathUtils.cos(a) * r, h / 2 - .7f, MathUtils.sin(a) * r)
                    .rotate(Vector3.Y, -a * MathUtils.radDeg);
            b.setVertexTransform(xf[k]);
            float v = .16f + rnd.nextFloat() * .08f;
            b.setColor(c.set(v, v + .02f, v + .17f, 1));
            BoxShapeBuilder.build(b, w, h, w);
        }
        b.setVertexTransformationEnabled(false);
        // lit windows on the face that looks toward the tower (local -X)
        Material lit = new Material(ColorAttribute.createDiffuse(Color.valueOf("ffcf86")),
                ColorAttribute.createEmissive(Color.valueOf("c99a50")));
        MeshPartBuilder wb = mb.part("windows", GL20.GL_TRIANGLES, PN, lit);
        Vector3 n = new Vector3(-1, 0, 0);
        for (int k = 0; k < count; k++) {
            wb.setVertexTransform(xf[k]);
            float w = ws[k], h = hs[k], x = -w / 2 - .02f;
            for (float y = -h / 2 + .7f; y < h / 2 - .5f; y += 1.1f) {
                for (float z = -w / 2 + .45f; z < w / 2 - .3f; z += .8f) {
                    if (rnd.nextFloat() > .38f) continue;
                    quad(wb, new Vector3(x, y, z), new Vector3(x, y, z + .35f), new Vector3(x, y + .5f, z + .35f),
                            new Vector3(x, y + .5f, z), n);
                }
            }
        }
        add(mb.end());
    }

    private void buildCore() {
        float h = TOP_Y + 1.4f;
        mb.begin();
        CylinderShapeBuilder.build(partAt("core", 0, h / 2 - .7f, 0, mat("9c4a3a")), RIN * 2, h, RIN * 2, 40);
        Material cream = mat("e8dcc4");
        for (int f = 1; f <= 10; f++) {
            CylinderShapeBuilder.build(partAt("cornice" + f, 0, yOf((f - 1) * 10 + 1) - .2f, 0, cream),
                    (RIN + .12f) * 2, .08f, (RIN + .12f) * 2, 40);
        }
        float roofY = h - .7f;
        CylinderShapeBuilder.build(partAt("roof", 0, roofY + .1f, 0, mat("4a3b3b")), (RIN + .5f) * 2, .3f, (RIN + .5f) * 2, 40);
        // water tower
        Material wood = mat("8a5a3b"), steel = mat("5b5d6e"), legs = mat("2d2f45");
        CylinderShapeBuilder.build(partAt("tank", 0, roofY + 2.5f, 0, wood), 2.6f, 2f, 2.6f, 20);
        ConeShapeBuilder.build(partAt("cap", 0, roofY + 4f, 0, steel), 2.9f, 1f, 2.9f, 20);
        for (int k = 0; k < 4; k++) {
            float a = k * MathUtils.HALF_PI + MathUtils.PI / 4;
            CylinderShapeBuilder.build(partAt("leg" + k, MathUtils.cos(a), roofY + .9f, MathUtils.sin(a), legs), .16f, 1.6f, .16f, 6);
        }
        // lit windows on the core
        Material lit = new Material("coreWindows", ColorAttribute.createDiffuse(WINDOW), ColorAttribute.createEmissive(WINDOW_GLOW));
        mb.node();
        MeshPartBuilder wb = mb.part("coreWindows", GL20.GL_TRIANGLES, PN, lit);
        RandomXS128 rnd = new RandomXS128(1958);
        for (int f = 0; f < 10; f++) {
            for (int k = 0; k < 24; k++) {
                if (rnd.nextFloat() > .55f) continue;
                float a = k / 24f * MathUtils.PI2, a2 = a + .07f, r = RIN + .02f;
                float y = f * 10 * STEP + 1.1f;
                Vector3 nrm = new Vector3(MathUtils.cos(a + .035f), 0, MathUtils.sin(a + .035f));
                quad(wb, polar(r, a, y), polar(r, a2, y), polar(r, a2, y + .55f), polar(r, a, y + .55f), nrm);
            }
        }
        core = add(mb.end());
    }

    private static Color tileColor(int i) {
        if (i == Board.ROOF) return Color.valueOf("e4ab4c");
        switch (Board.kindAt(i)) {
            case ELEVATOR: return Color.valueOf("f0c46e");
            case CHUTE: return Color.valueOf("f19385");
            default:
                if (Board.isMailbox(i)) return Color.valueOf("bfe3dc");
                return Color.valueOf(Board.floorOf(i) % 2 == 1 ? "e8dcc4" : "cdbfa6");
        }
    }

    private void buildTiles() {
        mb.begin();
        Material m = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        Color post = Color.valueOf("2d2f45"), box = Color.valueOf("5c7fd6");
        for (int i = 0; i <= Board.ROOF; i++) {
            Node n = mb.node();
            n.id = "t" + i;
            MeshPartBuilder b = mb.part("t" + i, GL20.GL_TRIANGLES, PNC, m);
            if (i == 0) {
                wedge(b, RIN - .02f, ROUT + 1.2f, LOBBY_ANGLE - HALF * 1.3f, LOBBY_ANGLE + HALF * 1.3f, yOf(0), THICK, Color.valueOf("5a6aa8"));
                continue;
            }
            float a = ang(i);
            wedge(b, RIN - .02f, ROUT, a - HALF + .025f, a + HALF - .025f, yOf(i), THICK, tileColor(i));
            b.setColor(post);
            Vector3 p = polar(ROUT - .1f, a - HALF + .04f, yOf(i) + .25f);
            BoxShapeBuilder.build(b, p.x, p.y, p.z, .07f, .5f, .07f);
            if (Board.isMailbox(i)) {
                Vector3 q = polar(ROUT - .35f, a + HALF * .6f, yOf(i));
                BoxShapeBuilder.build(b, q.x, q.y + .2f, q.z, .06f, .4f, .06f);
                b.setColor(box);
                BoxShapeBuilder.build(b, q.x, q.y + .5f, q.z, .3f, .24f, .24f);
            }
        }
        Model model = mb.end();
        models.add(model);
        for (int i = 0; i <= Board.ROOF; i++) tiles[i] = new ModelInstance(model, "t" + i);
    }

    /** Draws every square's number into one texture, then lays a flat quad on each tile. */
    private void buildLabels(BitmapFont font) {
        int cell = 128, cols = 11, rows = 10, w = cols * cell, h = rows * cell;
        FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        SpriteBatch sb = new SpriteBatch();
        sb.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        GlyphLayout layout = new GlyphLayout();
        fb.begin();
        Gdx.gl.glClearColor(1, 1, 1, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        sb.begin();
        sb.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        font.setColor(Color.WHITE);
        for (int i = 0; i <= Board.ROOF; i++) {
            String txt = i == 0 ? "LOBBY" : i == Board.ROOF ? "ROOF" : String.valueOf(i);
            float s = txt.length() > 3 ? .48f : 1f;
            font.getData().setScale(s);
            layout.setText(font, txt);
            float cx = (i % cols) * cell + cell / 2f, cy = (i / cols) * cell + cell / 2f;
            font.draw(sb, layout, cx - layout.width / 2, cy + layout.height / 2);
        }
        font.getData().setScale(1f);
        sb.end();
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, w, h);
        fb.end();
        fb.dispose();
        sb.dispose();
        labelTexture = new Texture(new PixmapTextureData(pm, null, true, false, true));
        labelTexture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        Material m = new Material(TextureAttribute.createDiffuse(labelTexture),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE));
        mb.begin();
        Color ink = Color.valueOf("2a2340");
        for (int i = 0; i <= Board.ROOF; i++) {
            Node node = mb.node();
            node.id = "l" + i;
            MeshPartBuilder b = mb.part("l" + i, GL20.GL_TRIANGLES, PNC | Usage.TextureCoordinates, m);
            float a = i == 0 ? LOBBY_ANGLE : ang(i);
            float r = i == 0 ? RMID + 1.6f : RMID + .9f, half = i == 0 ? .85f : .72f;
            Vector3 c = polar(r, a, (i == 0 ? yOf(0) : yOf(i)) + .012f);
            // Readable from outside the tower: text "up" points inward, text "right" is the tangent.
            Vector3 right = new Vector3(MathUtils.sin(a), 0, -MathUtils.cos(a)).scl(half);
            Vector3 up = new Vector3(-MathUtils.cos(a), 0, -MathUtils.sin(a)).scl(half);
            float u0 = (i % cols) * cell / (float) w, u1 = u0 + cell / (float) w;
            float v0 = (i / cols) * cell / (float) h, v1 = v0 + cell / (float) h;
            short bl = b.vertex(c.cpy().sub(right).sub(up), Vector3.Y, ink, new com.badlogic.gdx.math.Vector2(u0, v0));
            short br = b.vertex(c.cpy().add(right).sub(up), Vector3.Y, ink, new com.badlogic.gdx.math.Vector2(u1, v0));
            short tr = b.vertex(c.cpy().add(right).add(up), Vector3.Y, ink, new com.badlogic.gdx.math.Vector2(u1, v1));
            short tl = b.vertex(c.cpy().sub(right).add(up), Vector3.Y, ink, new com.badlogic.gdx.math.Vector2(u0, v1));
            b.rect(bl, br, tr, tl);
        }
        Model model = mb.end();
        models.add(model);
        for (int i = 0; i <= Board.ROOF; i++) labels[i] = new ModelInstance(model, "l" + i);
    }

    private void buildElevators() {
        Material glass = new Material("glass", ColorAttribute.createDiffuse(GLASS),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, .16f),
                new DepthTestAttribute(GL20.GL_LEQUAL, false));
        Material brass = new Material("brass", ColorAttribute.createDiffuse(BRASS),
                ColorAttribute.createEmissive(BRASS_GLOW),
                ColorAttribute.createSpecular(Color.valueOf("ffe2a6")));

        // shared car model: floor, roof and four posts
        mb.begin();
        mb.node();
        MeshPartBuilder cb = mb.part("car", GL20.GL_TRIANGLES, PN, brass);
        BoxShapeBuilder.build(cb, 0, 0, 0, .9f, .08f, .9f);
        BoxShapeBuilder.build(cb, 0, 1f, 0, .9f, .08f, .9f);
        for (float dx = -.42f; dx <= .43f; dx += .84f)
            for (float dz = -.42f; dz <= .43f; dz += .84f)
                BoxShapeBuilder.build(cb, dx, .5f, dz, .05f, 1f, .05f);
        Model carModel = mb.end();
        models.add(carModel);

        mb.begin();
        for (int[] e : Board.ELEVATORS) {
            float a = ang(e[0]), deg = -a * MathUtils.radDeg;
            float bx = MathUtils.cos(a) * RSHAFT, bz = MathUtils.sin(a) * RSHAFT;
            float y0 = yOf(e[0]), y1 = yOf(e[1]) + 1.2f, h = y1 - y0;

            Node n = mb.node();
            n.id = "shaft" + e[0];
            n.translation.set(bx, y0 + h / 2, bz);
            n.rotation.set(Vector3.Y, deg);
            BoxShapeBuilder.build(mb.part("glass" + e[0], GL20.GL_TRIANGLES, PN, glass), 1f, h, 1f);
            MeshPartBuilder rails = mb.part("rails" + e[0], GL20.GL_TRIANGLES, PN, brass);
            for (float dx = -.5f; dx <= .51f; dx += 1f)
                for (float dz = -.5f; dz <= .51f; dz += 1f)
                    BoxShapeBuilder.build(rails, dx, 0, dz, .06f, h, .06f);
            BoxShapeBuilder.build(rails, 0, h / 2, 0, 1.15f, .18f, 1.15f);

            ModelInstance car = new ModelInstance(carModel);
            instances.add(car);
            elevatorAt[e[0]] = new Elevator(bx, bz, y0, yOf(e[1]), deg, car);
        }
        shafts = add(mb.end());
    }

    private void buildChutes() {
        String[] cols = {"ef6f5e", "f08aa0", "e9785a", "d9647f", "f2a07b", "c95c74", "ee8466", "e86f91"};
        Vector3 p = new Vector3(), pa = new Vector3(), pb = new Vector3(), tan = new Vector3(), side = new Vector3(),
                up = new Vector3(), nrm = new Vector3();
        int ringN = 10;
        float radius = .32f;
        for (int ci = 0; ci < Board.CHUTES.length; ci++) {
            float laps = (Board.CHUTES[ci][0] - Board.CHUTES[ci][1]) / 10f;
            int segs = Math.max(36, Math.round(laps * 24));
            mb.begin();
            mb.node();
            MeshPartBuilder b = mb.part("chute" + ci, GL20.GL_TRIANGLES, PNC,
                    new Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createSpecular(.25f, .25f, .25f, 1),
                            IntAttribute.createCullFace(GL20.GL_NONE)));
            Color col = Color.valueOf(cols[ci]);
            short[][] idx = new short[segs + 1][ringN];
            Vector3[][] pos = new Vector3[segs + 1][ringN];
            for (int s = 0; s <= segs; s++) {
                float t = s / (float) segs;
                chutePoint(ci, t, p);
                chutePoint(ci, Math.min(1, t + .002f), pb);
                chutePoint(ci, Math.max(0, t - .002f), pa);
                tan.set(pb).sub(pa).nor();
                side.set(tan).crs(Vector3.Y).nor();
                up.set(side).crs(tan).nor();
                for (int k = 0; k < ringN; k++) {
                    float phi = k / (float) ringN * MathUtils.PI2;
                    nrm.set(side).scl(MathUtils.cos(phi)).mulAdd(up, MathUtils.sin(phi));
                    pos[s][k] = new Vector3(p).mulAdd(nrm, radius);
                    idx[s][k] = b.vertex(pos[s][k], nrm, col, null);
                }
            }
            // cap both ends so you never see into the tube
            for (int end = 0; end <= 1; end++) {
                int s = end == 0 ? 0 : segs;
                chutePoint(ci, end, p);
                chutePoint(ci, end == 0 ? .01f : .99f, pb);
                nrm.set(p).sub(pb).nor();
                short center = b.vertex(p, nrm, col, null);
                for (int k = 0; k < ringN; k++) {
                    int k2 = (k + 1) % ringN;
                    T1.set(pos[s][k]).sub(p);
                    T2.set(pos[s][k2]).sub(p);
                    if (T1.crs(T2).dot(nrm) >= 0) b.triangle(center, idx[s][k], idx[s][k2]);
                    else b.triangle(center, idx[s][k2], idx[s][k]);
                }
            }
            for (int s = 0; s < segs; s++) {
                for (int k = 0; k < ringN; k++) {
                    int k2 = (k + 1) % ringN;
                    // outward normal at this patch is roughly (patch center - spine point)
                    T1.set(pos[s + 1][k]).sub(pos[s][k]);
                    T2.set(pos[s + 1][k2]).sub(pos[s][k]);
                    chutePoint(ci, (s + .5f) / segs, p);
                    nrm.set(pos[s][k]).add(pos[s + 1][k2]).scl(.5f).sub(p);
                    if (T1.crs(T2).dot(nrm) >= 0) b.rect(idx[s][k], idx[s + 1][k], idx[s + 1][k2], idx[s][k2]);
                    else b.rect(idx[s][k], idx[s][k2], idx[s + 1][k2], idx[s + 1][k]);
                }
            }
            add(mb.end());
        }
    }

    private Model buildToken(Color color) {
        Material body = new Material(ColorAttribute.createDiffuse(color), ColorAttribute.createSpecular(.3f, .3f, .3f, 1));
        mb.begin();
        CylinderShapeBuilder.build(partAt("body", 0, .25f, 0, body), .46f, .5f, .46f, 14);
        SphereShapeBuilder.build(partAt("head", 0, .64f, 0, body), .34f, .34f, .34f, 16, 12);
        BoxShapeBuilder.build(partAt("basket", .24f, .34f, 0, mat("f3ead8")), .22f, .14f, .14f);
        Model m = mb.end();
        models.add(m);
        return m;
    }

    @Override
    public void dispose() {
        for (Model m : models) m.dispose();
        if (labelTexture != null) labelTexture.dispose();
    }
}

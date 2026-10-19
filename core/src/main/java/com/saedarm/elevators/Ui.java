package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Fonts, colors and drawables, all generated at startup so there is no skin file.
 * Sizes are in dp; fonts are rasterized at the phone's real pixel density so text stays sharp on the S23.
 */
public final class Ui implements Disposable {
    public static final Color SKY = Color.valueOf("1c2142");
    public static final Color PANEL = Color.valueOf("181c3cea");
    public static final Color CARD = Color.valueOf("232853f5");
    public static final Color LINE = Color.valueOf("f3ead833");
    public static final Color INK = Color.valueOf("f3ead8");
    public static final Color MUTED = Color.valueOf("b9b3c9");
    public static final Color BRASS = Color.valueOf("e4ab4c");
    public static final Color CORAL = Color.valueOf("ef6f5e");
    public static final Color TEAL = Color.valueOf("5cc6b8");
    public static final Color DARK = Color.valueOf("2a2340");
    public static final Color[] PLAYERS = {CORAL, TEAL, BRASS, Color.valueOf("9b7be8")};

    private static final String CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "…·—–’";

    public final float scale;
    public final BitmapFont title, heading, chip, dieFont, number, body, small, label3d;
    public final Drawable panel, panelActive, card, button, buttonDown, brass, brassDown, dieFace, field, cursor, selection, dot;

    private final Array<Texture> textures = new Array<>();
    private final Array<BitmapFont> fonts = new Array<>();

    public Ui() {
        scale = Math.max(1f, Gdx.graphics.getDensity());
        FreeTypeFontGenerator display = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Bungee-Regular.ttf"));
        FreeTypeFontGenerator text = new FreeTypeFontGenerator(Gdx.files.internal("fonts/BricolageGrotesque.ttf"));
        title = font(display, 27, false);
        heading = font(display, 16, false);
        chip = font(display, 12, false);
        dieFont = font(display, 32, false);
        number = font(display, 15, false);
        body = font(text, 15, true);
        small = font(text, 12.5f, true);
        // Unscaled pixel font for the number texture on the tiles.
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = 70;
        p.characters = CHARS;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        label3d = display.generateFont(p);
        fonts.add(label3d);
        display.dispose();
        text.dispose();

        panel = box(PANEL, LINE, 8, 1);
        panelActive = box(PANEL, BRASS, 8, 1.5f);
        card = box(CARD, LINE, 12, 1);
        button = box(Color.valueOf("262b58"), LINE, 10, 1.5f);
        buttonDown = box(Color.valueOf("30366a"), BRASS, 10, 1.5f);
        brass = box(BRASS, BRASS, 10, 0);
        brassDown = box(Color.valueOf("c98f33"), Color.valueOf("c98f33"), 10, 0);
        dieFace = box(INK, Color.valueOf("b9ad95"), 12, 3);
        field = box(Color.valueOf("1b1f43"), LINE, 6, 1);
        TextureRegionDrawable solid = new TextureRegionDrawable(new TextureRegion(texture(solidPixmap())));
        cursor = solid.tint(INK);
        cursor.setMinWidth(2);
        selection = solid.tint(Color.valueOf("e4ab4c66"));
        TextureRegionDrawable circle = new TextureRegionDrawable(new TextureRegion(texture(circlePixmap(Math.round(24 * scale)))));
        circle.setMinSize(14, 14);
        dot = circle;
    }

    private BitmapFont font(FreeTypeFontGenerator g, float dp, boolean markup) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.round(dp * scale);
        p.characters = CHARS;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.kerning = true;
        BitmapFont f = g.generateFont(p);
        f.getData().setScale(1f / scale);
        f.getData().markupEnabled = markup;
        f.setUseIntegerPositions(false);
        fonts.add(f);
        return f;
    }

    private Texture texture(Pixmap pm) {
        Texture t = new Texture(pm);
        pm.dispose();
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textures.add(t);
        return t;
    }

    private static Pixmap solidPixmap() {
        Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        return pm;
    }

    private static Pixmap circlePixmap(int r) {
        Pixmap pm = new Pixmap(r * 2 + 2, r * 2 + 2, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fillCircle(r + 1, r + 1, r);
        return pm;
    }

    private static void rounded(Pixmap pm, int x, int y, int w, int h, int r) {
        pm.fillRectangle(x + r, y, w - 2 * r, h);
        pm.fillRectangle(x, y + r, w, h - 2 * r);
        pm.fillCircle(x + r, y + r, r);
        pm.fillCircle(x + w - r - 1, y + r, r);
        pm.fillCircle(x + r, y + h - r - 1, r);
        pm.fillCircle(x + w - r - 1, y + h - r - 1, r);
    }

    /** Rounded box with an optional border, as a nine-patch scaled back to dp. */
    private Drawable box(Color fill, Color border, float radiusDp, float borderDp) {
        int r = Math.max(2, Math.round(radiusDp * scale));
        int b = Math.round(borderDp * scale);
        int s = r * 2 + 4;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        if (b > 0) {
            pm.setColor(border);
            rounded(pm, 0, 0, s, s, r);
        }
        pm.setColor(fill);
        rounded(pm, b, b, s - 2 * b, s - 2 * b, Math.max(1, r - b));
        NinePatch np = new NinePatch(new TextureRegion(texture(pm)), r + 1, r + 1, r + 1, r + 1);
        np.scale(1f / scale, 1f / scale);
        return new NinePatchDrawable(np);
    }

    /** "[#rrggbbaa]" for inline color markup. */
    public static String hex(Color c) { return "[#" + c.toString() + "]"; }

    /** Player names go into markup strings, so escape the bracket. */
    public static String safe(String s) { return s.replace("[", "[["); }

    @Override
    public void dispose() {
        for (BitmapFont f : fonts) f.dispose();
        for (Texture t : textures) t.dispose();
    }
}

package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Scaling;

/** The title screen, How to Play, Settings and the pause menu. Hud decides which one is showing. */
public final class Menus implements Disposable {
    public interface Nav {
        void play();
        void howToPlay();
        void settings();
        void quit();
        void resume();
        void quitToMenu();
        void back();
        void settingsChanged();
    }

    public static final String VERSION = "1.0";

    public final Table title = new Table(), how = new Table(), settings = new Table(), pause = new Table();
    private final Ui ui;
    private final Audio audio;
    private final Settings prefs;
    private final Nav nav;
    private final Array<Texture> pictures = new Array<>();
    private final Array<Cell<?>> cardCells = new Array<>();

    private static final String[][] PAGES = {
            {"1-roof.png", "RACE TO THE ROOF",
                    "A hundred squares spiral up the tower, ten per floor. First tenant to land on exactly 100 wins. "
                            + "Roll past it and you bounce back down the extra."},
            {"2-pace.png", "PICK YOUR PACE",
                    "Every turn, choose how you move. Take the stairs and move 3 to 6, or hustle and move 1 to 8. "
                            + "Both average the same speed. The buttons show your real chance of going up or down from where you stand, so read them before you tap."},
            {"3-ups-downs.png", "ELEVATORS AND CHUTES",
                    "Land on the bottom of a brass shaft and the elevator takes you up. Land on the top of a coral tube "
                            + "and you slide down the laundry chute. Squares stack straight up, so 34 sits right above 24, 14 and 4."},
            {"4-cards.png", "MAILBOXES AND CARDS",
                    "Blue tiles are mailboxes. Land on one to draw a card, and you get one for falling down a chute too. "
                            + "Hold up to 3 and play one per turn before you roll. Automatic cards, like the Spare Key, work on their own."},
            {"5-chaos.png", "CHAOS",
                    "Chaos cards hit an area: bombs leave holes, fires burn whole floors, pianos fall down a column. "
                            + "The card panel tells you who gets hit before you play it, including you. A Fire Extinguisher blocks one hit."},
            {"6-events.png", "BUILDING EVENTS",
                    "Every 5 rounds something happens to the whole tower: a fire drill, a power outage, express elevators, "
                            + "a wet floor, a laundry pileup or rent day. The line under the tenants shows what's active."}};
    private int page;
    private Image pageImage;
    private Label pageTitle, pageText, pageCount;
    private TextButton prev, next;
    private Label musicValue, sfxValue;

    public Menus(Ui ui, Audio audio, Settings prefs, Nav nav) {
        this.ui = ui;
        this.audio = audio;
        this.prefs = prefs;
        this.nav = nav;
        buildTitle();
        buildHow();
        buildSettings();
        buildPause();
        for (Table t : new Table[]{title, how, settings, pause}) {
            t.setFillParent(true);
            t.setVisible(false);
        }
    }

    // ---------- helpers ----------

    private static Label label(String text, BitmapFont font, Color color) {
        return new Label(text, new Label.LabelStyle(font, color));
    }

    private ChangeListener click(Runnable r) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                audio.play("ui_click", .6f, 1f);
                r.run();
            }
        };
    }

    private TextButton button(String text, boolean primary, Runnable r) {
        TextButton b = new TextButton(text, primary ? ui.brassButton() : ui.plainButton(ui.heading));
        b.addListener(click(r));
        return b;
    }

    private Table card() {
        Table c = new Table();
        c.setBackground(ui.card);
        c.pad(22, 20, 22, 20);
        c.defaults().growX();
        return c;
    }

    // ---------- title ----------

    private void buildTitle() {
        Table logo = new Table();
        logo.setBackground(ui.panel);
        logo.pad(18, 22, 18, 22);
        Label a = label("ELEVATORS &", ui.logo, Ui.INK);
        Label b = label("LAUNDRY CHUTES", ui.logo, Ui.INK);
        a.setWrap(true);
        b.setWrap(true);
        a.setAlignment(Align.center);
        b.setAlignment(Align.center);
        logo.add(a).growX().row();
        logo.add(b).growX().row();
        Label sub = label("A RACE TO THE ROOF", ui.heading, Ui.BRASS);
        sub.setAlignment(Align.center);
        logo.add(sub).growX().padTop(8);

        Table buttons = new Table();
        buttons.defaults().growX().height(52).padTop(10);
        buttons.add(button("PLAY", true, nav::play)).height(60).row();
        buttons.add(button("HOW TO PLAY", false, nav::howToPlay)).row();
        buttons.add(button("SETTINGS", false, nav::settings)).row();
        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.WebGL) {
            buttons.add(button("QUIT", false, nav::quit)).row();
        }
        Label foot = label("v" + VERSION + "  ·  music and sounds made in code", ui.small, Ui.MUTED);
        foot.setAlignment(Align.center);

        title.top();
        cardCells.add(title.add(logo).padTop(36));
        title.row();
        title.add().expandY().row();
        cardCells.add(title.add(buttons));
        title.row();
        title.add(foot).padTop(14);
    }

    // ---------- how to play ----------

    private void buildHow() {
        for (String[] p : PAGES) {
            Texture t = new Texture(Gdx.files.internal("howto/" + p[0]), true);
            t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            pictures.add(t);
        }
        Table c = card();
        Table head = new Table();
        head.add(label("HOW TO PLAY", ui.chip, Ui.BRASS)).left().expandX();
        pageCount = label("", ui.chip, Ui.MUTED);
        head.add(pageCount).right();
        c.add(head).row();
        pageImage = new Image();
        pageImage.setScaling(Scaling.fit);
        c.add(pageImage).height(190).growX().minWidth(0).padTop(10).row();
        pageTitle = label("", ui.title, Ui.INK);
        pageTitle.setWrap(true);
        c.add(pageTitle).padTop(12).minWidth(0).row();
        pageText = label("", ui.body, Ui.MUTED);
        pageText.setWrap(true);
        c.add(pageText).padTop(6).minHeight(110).minWidth(0).top().row();
        Table navRow = new Table();
        prev = new TextButton("BACK", ui.plainButton(ui.heading));
        prev.addListener(click(() -> { if (page == 0) nav.back(); else showPage(page - 1); }));
        next = new TextButton("NEXT", ui.brassButton());
        next.addListener(click(() -> { if (page == PAGES.length - 1) nav.back(); else showPage(page + 1); }));
        navRow.add(prev).growX().uniformX().height(50);
        navRow.add(next).growX().uniformX().height(50).padLeft(10);
        c.add(navRow).padTop(14);
        ScrollPane sp = new ScrollPane(c);
        sp.setOverscroll(false, true);
        sp.setScrollingDisabled(true, false);
        how.center();
        cardCells.add(how.add(sp));
    }

    public void openHow() {
        showPage(0);
    }

    private void showPage(int i) {
        page = i;
        pageImage.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(pictures.get(i)));
        pageTitle.setText(PAGES[i][1]);
        pageText.setText(PAGES[i][2]);
        pageCount.setText((i + 1) + " / " + PAGES.length);
        prev.setText(i == 0 ? "BACK" : "PREVIOUS");
        next.setText(i == PAGES.length - 1 ? "GOT IT" : "NEXT");
        pageImage.clearActions();
        pageImage.getColor().a = 0;
        pageImage.addAction(Actions.fadeIn(.2f));
    }

    // ---------- settings ----------

    private void buildSettings() {
        Table c = card();
        c.defaults().left();
        c.add(label("SETTINGS", ui.title, Ui.INK)).row();

        Slider.SliderStyle ss = new Slider.SliderStyle(ui.sliderBg, ui.knob);
        ss.knobBefore = ui.sliderFill;
        final Slider music = new Slider(0, 1, .05f, false, ss);
        music.setValue(prefs.music);
        musicValue = label("", ui.chip, Ui.BRASS);
        music.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                prefs.music = music.getValue();
                musicValue.setText(Math.round(prefs.music * 100) + "%");
                nav.settingsChanged();
            }
        });
        final Slider sfx = new Slider(0, 1, .05f, false, ss);
        sfx.setValue(prefs.sfx);
        sfxValue = label("", ui.chip, Ui.BRASS);
        sfx.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                prefs.sfx = sfx.getValue();
                sfxValue.setText(Math.round(prefs.sfx * 100) + "%");
                if (!sfx.isDragging()) audio.play("dice_land");
                nav.settingsChanged();
            }
        });
        musicValue.setText(Math.round(prefs.music * 100) + "%");
        sfxValue.setText(Math.round(prefs.sfx * 100) + "%");
        c.add(sliderRow("Music", music, musicValue)).growX().padTop(18).row();
        c.add(sliderRow("Sound effects", sfx, sfxValue)).growX().padTop(14).row();
        c.add(toggleRow("Vibration", "Buzz the phone for elevators, chutes and big hits.", prefs.vibrate,
                on -> { prefs.vibrate = on; if (on) audio.buzz(60); })).growX().padTop(18).row();
        c.add(toggleRow("Camera follows the action", "Swing to whoever's turn it is and to every explosion.", prefs.followAction,
                on -> prefs.followAction = on)).growX().padTop(14).row();
        c.add(toggleRow("Fast animations", "Speed up moves and CPU turns.", prefs.fastAnimations,
                on -> prefs.fastAnimations = on)).growX().padTop(14).row();
        TextButton done = button("DONE", true, nav::back);
        c.add(done).growX().height(52).padTop(22);
        ScrollPane sp = new ScrollPane(c);
        sp.setOverscroll(false, true);
        sp.setScrollingDisabled(true, false);
        settings.center();
        cardCells.add(settings.add(sp));
    }

    private Table sliderRow(String name, Slider slider, Label value) {
        Table row = new Table();
        row.add(label(name, ui.body, Ui.INK)).left().expandX();
        row.add(value).right().row();
        row.add(slider).colspan(2).growX().height(34).padTop(4);
        return row;
    }

    private interface Toggle { void set(boolean on); }

    private Table toggleRow(String name, String hint, boolean start, final Toggle onChange) {
        Table row = new Table();
        Table words = new Table();
        words.add(label(name, ui.body, Ui.INK)).left().growX().row();
        Label h = label(hint, ui.small, Ui.MUTED);
        h.setWrap(true);
        words.add(h).left().growX();
        final TextButton t = new TextButton(start ? "ON" : "OFF", ui.toggleButton());
        t.setChecked(start);
        t.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                audio.play("ui_click", .6f, t.isChecked() ? 1.2f : .9f);
                t.setText(t.isChecked() ? "ON" : "OFF");
                onChange.set(t.isChecked());
                nav.settingsChanged();
            }
        });
        row.add(words).growX().minWidth(0);
        row.add(t).width(70).height(40).padLeft(12);
        return row;
    }

    // ---------- pause ----------

    private void buildPause() {
        Table c = card();
        Label t = label("PAUSED", ui.title, Ui.INK);
        t.setAlignment(Align.center);
        c.add(t).row();
        c.defaults().height(52).padTop(10);
        c.add(button("RESUME", true, nav::resume)).padTop(16).row();
        c.add(button("HOW TO PLAY", false, nav::howToPlay)).row();
        c.add(button("SETTINGS", false, nav::settings)).row();
        c.add(button("QUIT TO MENU", false, nav::quitToMenu)).row();
        pause.center();
        cardCells.add(pause.add(c));
    }

    // ---------- layout ----------

    public void resize(float worldW, float worldH, float top, float left, float bottom, float right) {
        for (Table t : new Table[]{title, how, settings, pause}) t.pad(top, left, bottom, right);
        float w = Math.min(460, worldW - left - right);
        for (Cell<?> c : cardCells) c.width(w).maxHeight(worldH - top - bottom);
        for (Table t : new Table[]{title, how, settings, pause}) t.invalidateHierarchy();
    }

    @Override
    public void dispose() { for (Texture t : pictures) t.dispose(); }
}

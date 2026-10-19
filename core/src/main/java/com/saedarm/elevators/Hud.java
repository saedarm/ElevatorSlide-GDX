package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** All the 2D screens: tenant setup, the in-game HUD, and the winner card. */
public final class Hud implements Match.Listener {
    public final Stage stage;
    private final Ui ui;
    private final Match match;
    private final Tower tower;
    private final ScreenViewport viewport;

    private final Table setupRoot = new Table(), gameRoot = new Table(), winRoot = new Table();
    private Cell<ScrollPane> setupCell;
    private Cell<Table> winCell;

    // setup state
    private static final String[] DEFAULT_NAMES = {"Marge", "Lou", "Dev", "Pat"};
    private final boolean[] in = {true, true, false, false};
    private final boolean[] cpu = {false, true, false, false};
    private final TextField[] nameFields = new TextField[4];
    private TextButton startButton;

    // game HUD
    private final Table tenants = new Table();
    private final Label banner, who, dieValue, dieSides, oddsStairs, oddsHustle, winTitle, winSub;
    private final Container<Label> bannerBox;
    private final Button stairs, hustle;

    public Hud(Ui ui, Match match, Tower tower) {
        this.ui = ui;
        this.match = match;
        this.tower = tower;
        viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / ui.scale);
        stage = new Stage(viewport);

        banner = label("", ui.body, Ui.INK);
        banner.setWrap(true);
        banner.setAlignment(Align.center);
        bannerBox = new Container<>(banner);
        bannerBox.setBackground(ui.panelActive);
        bannerBox.pad(8, 14, 8, 14).fillX();
        bannerBox.setVisible(false);

        who = label("", ui.body, Ui.MUTED);
        who.setAlignment(Align.center);
        dieValue = label("–", ui.dieFont, Ui.DARK);
        dieSides = label("", ui.small, Ui.DARK);
        oddsStairs = label("", ui.small, Ui.INK);
        oddsHustle = label("", ui.small, Ui.INK);
        stairs = choice("TAKE THE STAIRS", "Move 3–6 · steady", oddsStairs);
        hustle = choice("HUSTLE", "Move 1–8 · risky", oddsHustle);
        stairs.addListener(onChange(() -> match.takeTurn(Board.Pace.STAIRS)));
        hustle.addListener(onChange(() -> match.takeTurn(Board.Pace.HUSTLE)));

        winTitle = label("", ui.title, Ui.INK);
        winTitle.setWrap(true);
        winTitle.setAlignment(Align.center);
        winSub = label("", ui.body, Ui.MUTED);
        winSub.setWrap(true);
        winSub.setAlignment(Align.center);

        buildSetup();
        buildGame();
        buildWin();
        for (Table t : new Table[]{setupRoot, gameRoot, winRoot}) {
            t.setFillParent(true);
            stage.addActor(t);
        }
        showSetup();
    }

    // ---------- small builders ----------

    private static Label label(String text, BitmapFont font, Color color) {
        return new Label(text, new Label.LabelStyle(font, color));
    }

    private static ChangeListener onChange(Runnable r) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { r.run(); }
        };
    }

    private TextButton.TextButtonStyle toggleStyle() {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle(ui.button, ui.buttonDown, ui.brass, ui.chip);
        s.fontColor = Ui.INK;
        s.checkedFontColor = Ui.DARK;
        return s;
    }

    private Button choice(String title, String sub, Label odds) {
        Button.ButtonStyle s = new Button.ButtonStyle(ui.button, ui.buttonDown, null);
        Button b = new Button(s);
        b.pad(10, 14, 10, 14);
        b.defaults().left();
        b.add(label(title, ui.heading, Ui.INK)).row();
        b.add(label(sub, ui.small, Ui.MUTED)).padTop(2).row();
        b.add(odds).padTop(4);
        return b;
    }

    // ---------- setup ----------

    private void buildSetup() {
        Table card = new Table();
        card.setBackground(ui.card);
        card.pad(22, 20, 22, 20);
        card.defaults().growX().left();

        Label title = label("ELEVATORS &\nLAUNDRY CHUTES", ui.title, Ui.INK);
        title.setWrap(true);
        card.add(title).row();
        card.add(label("RACE TO THE ROOF", ui.heading, Ui.BRASS)).padTop(4).row();
        Label intro = label("Ten floors, a hundred units, one water tower on top. Each turn you pick your pace: "
                + "take the stairs and move 3 to 6, or hustle and move 1 to 8. Same average speed, "
                + "different risk. The buttons show your odds of hitting an elevator or a chute.", ui.body, Ui.MUTED);
        intro.setWrap(true);
        card.add(intro).padTop(12).row();

        Table slots = new Table();
        slots.defaults().growX().padTop(8);
        TextField.TextFieldStyle fs = new TextField.TextFieldStyle(ui.body, Ui.INK, ui.cursor, ui.selection, ui.field);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Table row = new Table();
            final TextButton inBtn = new TextButton(in[i] ? "IN" : "OUT", toggleStyle());
            inBtn.setChecked(in[i]);
            inBtn.addListener(onChange(() -> {
                in[idx] = inBtn.isChecked();
                inBtn.setText(in[idx] ? "IN" : "OUT");
                refreshStart();
            }));
            Image dot = new Image(ui.dot);
            dot.setColor(Ui.PLAYERS[i]);
            TextField name = new TextField(DEFAULT_NAMES[i], fs);
            name.setMaxLength(12);
            nameFields[i] = name;
            final TextButton who = new TextButton(cpu[i] ? "CPU" : "HUMAN", toggleStyle());
            who.setChecked(cpu[i]);
            who.addListener(onChange(() -> {
                cpu[idx] = who.isChecked();
                who.setText(cpu[idx] ? "CPU" : "HUMAN");
            }));
            row.add(inBtn).width(58).height(42);
            row.add(dot).size(16).padLeft(10);
            row.add(name).growX().height(42).padLeft(10).minWidth(0);
            row.add(who).width(80).height(42).padLeft(10);
            slots.add(row).row();
        }
        card.add(slots).padTop(6).row();

        String[][] rules = {
                {"Brass shaft", "Ride the elevator up one or more floors."},
                {"Coral chute", "Slide down with the dirty laundry."},
                {"Exactly 100 wins", "Roll past the roof and you bounce back the extra."},
                {"CPU tenants", "They do the math and pick the better pace."}};
        Table rt = new Table();
        rt.defaults().growX().left().padTop(8);
        for (String[] r : rules) {
            Label l = label(Ui.hex(Ui.INK) + r[0] + "[]  " + r[1], ui.small, Ui.MUTED);
            l.setWrap(true);
            rt.add(l).row();
        }
        card.add(rt).padTop(8).row();

        TextButton.TextButtonStyle go = new TextButton.TextButtonStyle(ui.brass, ui.brassDown, null, ui.heading);
        go.fontColor = Ui.DARK;
        go.disabled = ui.button;
        go.disabledFontColor = Ui.MUTED;
        startButton = new TextButton("", go);
        startButton.addListener(onChange(this::startMatch));
        card.add(startButton).height(52).padTop(18).row();
        refreshStart();

        ScrollPane sp = new ScrollPane(card);
        sp.setFadeScrollBars(true);
        sp.setOverscroll(false, true);
        setupRoot.center();
        setupCell = setupRoot.add(sp);
    }

    private void refreshStart() {
        int n = 0;
        for (boolean b : in) if (b) n++;
        startButton.setDisabled(n < 2);
        startButton.setText(n < 2 ? "PICK AT LEAST 2" : "MOVE IN " + n + " TENANTS");
    }

    private void startMatch() {
        stage.setKeyboardFocus(null);
        Gdx.input.setOnscreenKeyboardVisible(false);
        Array<Player> lineup = new Array<>();
        for (int i = 0; i < 4; i++) {
            if (!in[i]) continue;
            String name = nameFields[i].getText().trim();
            if (name.isEmpty()) name = DEFAULT_NAMES[i];
            lineup.add(new Player(name, cpu[i], Ui.PLAYERS[i], tower.newToken(i)));
        }
        setupRoot.setVisible(false);
        winRoot.setVisible(false);
        gameRoot.setVisible(true);
        dieValue.setText("–");
        dieSides.setText("");
        match.start(lineup);
    }

    // ---------- in-game HUD ----------

    private void buildGame() {
        gameRoot.top();
        gameRoot.add(tenants).growX().row();
        gameRoot.add(bannerBox).growX().padTop(8).row();
        gameRoot.add().expand().row();

        Table bottom = new Table();
        bottom.setBackground(ui.panel);
        bottom.pad(12);
        Table die = new Table();
        die.setBackground(ui.dieFace);
        die.add(dieValue).row();
        die.add(dieSides).padTop(-6);
        Table whoRow = new Table();
        whoRow.add(who).growX().minWidth(0);
        whoRow.add(die).size(66).padLeft(12);
        bottom.add(whoRow).colspan(2).growX().row();
        bottom.add(stairs).growX().uniformX().padTop(10);
        bottom.add(hustle).growX().uniformX().padTop(10).padLeft(10);
        gameRoot.add(bottom).growX();
        who.setWrap(true);
    }

    private void rebuildTenants() {
        tenants.clearChildren();
        int i = 0;
        for (int pi = 0; pi < match.players.size; pi++) {
            Player p = match.players.get(pi);
            boolean active = p == match.current() && !match.isOver();
            Table cell = new Table();
            cell.setBackground(active ? ui.panelActive : ui.panel);
            cell.pad(6, 10, 6, 10);
            Image dot = new Image(ui.dot);
            dot.setColor(p.color);
            Table names = new Table();
            Label nm = label(Ui.safe(p.name), ui.body, Ui.INK);
            nm.setEllipsis(true);
            names.add(nm).left().growX().minWidth(0).row();
            names.add(label(Board.placeName(p.sq) + (p.cpu ? " · CPU" : ""), ui.small, Ui.MUTED)).left();
            cell.add(dot).size(12);
            cell.add(names).growX().minWidth(0).padLeft(8);
            cell.add(label(p.sq == 0 ? "–" : String.valueOf(p.sq), ui.number, Ui.BRASS)).padLeft(8);
            tenants.add(cell).growX().uniformX().pad(3);
            if (++i % 2 == 0) tenants.row();
        }
    }

    private String oddsText(Board.Odds o) {
        return Ui.hex(Ui.BRASS) + "Elevator " + Math.round(o.elevator * 100) + "%[]  ·  "
                + Ui.hex(Ui.CORAL) + "Chute " + Math.round(o.chute * 100) + "%[]";
    }

    private void refreshControls() {
        Player p = match.current();
        if (p == null) return;
        oddsStairs.setText(oddsText(Board.odds(p.sq, Board.Pace.STAIRS)));
        oddsHustle.setText(oddsText(Board.odds(p.sq, Board.Pace.HUSTLE)));
        String nm = Ui.hex(p.color) + Ui.safe(p.name) + "[]";
        if (match.isOver()) who.setText(nm + " made the roof.");
        else if (p.cpu) who.setText(nm + " is thinking…");
        else who.setText(nm + "'s turn · " + Board.placeName(p.sq) + (p.sq > 0 && p.sq < 100 ? ", unit " + p.sq : ""));
        boolean disabled = match.isRolling() || match.isOver() || p.cpu;
        for (Button b : new Button[]{stairs, hustle}) {
            b.setDisabled(disabled);
            b.setTouchable(disabled ? Touchable.disabled : Touchable.enabled);
            b.getColor().a = disabled ? .55f : 1f;
        }
    }

    // ---------- win ----------

    private void buildWin() {
        Table card = new Table();
        card.setBackground(ui.card);
        card.pad(26, 22, 26, 22);
        card.defaults().growX();
        card.add(winTitle).row();
        card.add(winSub).padTop(10).row();
        TextButton.TextButtonStyle go = new TextButton.TextButtonStyle(ui.brass, ui.brassDown, null, ui.heading);
        go.fontColor = Ui.DARK;
        TextButton again = new TextButton("NEW LEASE", go);
        again.addListener(onChange(this::showSetup));
        card.add(again).height(52).padTop(20);
        winRoot.center();
        winCell = winRoot.add(card);
    }

    // ---------- public ----------

    public boolean inSetup() { return setupRoot.isVisible(); }

    public void showSetup() {
        setupRoot.setVisible(true);
        gameRoot.setVisible(false);
        winRoot.setVisible(false);
        bannerBox.setVisible(false);
    }

    /** Keeps everything clear of the S23 camera hole and gesture bar. */
    public void resize(int w, int h) {
        viewport.update(w, h, true);
        float s = ui.scale;
        float top = Gdx.graphics.getSafeInsetTop() / s + 10, bottom = Gdx.graphics.getSafeInsetBottom() / s + 14;
        float left = Math.max(16, Gdx.graphics.getSafeInsetLeft() / s), right = Math.max(16, Gdx.graphics.getSafeInsetRight() / s);
        for (Table t : new Table[]{setupRoot, gameRoot, winRoot}) t.pad(top, left, bottom, right);
        float vw = viewport.getWorldWidth(), vh = viewport.getWorldHeight();
        float cardW = Math.min(520, vw - left - right);
        setupCell.width(cardW).maxHeight(vh - top - bottom);
        winCell.width(Math.min(440, vw - left - right));
        setupRoot.invalidateHierarchy();
        winRoot.invalidateHierarchy();
    }

    public void render(float dt) {
        viewport.apply();
        stage.act(dt);
        stage.draw();
    }

    // ---------- Match.Listener ----------

    @Override public void changed() { rebuildTenants(); refreshControls(); }

    @Override public void banner(String message) {
        banner.setText(Ui.safe(message));
        bannerBox.clearActions();
        bannerBox.getColor().a = 1;
        bannerBox.setVisible(true);
        bannerBox.addAction(Actions.sequence(Actions.delay(2.6f), Actions.fadeOut(.3f), Actions.visible(false)));
    }

    @Override public void die(int value, Board.Pace pace) {
        dieValue.setText(String.valueOf(value));
        dieSides.setText(pace.dice);
    }

    @Override public void won(Player winner) {
        winTitle.setText(winner.name + " made the roof");
        StringBuilder sb = new StringBuilder("First to unit 100 and the water tower.");
        boolean first = true;
        for (int qi = 0; qi < match.players.size; qi++) {
            Player q = match.players.get(qi);
            if (q == winner) continue;
            sb.append(first ? " Still climbing: " : ", ").append(Ui.safe(q.name)).append(" on ")
                    .append(q.sq == 0 ? "the lobby" : String.valueOf(q.sq));
            first = false;
        }
        if (!first) sb.append('.');
        winSub.setText(sb.toString());
        winRoot.setVisible(true);
        refreshControls();
    }
}

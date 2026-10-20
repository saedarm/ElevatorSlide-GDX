package com.saedarm.elevators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
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

import java.util.List;

/** All the 2D screens: menus, tenant setup, the in-game HUD with cards, overlays, and the winner card. */
public final class Hud implements Match.Listener, Menus.Nav {
    public final Stage stage;
    private final Ui ui;
    private final Match match;
    private final Audio audio;
    private final Settings settings;
    private final Menus menus;
    private final ScreenViewport viewport;
    private final Image menuDim;
    /** Where How to Play and Settings return to. */
    private boolean menuFromPause;

    private final Table setupRoot = new Table(), gameRoot = new Table(), winRoot = new Table(), modalRoot = new Table();
    private Cell<ScrollPane> setupCell;
    private Cell<Table> winCell, modalCell;
    private Cell<Container<Table>> revealCell;
    private final Group worldLayer = new Group();
    private final Image flash, vhsTint, vhsLines, alarm, modalDim;
    private boolean modalClosable;
    private final Label vhsLabel;

    // setup state
    private static final String[] DEFAULT_NAMES = {"Marge", "Lou", "Dev", "Pat"};
    private final boolean[] in = {true, true, false, false};
    private final boolean[] cpu = {false, true, false, false};
    private final TextField[] nameFields = new TextField[4];
    private TextButton startButton;

    // game HUD
    private final Table tenants = new Table(), hand = new Table();
    private final Label banner, status, who, dieValue, dieSides, stairsSub, hustleSub, oddsStairs, oddsHustle, winTitle, winSub;
    private final Container<Label> bannerBox;
    private final Container<Table> revealBox;
    private final Button stairs, hustle;

    // floating world-anchored text and spinners
    private static final class Floater { final Vector3 at; final Label label; float age; Floater(Vector3 a, Label l) { at = a; label = l; } }
    private final Array<Floater> floaters = new Array<>();
    private final Array<Image> spinners = new Array<>();
    private final Vector3 proj = new Vector3();

    public Hud(Ui ui, Match match, Audio audio, Settings settings) {
        this.ui = ui;
        this.match = match;
        this.audio = audio;
        this.settings = settings;
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

        status = label("", ui.small, Ui.INK);
        status.setWrap(true);
        status.setAlignment(Align.center);

        who = label("", ui.body, Ui.MUTED);
        who.setWrap(true);
        dieValue = label("–", ui.dieFont, Ui.DARK);
        dieSides = label("", ui.small, Ui.DARK);
        stairsSub = label("", ui.small, Ui.MUTED);
        hustleSub = label("", ui.small, Ui.MUTED);
        oddsStairs = label("", ui.small, Ui.INK);
        oddsHustle = label("", ui.small, Ui.INK);
        stairs = choice("TAKE THE STAIRS", stairsSub, oddsStairs);
        hustle = choice("HUSTLE", hustleSub, oddsHustle);
        stairs.addListener(onChange(() -> match.takeTurn(Board.Pace.STAIRS)));
        hustle.addListener(onChange(() -> match.takeTurn(Board.Pace.HUSTLE)));

        revealBox = new Container<>();
        revealBox.setVisible(false);
        revealBox.setTouchable(Touchable.disabled);

        winTitle = label("", ui.title, Ui.INK);
        winTitle.setWrap(true);
        winTitle.setAlignment(Align.center);
        winSub = label("", ui.body, Ui.MUTED);
        winSub.setWrap(true);
        winSub.setAlignment(Align.center);

        flash = overlay(ui.white);
        vhsTint = overlay(ui.white);
        vhsLines = overlay(ui.scanlines);
        alarm = overlay(ui.white);
        modalDim = overlay(ui.white);
        modalDim.setColor(0, 0, 0, .55f);
        modalDim.setTouchable(Touchable.enabled);
        modalDim.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (modalClosable) closeModal();
            }
        });
        vhsLabel = label("<< REWIND", ui.heading, Ui.INK);
        vhsLabel.setVisible(false);
        menuDim = overlay(ui.white);
        menuDim.setColor(0, 0, 0, .6f);
        menuDim.setTouchable(Touchable.enabled);
        menus = new Menus(ui, audio, settings, this);

        buildSetup();
        buildGame();
        buildWin();
        modalRoot.setVisible(false);
        worldLayer.setTouchable(Touchable.disabled);
        stage.addActor(worldLayer);
        for (Table t : new Table[]{setupRoot, gameRoot, winRoot}) {
            t.setFillParent(true);
            stage.addActor(t);
        }
        Table revealRoot = new Table();
        revealRoot.setFillParent(true);
        revealRoot.setTouchable(Touchable.disabled);
        revealCell = revealRoot.add(revealBox);
        stage.addActor(revealRoot);
        modalRoot.setFillParent(true);
        stage.addActor(modalDim);
        stage.addActor(modalRoot);
        stage.addActor(menuDim);
        for (Table t : new Table[]{menus.title, menus.pause, menus.how, menus.settings}) stage.addActor(t);
        for (Image i : new Image[]{alarm, vhsTint, vhsLines, flash}) stage.addActor(i);
        stage.addActor(vhsLabel);
        settingsChanged();
        showTitle();
    }

    // ---------- small builders ----------

    private static Label label(String text, BitmapFont font, Color color) {
        return new Label(text, new Label.LabelStyle(font, color));
    }

    private ChangeListener onChange(Runnable r) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                audio.play("ui_click", .5f, 1f);
                r.run();
            }
        };
    }

    private Image overlay(com.badlogic.gdx.scenes.scene2d.utils.Drawable d) {
        Image i = new Image(d);
        i.setTouchable(Touchable.disabled);
        i.setVisible(false);
        return i;
    }

    private TextButton.TextButtonStyle toggleStyle() {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle(ui.button, ui.buttonDown, ui.brass, ui.chip);
        s.fontColor = Ui.INK;
        s.checkedFontColor = Ui.DARK;
        return s;
    }

    private TextButton.TextButtonStyle brassStyle() {
        TextButton.TextButtonStyle go = new TextButton.TextButtonStyle(ui.brass, ui.brassDown, null, ui.heading);
        go.fontColor = Ui.DARK;
        go.disabled = ui.button;
        go.disabledFontColor = Ui.MUTED;
        return go;
    }

    private TextButton.TextButtonStyle plainStyle() {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle(ui.button, ui.buttonDown, null, ui.chip);
        s.fontColor = Ui.INK;
        return s;
    }

    private Button choice(String title, Label sub, Label odds) {
        Button b = new Button(new Button.ButtonStyle(ui.button, ui.buttonDown, null));
        b.pad(10, 14, 10, 14);
        b.defaults().left();
        b.add(label(title, ui.heading, Ui.INK)).row();
        b.add(sub).padTop(2).row();
        b.add(odds).padTop(4);
        return b;
    }

    private static void setEnabled(Button b, boolean on) {
        b.setDisabled(!on);
        b.setTouchable(on ? Touchable.enabled : Touchable.disabled);
        b.getColor().a = on ? 1f : .5f;
    }

    // ---------- setup ----------

    private void buildSetup() {
        Table card = new Table();
        card.setBackground(ui.card);
        card.pad(22, 20, 22, 20);
        card.defaults().growX().left();

        Label title = label("WHO'S MOVING IN?", ui.title, Ui.INK);
        title.setWrap(true);
        card.add(title).row();
        Label intro = label("Two to four tenants. Tap IN or OUT, type a name, and pick human or CPU.", ui.body, Ui.MUTED);
        intro.setWrap(true);
        card.add(intro).padTop(8).row();

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
            final TextButton whoBtn = new TextButton(cpu[i] ? "CPU" : "HUMAN", toggleStyle());
            whoBtn.setChecked(cpu[i]);
            whoBtn.addListener(onChange(() -> {
                cpu[idx] = whoBtn.isChecked();
                whoBtn.setText(cpu[idx] ? "CPU" : "HUMAN");
            }));
            row.add(inBtn).width(58).height(42);
            row.add(dot).size(16).padLeft(10);
            row.add(name).growX().height(42).padLeft(10).minWidth(0);
            row.add(whoBtn).width(80).height(42).padLeft(10);
            slots.add(row).row();
        }
        card.add(slots).padTop(6).row();

        startButton = new TextButton("", brassStyle());
        startButton.addListener(onChange(this::startMatch));
        card.add(startButton).height(52).padTop(18).row();
        TextButton back = new TextButton("BACK", plainStyle());
        back.addListener(onChange(this::showTitle));
        card.add(back).height(44).padTop(8).row();
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
        int n = 0;
        for (boolean b : in) if (b) n++;
        String[] names = new String[n];
        boolean[] cpus = new boolean[n];
        int[] colors = new int[n];
        int k = 0;
        for (int i = 0; i < 4; i++) {
            if (!in[i]) continue;
            String name = nameFields[i].getText().trim();
            names[k] = name.isEmpty() ? DEFAULT_NAMES[i] : name;
            cpus[k] = cpu[i];
            colors[k] = i;
            k++;
        }
        setupRoot.setVisible(false);
        winRoot.setVisible(false);
        gameRoot.setVisible(true);
        dieValue.setText("–");
        dieSides.setText("");
        match.start(names, cpus, colors);
    }

    // ---------- in-game HUD ----------

    private void buildGame() {
        gameRoot.top();
        Table topRow = new Table();
        topRow.add(tenants).growX().top().minWidth(0);
        TextButton pauseBtn = new TextButton("II", plainStyle());
        pauseBtn.addListener(onChange(this::openPause));
        topRow.add(pauseBtn).size(44).top().padLeft(4).padTop(3);
        gameRoot.add(topRow).growX().row();
        gameRoot.add(status).growX().padTop(4).row();
        gameRoot.add(bannerBox).growX().padTop(6).row();
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
        bottom.add(hand).colspan(2).growX().padTop(8).row();
        bottom.add(stairs).growX().uniformX().padTop(10);
        bottom.add(hustle).growX().uniformX().padTop(10).padLeft(10);
        gameRoot.add(bottom).growX();
    }

    private void rebuildTenants() {
        tenants.clearChildren();
        Engine e = match.engine();
        if (e == null) return;
        Player cur = match.current();
        for (int i = 0; i < match.players.size; i++) {
            Player p = match.players.get(i);
            boolean active = p == cur && match.phase() != Match.Phase.OVER;
            Table cell = new Table();
            cell.setBackground(active ? ui.panelActive : ui.panel);
            cell.pad(6, 10, 6, 10);
            Image dot = new Image(ui.dot);
            dot.setColor(p.color);
            Table names = new Table();
            Label nm = label(Ui.safe(p.name()), ui.body, Ui.INK);
            nm.setEllipsis(true);
            names.add(nm).left().growX().minWidth(0).row();
            StringBuilder sub = new StringBuilder(Board.placeName(p.shownSq));
            if (p.cpu()) sub.append(" · CPU");
            if (p.t.offline) sub.append(" · ").append(Ui.hex(Ui.CORAL)).append("OFFLINE[]");
            if (p.t.stairsOnly) sub.append(" · ").append(Ui.hex(Ui.CORAL)).append("STAIRS ONLY[]");
            if (p.t.doormanTip) sub.append(" · ").append(Ui.hex(Ui.BRASS)).append("TIPPED[]");
            int cards = p.t.hand.size();
            if (cards > 0) sub.append(" · ").append(cards).append(cards == 1 ? " card" : " cards");
            Label sl = label(sub.toString(), ui.small, Ui.MUTED);
            sl.setWrap(true);
            names.add(sl).left().growX();
            cell.add(dot).size(12);
            cell.add(names).growX().minWidth(0).padLeft(8);
            cell.add(label(p.shownSq == 0 ? "–" : String.valueOf(p.shownSq), ui.number, Ui.BRASS)).padLeft(8);
            tenants.add(cell).growX().uniformX().pad(3);
            if ((i + 1) % 2 == 0) tenants.row();
        }
    }

    private void refreshStatus() {
        Engine e = match.engine();
        if (e == null) { status.setText(""); return; }
        StringBuilder sb = new StringBuilder();
        String coral = Ui.hex(Ui.CORAL), brass = Ui.hex(Ui.BRASS), teal = Ui.hex(Ui.TEAL);
        if (e.powerOut()) add(sb, coral + "POWER OUT[] " + e.roundsLeft(e.powerUntil) + "r");
        if (e.cableBroken()) add(sb, coral + "ELEVATORS BROKEN[] " + e.roundsLeft(e.cableUntil) + "r");
        if (e.express()) add(sb, brass + "EXPRESS HOUR[]");
        if (e.wet()) add(sb, teal + "WET FLOOR[] stairs 2–7");
        if (e.pileup()) add(sb, teal + "PILEUP[] chute " + Board.CHUTES[e.pileupChute][0]);
        if (e.raccoons()) add(sb, brass + "RACCOONS[] chute " + Board.CHUTES[e.raccoonChute][0] + " " + e.roundsLeft(e.raccoonUntil) + "r");
        for (int f = 1; f <= 10; f++) if (e.fire(f)) add(sb, coral + "FIRE[] floor " + f + " " + e.roundsLeft(e.fireUntil[f]) + "r");
        for (int s = 1; s < Board.ROOF; s++) if (e.hole(s)) add(sb, coral + "HOLE[] at " + s + " " + e.roundsLeft(e.holeUntil[s]) + "r");
        int untilEvent = Engine.EVENT_EVERY - (e.turnCount / e.tenants.size()) % Engine.EVENT_EVERY;
        add(sb, Ui.hex(Ui.MUTED) + "next building event in " + untilEvent + (untilEvent == 1 ? " round[]" : " rounds[]"));
        status.setText(sb.toString());
    }

    private static void add(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("   ");
        sb.append(s);
    }

    private String oddsText(Engine.Odds o) {
        return Ui.hex(Ui.BRASS) + "Up " + Math.round(o.up * 100) + "%[]  ·  "
                + Ui.hex(Ui.CORAL) + "Down " + Math.round(o.down * 100) + "%[]";
    }

    private void refreshControls() {
        Engine e = match.engine();
        Player p = match.current();
        if (e == null || p == null) return;
        boolean human = match.humanCanAct();
        boolean key = p.t.has(Card.SPARE_KEY);
        oddsStairs.setText(oddsText(e.odds(p.t, Board.Pace.STAIRS)) + (key ? "  " + Ui.hex(Ui.TEAL) + "w/ key[]" : ""));
        oddsHustle.setText(oddsText(e.odds(p.t, Board.Pace.HUSTLE)) + (key ? "  " + Ui.hex(Ui.TEAL) + "w/ key[]" : ""));
        stairsSub.setText("Move " + e.paceMin(Board.Pace.STAIRS) + "–" + e.paceMax(Board.Pace.STAIRS) + (e.wet() ? " · slippery" : " · steady"));
        hustleSub.setText(e.canHustle() ? "Move 1–8 · risky" : "Noise complaint · not allowed");
        String nm = Ui.hex(p.color) + Ui.safe(p.name()) + "[]";
        if (match.phase() == Match.Phase.OVER) who.setText(nm + " made the roof.");
        else if (p.cpu()) who.setText(nm + " is thinking…");
        else who.setText(nm + "'s turn · " + Board.placeName(p.shownSq) + (p.shownSq > 0 && p.shownSq < 100 ? ", unit " + p.shownSq : ""));
        setEnabled(stairs, human);
        setEnabled(hustle, human && e.canHustle());
        rebuildHand(p, human);
    }

    private void rebuildHand(Player p, boolean human) {
        hand.clearChildren();
        if (p.cpu()) {
            int n = p.t.hand.size();
            if (n > 0) hand.add(label(Ui.safe(p.name()) + " is holding " + n + (n == 1 ? " card." : " cards."), ui.small, Ui.MUTED)).left().growX();
            return;
        }
        if (p.t.hand.isEmpty()) {
            hand.add(label("No cards yet. Land on a blue mailbox tile to draw one.", ui.small, Ui.MUTED)).left();
            return;
        }
        Engine e = match.engine();
        for (final Card c : p.t.hand) {
            Button b = new Button(new Button.ButtonStyle(ui.tier(c), ui.buttonDown, null));
            b.pad(6, 8, 6, 8);
            Label t = label(c.title.toUpperCase(), ui.chip, Ui.INK);
            t.setWrap(true);
            t.setAlignment(Align.center);
            b.add(t).growX().row();
            String tag = c.use == Card.Use.PASSIVE ? "AUTO" : e.playable(p.t, c) ? "TAP TO PLAY" : e.cardPlayed ? "ONE PER TURN" : "NO TARGET";
            Label tg = label(tag, ui.small, Ui.tierColor(c));
            tg.setAlignment(Align.center);
            tg.setWrap(true);
            b.add(tg).growX();
            b.addListener(onChange(() -> openCard(c)));
            b.setDisabled(!human);
            b.getColor().a = human ? 1 : .6f;
            hand.add(b).growX().uniformX().minWidth(0).minHeight(54).padRight(6);
        }
    }

    // ---------- card confirm / target picker ----------

    private void openCard(final Card c) {
        if (!match.humanCanAct()) return;
        Engine e = match.engine();
        Engine.Tenant me = e.current();
        Table card = new Table();
        card.setBackground(ui.tier(c));
        card.pad(18);
        card.defaults().growX().left();
        card.add(label(Ui.tierName(c), ui.chip, Ui.tierColor(c))).row();
        Label t = label(c.title.toUpperCase(), ui.title, Ui.INK);
        t.setWrap(true);
        card.add(t).padTop(4).row();
        Label d = label(c.text, ui.body, Ui.MUTED);
        d.setWrap(true);
        card.add(d).padTop(8).row();

        if (c.use == Card.Use.PASSIVE) {
            card.add(label("This card works on its own when it's needed.", ui.small, Ui.TEAL)).padTop(12).row();
        } else if (!e.playable(me, c)) {
            card.add(label(e.cardPlayed ? "You already played a card this turn." : "Nobody can be targeted right now.", ui.small, Ui.CORAL)).padTop(12).row();
        } else if (c.use == Card.Use.TENANT) {
            card.add(label("PICK A TENANT", ui.chip, Ui.BRASS)).padTop(14).row();
            List<Engine.Tenant> targets = e.targets(me, c);
            for (final Engine.Tenant target : targets) {
                Player tp = match.player(target.id);
                Button b = new Button(new Button.ButtonStyle(ui.button, ui.buttonDown, null));
                b.pad(8, 12, 8, 12);
                b.defaults().left();
                b.add(label(Ui.hex(tp.color) + Ui.safe(tp.name()) + "[]  on " + (target.sq == 0 ? "the lobby" : String.valueOf(target.sq)), ui.body, Ui.INK)).growX().row();
                String pv = e.preview(me, c, target);
                if (!pv.isEmpty()) { Label pl = label(pv, ui.small, Ui.MUTED); pl.setWrap(true); b.add(pl).growX(); }
                b.addListener(onChange(() -> { closeModal(); match.playCard(c, target); }));
                card.add(b).padTop(6).row();
            }
        } else {
            String pv = e.preview(me, c, null);
            if (!pv.isEmpty()) {
                Label pl = label(pv, ui.small, Ui.BRASS);
                pl.setWrap(true);
                card.add(pl).padTop(12).row();
            }
            TextButton play = new TextButton("PLAY IT", brassStyle());
            play.addListener(onChange(() -> { closeModal(); match.playCard(c, null); }));
            card.add(play).height(50).padTop(14).row();
        }
        TextButton close = new TextButton("KEEP IT FOR LATER", plainStyle());
        close.addListener(onChange(this::closeModal));
        card.add(close).height(42).padTop(8).row();
        openModal(card, true);
    }

    private void openModal(Table content, boolean closable) {
        modalClosable = closable;
        modalRoot.clearChildren();
        modalCell = modalRoot.add(content);
        modalCell.width(Math.min(420, viewport.getWorldWidth() - 32));
        modalRoot.setVisible(true);
        modalDim.setVisible(true);
        modalDim.clearActions();
        modalDim.getColor().a = 0;
        modalDim.addAction(Actions.alpha(.55f, .15f));
        content.setTransform(true);
        content.setOrigin(Align.center);
        content.setScale(.9f);
        content.addAction(Actions.scaleTo(1, 1, .18f, Interpolation.swingOut));
    }

    private void closeModal() {
        modalRoot.setVisible(false);
        modalDim.setVisible(false);
        modalRoot.clearChildren();
    }

    // ---------- win ----------

    private void buildWin() {
        Table card = new Table();
        card.setBackground(ui.card);
        card.pad(26, 22, 26, 22);
        card.defaults().growX();
        card.add(winTitle).row();
        card.add(winSub).padTop(10).row();
        TextButton again = new TextButton("NEW LEASE", brassStyle());
        again.addListener(onChange(this::showSetup));
        card.add(again).height(52).padTop(20).row();
        TextButton menu = new TextButton("MAIN MENU", plainStyle());
        menu.addListener(onChange(this::showTitle));
        card.add(menu).height(44).padTop(8);
        winRoot.center();
        winCell = winRoot.add(card);
    }

    // ---------- public ----------

    /** True when no game is on screen, so the camera can idle-spin around the tower. */
    public boolean inMenus() { return !gameRoot.isVisible(); }

    private void hideAll() {
        for (Table t : new Table[]{setupRoot, gameRoot, winRoot, menus.title, menus.how, menus.settings, menus.pause}) t.setVisible(false);
        menuDim.setVisible(false);
        bannerBox.setVisible(false);
        revealBox.setVisible(false);
        closeModal();
    }

    public void showTitle() {
        match.abandon();
        match.paused = false;
        hideAll();
        for (Floater f : floaters) f.label.remove();
        floaters.clear();
        menus.title.setVisible(true);
        audio.setInGame(false);
    }

    public void showSetup() {
        match.abandon();
        match.paused = false;
        hideAll();
        setupRoot.setVisible(true);
        audio.setInGame(false);
    }

    private void openPause() {
        if (!gameRoot.isVisible()) return;
        match.paused = true;
        closeModal();
        menus.how.setVisible(false);
        menus.settings.setVisible(false);
        menuDim.setVisible(true);
        menus.pause.setVisible(true);
    }

    // ---------- Menus.Nav ----------

    @Override public void play() { showSetup(); }

    @Override public void howToPlay() {
        menuFromPause = menus.pause.isVisible();
        menus.title.setVisible(false);
        menus.pause.setVisible(false);
        menus.openHow();
        menus.how.setVisible(true);
    }

    @Override public void settings() {
        menuFromPause = menus.pause.isVisible();
        menus.title.setVisible(false);
        menus.pause.setVisible(false);
        menus.settings.setVisible(true);
    }

    @Override public void quit() { Gdx.app.exit(); }

    @Override public void resume() {
        menus.pause.setVisible(false);
        menuDim.setVisible(false);
        match.paused = false;
        refreshControls();
    }

    @Override public void quitToMenu() { showTitle(); }

    @Override public void back() {
        audio.play("ui_back", .6f, 1f);
        settings.save();
        menus.how.setVisible(false);
        menus.settings.setVisible(false);
        if (menuFromPause) menus.pause.setVisible(true);
        else menus.title.setVisible(true);
    }

    @Override public void settingsChanged() {
        audio.apply();
        match.speed = settings.fastAnimations ? 1.8f : 1f;
    }

    /**
     * Android Back / Escape, from the inside out: card panel, How to Play or Settings, pause menu,
     * then a running game pauses, setup goes to the title. Returns false only on the title screen.
     */
    public boolean handleBack() {
        if (modalRoot.isVisible()) {
            if (modalClosable) closeModal();
            return true;
        }
        if (menus.how.isVisible() || menus.settings.isVisible()) { back(); return true; }
        if (menus.pause.isVisible()) { resume(); return true; }
        if (winRoot.isVisible()) { showTitle(); return true; }
        if (gameRoot.isVisible()) { openPause(); return true; }
        if (setupRoot.isVisible()) { showTitle(); return true; }
        return false;
    }

    /** Keeps everything clear of the S23 camera hole and gesture bar. */
    public void resize(int w, int h) {
        viewport.update(w, h, true);
        float s = ui.scale;
        float top = Gdx.graphics.getSafeInsetTop() / s + 10, bottom = Gdx.graphics.getSafeInsetBottom() / s + 14;
        float left = Math.max(16, Gdx.graphics.getSafeInsetLeft() / s), right = Math.max(16, Gdx.graphics.getSafeInsetRight() / s);
        for (Table t : new Table[]{setupRoot, gameRoot, winRoot, modalRoot}) t.pad(top, left, bottom, right);
        float vw = viewport.getWorldWidth(), vh = viewport.getWorldHeight();
        setupCell.width(Math.min(520, vw - left - right)).maxHeight(vh - top - bottom);
        winCell.width(Math.min(440, vw - left - right));
        if (modalCell != null) modalCell.width(Math.min(420, vw - 32));
        menus.resize(vw, vh, top, left, bottom, right);
        menuDim.setBounds(0, 0, vw, vh);
        for (Image i : new Image[]{flash, vhsTint, vhsLines, alarm, modalDim}) i.setBounds(0, 0, vw, vh);
        vhsLabel.setPosition(left + 4, vh - top - 40);
        setupRoot.invalidateHierarchy();
        winRoot.invalidateHierarchy();
    }

    public void dispose() {
        menus.dispose();
        stage.dispose();
    }

    public void render(float dt, Camera cam) {
        updateWorldLayer(dt, cam);
        viewport.apply();
        stage.act(dt);
        stage.draw();
    }

    private boolean toStage(Vector3 world, Camera cam) {
        proj.set(world);
        cam.project(proj);
        if (proj.z > 1 || proj.z < 0) return false;
        proj.x /= ui.scale;
        proj.y /= ui.scale;
        return true;
    }

    private void updateWorldLayer(float dt, Camera cam) {
        for (int i = floaters.size - 1; i >= 0; i--) {
            Floater f = floaters.get(i);
            f.age += dt;
            if (f.age > 1.6f) { f.label.remove(); floaters.removeIndex(i); continue; }
            f.at.y += dt * .9f;
            if (!toStage(f.at, cam)) { f.label.setVisible(false); continue; }
            f.label.setVisible(true);
            f.label.pack();
            f.label.setPosition(proj.x - f.label.getWidth() / 2, proj.y);
            float a = f.age < .15f ? f.age / .15f : f.age > 1.2f ? 1 - (f.age - 1.2f) / .4f : 1;
            f.label.getColor().a = a;
            float sc = f.age < .15f ? .6f + 2.7f * f.age : 1;
            f.label.setFontScale(sc);
        }
        int need = 0;
        if (gameRoot.isVisible()) for (int i = 0; i < match.players.size; i++) if (match.players.get(i).t.offline) need++;
        while (spinners.size < need) {
            Image s = new Image(ui.ring);
            s.setSize(28, 28);
            s.setOrigin(Align.center);
            s.setColor(Ui.MUTED);
            worldLayer.addActor(s);
            spinners.add(s);
        }
        int k = 0;
        for (int i = 0; i < match.players.size && gameRoot.isVisible(); i++) {
            Player p = match.players.get(i);
            if (!p.t.offline) continue;
            Image s = spinners.get(k++);
            if (!toStage(new Vector3(p.pos).add(0, 2.2f, 0), cam)) { s.setVisible(false); continue; }
            s.setVisible(true);
            s.setPosition(proj.x - 14, proj.y - 14);
            s.rotateBy(-dt * 360);
        }
        for (; k < spinners.size; k++) spinners.get(k).setVisible(false);
    }

    // ---------- Match.Listener ----------

    @Override public void changed() { rebuildTenants(); refreshStatus(); refreshControls(); }

    @Override public void banner(String message) {
        banner.setText(Ui.safe(message));
        bannerBox.clearActions();
        bannerBox.getColor().a = 1;
        bannerBox.setVisible(true);
        bannerBox.addAction(Actions.sequence(Actions.delay(3f), Actions.fadeOut(.3f), Actions.visible(false)));
    }

    @Override public void die(int value, String label) {
        dieValue.setText(String.valueOf(value));
        dieSides.setText(label);
    }

    private void showReveal(Table content, float hold) {
        revealBox.setActor(content);
        revealBox.fill();
        revealCell.width(Math.min(380, viewport.getWorldWidth() - 48));
        ((Table) revealBox.getParent()).invalidateHierarchy();
        revealBox.clearActions();
        revealBox.setVisible(true);
        revealBox.getColor().a = 0;
        content.setTransform(true);
        content.setWidth(Math.min(380, viewport.getWorldWidth() - 48));
        content.pack();
        content.setOrigin(Align.center);
        content.setScale(.6f);
        content.setRotation(-6);
        content.addAction(Actions.parallel(Actions.scaleTo(1, 1, .25f, Interpolation.swingOut), Actions.rotateTo(0, .25f)));
        revealBox.addAction(Actions.sequence(Actions.fadeIn(.12f), Actions.delay(hold), Actions.fadeOut(.25f), Actions.visible(false)));
    }

    @Override public void cardReveal(Player by, Card card, Player target) {
        Table t = new Table();
        t.setBackground(ui.tier(card));
        t.pad(16, 18, 16, 18);
        t.defaults().growX().left();
        String line = Ui.hex(by.color) + Ui.safe(by.name()) + "[] played" + (target != null ? " on " + Ui.hex(target.color) + Ui.safe(target.name()) + "[]" : "");
        t.add(label(line, ui.small, Ui.MUTED)).row();
        t.add(label(Ui.tierName(card), ui.chip, Ui.tierColor(card))).padTop(6).row();
        Label title = label(card.title.toUpperCase(), ui.title, Ui.INK);
        title.setWrap(true);
        t.add(title).padTop(2).row();
        Label d = label(card.text, ui.small, Ui.MUTED);
        d.setWrap(true);
        t.add(d).padTop(6);
        showReveal(t, 1.4f);
    }

    @Override public void eventReveal(Engine.Event event) {
        Table t = new Table();
        t.setBackground(ui.tierEvent);
        t.pad(18);
        t.defaults().growX().left();
        t.add(label("BUILDING EVENT", ui.chip, Ui.BRASS)).row();
        Label title = label(event.title.toUpperCase(), ui.title, Ui.INK);
        title.setWrap(true);
        t.add(title).padTop(4).row();
        Label d = label(event.text, ui.body, Ui.MUTED);
        d.setWrap(true);
        t.add(d).padTop(6);
        showReveal(t, 1.8f);
    }

    @Override public void drewCard(Player p, Card card, String why) {
        banner(p.cpu() ? why + "." : why + ": " + card.title + ".");
        changed();
    }

    @Override public void nudge(int roll, int[] options, int[] ends) {
        Table card = new Table();
        card.setBackground(ui.tierNormal);
        card.pad(18);
        card.defaults().growX().left();
        card.add(label("SPARE KEY", ui.chip, Ui.TEAL)).row();
        card.add(label("You rolled " + roll + ". Move how far?", ui.heading, Ui.INK)).padTop(4).row();
        Player p = match.current();
        for (int i = 0; i < options.length; i++) {
            final int steps = options[i];
            int landed = Board.land(p.t.sq, steps), end = ends[i];
            String what = end > landed ? Ui.hex(Ui.BRASS) + "elevator up to " + end + "[]"
                    : end < landed ? Ui.hex(Ui.CORAL) + "down to " + (end == 0 ? "the lobby" : String.valueOf(end)) + "[]"
                    : end == Board.ROOF ? Ui.hex(Ui.BRASS) + "THE ROOF[]" : "ends on " + end;
            String head = steps == roll ? "Keep it: move " + steps : "Move " + steps + (steps < roll ? " (one less)" : " (one more)");
            Button b = new Button(new Button.ButtonStyle(steps == roll ? ui.button : ui.tierNormal, ui.buttonDown, null));
            b.pad(10, 12, 10, 12);
            b.defaults().left();
            b.add(label(head.toUpperCase(), ui.chip, Ui.INK)).row();
            b.add(label(what + (steps != roll ? "  " + Ui.hex(Ui.MUTED) + "uses the key[]" : ""), ui.small, Ui.MUTED));
            b.addListener(onChange(() -> { closeModal(); match.chooseSteps(steps); }));
            card.add(b).padTop(8).row();
        }
        openModal(card, false);
    }

    @Override public void floatText(Vector3 at, String text, Color color) {
        Label l = label(text, ui.heading, color);
        l.setTouchable(Touchable.disabled);
        worldLayer.addActor(l);
        floaters.add(new Floater(new Vector3(at), l));
    }

    @Override public void flash(Color color, float seconds) {
        flash.clearActions();
        flash.setColor(color.r, color.g, color.b, .7f);
        flash.setVisible(true);
        flash.addAction(Actions.sequence(Actions.fadeOut(seconds), Actions.visible(false)));
    }

    @Override public void vhs(float seconds) {
        vhsTint.clearActions();
        vhsTint.setColor(.55f, .3f, .9f, .28f);
        vhsTint.setVisible(true);
        vhsTint.addAction(Actions.sequence(Actions.delay(seconds), Actions.fadeOut(.3f), Actions.visible(false)));
        vhsLines.clearActions();
        vhsLines.setColor(1, 1, 1, 1);
        vhsLines.setVisible(true);
        vhsLines.addAction(Actions.sequence(
                Actions.repeat((int) (seconds / .12f), Actions.sequence(Actions.moveBy(0, 2, .06f), Actions.moveBy(0, -2, .06f))),
                Actions.visible(false)));
        vhsLabel.clearActions();
        vhsLabel.setVisible(true);
        vhsLabel.getColor().a = 1;
        vhsLabel.addAction(Actions.sequence(Actions.repeat((int) (seconds / .5f), Actions.sequence(Actions.alpha(.2f, .25f), Actions.alpha(1, .25f))),
                Actions.visible(false)));
    }

    @Override public void alarm(float seconds) {
        alarm.clearActions();
        alarm.setColor(.95f, .15f, .15f, 0);
        alarm.setVisible(true);
        alarm.addAction(Actions.sequence(
                Actions.repeat((int) (seconds / .6f), Actions.sequence(Actions.alpha(.32f, .3f), Actions.alpha(0, .3f))),
                Actions.visible(false)));
    }

    @Override public void won(Player winner) {
        winTitle.setText(winner.name() + " made the roof");
        StringBuilder sb = new StringBuilder("First to unit 100 and the water tower.");
        boolean first = true;
        for (int i = 0; i < match.players.size; i++) {
            Player q = match.players.get(i);
            if (q == winner) continue;
            sb.append(first ? " Still climbing: " : ", ").append(Ui.safe(q.name())).append(" on ")
                    .append(q.t.sq == 0 ? "the lobby" : String.valueOf(q.t.sq));
            first = false;
        }
        if (!first) sb.append('.');
        winSub.setText(sb.toString());
        winRoot.setVisible(true);
        refreshControls();
    }
}

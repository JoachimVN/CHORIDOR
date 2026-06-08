package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full-screen landing page. Three cards in a radial carousel — clicking a side card rotates it
 * to centre; clicking the centre card expands its body. Exiting to a game plays a scale-fade
 * transition that reveals the live board underneath.
 */
public final class LandingView {

    // ── Carousel positions ────────────────────────────────────────────────────
    private record Pos3D(double tx, double ty, double rot, double sc, double op) {}

    private static final Pos3D P_LEFT   = new Pos3D(-350, 24, -7.0, 0.82, 0.48);
    private static final Pos3D P_CENTER = new Pos3D(0,     0,  0.0, 1.00, 1.00);
    private static final Pos3D P_RIGHT  = new Pos3D( 350, 24,  7.0, 0.82, 0.48);
    private static final Pos3D P_L_HOV  = new Pos3D(-350, 12, -7.0, 0.91, 0.74);
    private static final Pos3D P_R_HOV  = new Pos3D( 350, 12,  7.0, 0.91, 0.74);
    /** Side cards drift back this many px when centre card is expanded. */
    private static final double EXPAND_DRIFT = 22;
    private static final Pos3D[] SLOTS = { P_LEFT, P_CENTER, P_RIGHT };

    // ── Durations ─────────────────────────────────────────────────────────────
    private static final Duration DUR_ROTATE  = Duration.millis(420);
    private static final Duration DUR_EXPAND  = Duration.millis(300);
    private static final Duration DUR_FADE_IO = Duration.millis(110);
    private static final Duration DUR_GROW    = Duration.millis(360);
    private static final Duration DUR_HOVER   = Duration.millis(160);
    private static final Duration DUR_EXIT    = Duration.millis(400);
    private static final Duration DUR_STAGGER = Duration.millis(170);
    private static final Interpolator EASE    = Interpolator.EASE_BOTH;

    // ── Accents ───────────────────────────────────────────────────────────────
    private static final String ACC_PLAY = "#9E4A40";
    private static final String ACC_SIM  = "#3E68A8";
    private static final String ACC_SET  = "#5A6090";

    private static final double CARD_W   = 460;
    private static final int    ROWS     = 4;

    // ── Mutable state ─────────────────────────────────────────────────────────
    private final StackPane root;
    private Canvas bgCanvas;
    private final StackPane arena  = new StackPane();
    private List<VBox> allCards;
    /** order[slot] = card index occupying that slot (0=left, 1=centre, 2=right). */
    private final int[] order = {0, 1, 2};
    private boolean  rotating = false;
    private VBox     openBody = null;
    private final Map<VBox, Timeline> rotTl = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public LandingView(GameController ctrl, BoardView board,
                       Consumer<Boolean> flipSelected, Runnable onTournament) {
        root = new StackPane();
        root.getStyleClass().add("landing-root");

        bgCanvas = new Canvas(1, 1);
        bgCanvas.setMouseTransparent(true);
        root.widthProperty().addListener((o, ov, w)  -> { bgCanvas.setWidth(w.doubleValue());  drawBg(bgCanvas); });
        root.heightProperty().addListener((o, ov, h) -> { bgCanvas.setHeight(h.doubleValue()); drawBg(bgCanvas); });

        ImageView logo = new ImageView(new Image(
            getClass().getResourceAsStream("/images/logos/CHORIDOR_Logo.png")));
        logo.setPreserveRatio(true);
        logo.setFitWidth(310);
        logo.setSmooth(true);

        VBox[] play = card(FontAwesomeSolid.CHESS,  "PLAY",     "Local or vs AI",    ACC_PLAY, "landing-card-play");
        VBox[] sim  = card(FontAwesomeSolid.ROBOT,  "SIMULATE", "Watch AIs compete", ACC_SIM,  "landing-card-sim");
        VBox[] set  = card(FontAwesomeSolid.COG,    "SETTINGS", "Preferences",       ACC_SET,  "landing-card-set");

        VBox playCard = play[0], playBody = play[1];
        VBox simCard  = sim[0],  simBody  = sim[1];
        VBox setCard  = set[0],  setBody  = set[1];
        allCards = List.of(playCard, simCard, setCard);

        populatePlay(playBody, ctrl, board, flipSelected);
        populateSimulate(simBody, ctrl, board, flipSelected, onTournament);
        populateSettings(setBody);

        arena.setAlignment(Pos.CENTER);
        arena.setMinHeight(220);
        for (int i = 0; i < 3; i++) {
            VBox c = allCards.get(i);
            c.setPrefWidth(CARD_W);
            c.setMaxWidth(CARD_W);
            place(c, SLOTS[slotOf(i)]);
            arena.getChildren().add(c);
        }
        bringCenterFront();
        wireCarousel();

        VBox page = new VBox(48, logo, arena);
        page.setAlignment(Pos.TOP_CENTER);
        page.setPadding(new Insets(0, 40, 64, 40));
        page.setMaxWidth(1280);
        page.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane centred = new StackPane(page);
        centred.setAlignment(Pos.CENTER);

        ScrollPane scroll = new ScrollPane(centred);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("landing-scroll");
        centred.minHeightProperty().bind(scroll.heightProperty());

        root.getChildren().addAll(bgCanvas, scroll);
    }

    public StackPane getRoot() { return root; }

    // ── Background board silhouette ───────────────────────────────────────────

    private static void drawBg(Canvas canvas) {
        double cw = canvas.getWidth(), ch = canvas.getHeight();
        if (cw < 1 || ch < 1) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, cw, ch);

        int n = 9;
        double cell = 54, gap = 10, step = 64, total = n * cell + (n - 1) * gap;
        double bp = Math.min(cw, ch) * 0.60;
        double sc = bp / total, cs = cell * sc, gs = gap * sc, ss = step * sc;
        double ox = (cw - bp) / 2.0, oy = (ch - bp) / 2.0;

        // Cell outlines only — filled rects at even 0.018 opacity accumulate too visibly
        g.setStroke(Color.web("#6080C0", 0.038));
        g.setLineWidth(0.6);
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                g.strokeRoundRect(ox + c*ss + 0.3, oy + r*ss + 0.3, cs - 0.6, cs - 0.6, 3*sc, 3*sc);

        // Starting pawn positions — subtle fills
        double pad = cs * 0.22;
        g.setFill(Color.web("#9E4A40", 0.055));
        g.fillOval(ox + 4*ss + pad, oy + 8*ss + pad, cs - 2*pad, cs - 2*pad);
        g.setFill(Color.web("#3E68A8", 0.055));
        g.fillOval(ox + 4*ss + pad, oy + pad, cs - 2*pad, cs - 2*pad);
    }

    // ── Game exit transition ──────────────────────────────────────────────────

    /**
     * Runs {@code gameStart} so the board underneath is live, then fades the
     * landing root out with EASE_IN so it lingers briefly then disappears cleanly.
     * No scale — scaling makes the transition feel artificial.
     */
    private void exitToGame(Runnable gameStart) {
        gameStart.run();
        root.setMouseTransparent(true);
        FadeTransition ft = new FadeTransition(Duration.millis(520), root);
        ft.setToValue(0);
        ft.setInterpolator(Interpolator.EASE_IN);
        ft.setOnFinished(e -> {
            root.setVisible(false);
            root.setOpacity(1);
            root.setMouseTransparent(false);
        });
        ft.play();
    }

    // ── Carousel ──────────────────────────────────────────────────────────────

    private void wireCarousel() {
        for (int i = 0; i < 3; i++) {
            int idx = i;
            VBox c = allCards.get(idx);
            StackPane header = (StackPane) c.getChildren().get(0);
            header.setOnMouseClicked(e -> { if (!rotating) onCardClick(idx); });
            c.setOnMouseEntered(e -> { if (slotOf(idx) != 1 && !rotating) hoverSide(idx, true); });
            c.setOnMouseExited(e ->  { if (slotOf(idx) != 1)               hoverSide(idx, false); });
            c.setCursor(Cursor.HAND);
        }
    }

    private void onCardClick(int cardIdx) {
        if (slotOf(cardIdx) == 1) {
            toggleBody(cardIdx);
        } else {
            if (openBody != null) {
                VBox cc = allCards.get(order[1]);
                collapseBody(cc, openBody);
                new Timeline(new KeyFrame(DUR_EXPAND, e -> rotate(cardIdx))).play();
            } else {
                rotate(cardIdx);
            }
        }
    }

    private void rotate(int targetIdx) {
        int tSlot  = slotOf(targetIdx);
        int shift  = (tSlot == 0) ? 1 : -1;
        int[] nw   = new int[3];
        for (int i = 0; i < 3; i++) nw[((i + shift) % 3 + 3) % 3] = order[i];
        System.arraycopy(nw, 0, order, 0, 3);

        rotating = true;
        for (int p = 0; p < 3; p++) animTo(allCards.get(order[p]), SLOTS[p]);
        new Timeline(new KeyFrame(DUR_ROTATE, e -> { bringCenterFront(); rotating = false; })).play();
    }

    private void animTo(VBox c, Pos3D pos) {
        Timeline prev = rotTl.get(c);
        if (prev != null) prev.stop();
        Timeline tl = new Timeline(new KeyFrame(DUR_ROTATE,
            new KeyValue(c.translateXProperty(), pos.tx(), EASE),
            new KeyValue(c.translateYProperty(), pos.ty(), EASE),
            new KeyValue(c.rotateProperty(),     pos.rot(), EASE),
            new KeyValue(c.scaleXProperty(),     pos.sc(),  EASE),
            new KeyValue(c.scaleYProperty(),     pos.sc(),  EASE),
            new KeyValue(c.opacityProperty(),    pos.op(),  EASE)));
        rotTl.put(c, tl);
        tl.play();
    }

    private void place(VBox c, Pos3D pos) {
        c.setTranslateX(pos.tx()); c.setTranslateY(pos.ty()); c.setRotate(pos.rot());
        c.setScaleX(pos.sc()); c.setScaleY(pos.sc()); c.setOpacity(pos.op());
    }

    private void bringCenterFront() {
        VBox centre = allCards.get(order[1]);
        arena.getChildren().remove(centre);
        arena.getChildren().add(centre);
    }

    private int slotOf(int cardIdx) {
        for (int p = 0; p < 3; p++) if (order[p] == cardIdx) return p;
        return -1;
    }

    private void hoverSide(int cardIdx, boolean enter) {
        Timeline prev = rotTl.get(allCards.get(cardIdx));
        if (prev != null && prev.getStatus() == Animation.Status.RUNNING) return;
        int slot = slotOf(cardIdx);
        Pos3D tgt = enter ? (slot == 0 ? P_L_HOV : P_R_HOV) : SLOTS[slot];
        VBox c = allCards.get(cardIdx);
        new Timeline(new KeyFrame(DUR_HOVER,
            new KeyValue(c.scaleXProperty(),     tgt.sc(),  EASE),
            new KeyValue(c.scaleYProperty(),     tgt.sc(),  EASE),
            new KeyValue(c.opacityProperty(),    tgt.op(),  EASE),
            new KeyValue(c.translateYProperty(), tgt.ty(),  EASE))).play();
    }

    // ── Body expansion ────────────────────────────────────────────────────────

    private void toggleBody(int cardIdx) {
        VBox c = allCards.get(cardIdx);
        VBox b = (VBox) c.getChildren().get(1);
        if (openBody == b) collapseBody(c, b);
        else               expandBody(c, b);
    }

    private void expandBody(VBox card, VBox body) {
        if (openBody != null && openBody != body) collapseBody(allCards.get(order[1]), openBody);
        openBody = body;

        body.setManaged(true);
        body.setVisible(true);
        body.setOpacity(1);
        for (Node child : body.getChildren()) { child.setOpacity(0); child.setTranslateY(-6); }

        // Force a CSS + layout pass so prefHeight is accurate
        card.applyCss();
        card.layout();
        double h = body.prefHeight(CARD_W - 48);
        if (h < 20) h = 220;

        Rectangle clip = new Rectangle(CARD_W, 0);
        body.setClip(clip);

        final double finalH = h;
        Timeline open = new Timeline(new KeyFrame(DUR_EXPAND,
            new KeyValue(clip.heightProperty(), finalH, EASE)));
        open.setOnFinished(ev -> {
            body.setClip(null);
            // Stagger each child settling into place
            List<Node> kids = body.getChildren();
            for (int i = 0; i < kids.size(); i++) {
                Node kid = kids.get(i);
                long delay = i * 55L;
                new Timeline(
                    new KeyFrame(Duration.millis(delay)),
                    new KeyFrame(Duration.millis(delay + DUR_STAGGER.toMillis()),
                        new KeyValue(kid.opacityProperty(),    1.0, EASE),
                        new KeyValue(kid.translateYProperty(), 0.0, EASE))
                ).play();
            }
        });
        open.play();

        // Side cards drift back to create depth
        driftSides(card, true);
        setChevron(card, true);
        card.setStyle("-fx-border-color: " + accent(card) + ";");
    }

    private void collapseBody(VBox card, VBox body) {
        if (openBody == body) openBody = null;

        double fromH = body.getHeight() > 0 ? body.getHeight() : body.prefHeight(CARD_W - 48);
        Rectangle clip = new Rectangle(CARD_W, fromH);
        body.setClip(clip);

        Timeline close = new Timeline(new KeyFrame(DUR_EXPAND,
            new KeyValue(clip.heightProperty(), 0.0,  EASE),
            new KeyValue(body.opacityProperty(), 0.0,  EASE)));
        close.setOnFinished(e -> {
            body.setClip(null);
            body.setManaged(false);
            body.setVisible(false);
            body.setOpacity(1);
            for (Node child : body.getChildren()) { child.setTranslateY(0); child.setOpacity(1); }
        });
        close.play();

        driftSides(card, false);
        setChevron(card, false);
        card.setStyle("");
    }

    /** Animates side cards toward (drift=true) or back from a depth offset. */
    private void driftSides(VBox centreCard, boolean deeper) {
        for (int i = 0; i < allCards.size(); i++) {
            VBox other = allCards.get(i);
            if (other == centreCard) continue;
            int slot = slotOf(i);
            double baseY = SLOTS[slot].ty();
            double targetY = deeper ? baseY + EXPAND_DRIFT : baseY;
            new Timeline(new KeyFrame(DUR_EXPAND,
                new KeyValue(other.translateYProperty(), targetY, EASE))).play();
        }
    }

    private void setChevron(VBox card, boolean up) {
        StackPane h  = (StackPane) card.getChildren().get(0);
        HBox fg      = (HBox) h.getChildren().get(1);
        FontIcon chv = (FontIcon) fg.getChildren().get(2);
        chv.setIconCode(up ? FontAwesomeSolid.CHEVRON_UP : FontAwesomeSolid.CHEVRON_DOWN);
        chv.setIconColor(Color.web(accent(card), up ? 1.0 : 0.25));
    }

    private String accent(VBox card) {
        if (card.getStyleClass().contains("landing-card-play")) return ACC_PLAY;
        if (card.getStyleClass().contains("landing-card-sim"))  return ACC_SIM;
        return ACC_SET;
    }

    // ── Tab switch ────────────────────────────────────────────────────────────

    private void switchTab(VBox body, VBox from, VBox to) {
        FadeTransition fo = new FadeTransition(DUR_FADE_IO, from);
        fo.setToValue(0);
        fo.setOnFinished(ev -> {
            double fromH = body.getHeight();
            from.setManaged(false); from.setVisible(false);
            to.setManaged(true); to.setVisible(true); to.setOpacity(0);
            double toH = Math.max(body.prefHeight(body.getWidth()), 40.0);
            body.setMinHeight(fromH); body.setMaxHeight(fromH);
            Rectangle clip = new Rectangle(CARD_W, fromH);
            body.setClip(clip);
            Timeline grow = new Timeline(new KeyFrame(DUR_GROW,
                new KeyValue(body.minHeightProperty(), toH, EASE),
                new KeyValue(body.maxHeightProperty(), toH, EASE),
                new KeyValue(clip.heightProperty(),    toH, EASE)));
            grow.setOnFinished(e -> {
                body.setClip(null);
                body.setMinHeight(Region.USE_COMPUTED_SIZE);
                body.setMaxHeight(Double.MAX_VALUE);
            });
            grow.play();
            FadeTransition fi = new FadeTransition(Duration.millis(200), to);
            fi.setDelay(Duration.millis(70)); fi.setFromValue(0); fi.setToValue(1); fi.play();
        });
        fo.play();
    }

    // ── Card factory ──────────────────────────────────────────────────────────

    private static VBox[] card(FontAwesomeSolid icon, String title,
                                String sub, String accent, String styleClass) {
        FontIcon fg = new FontIcon(icon);
        fg.setIconSize(26); fg.setIconColor(Color.web(accent));

        Label tl = new Label(title); tl.getStyleClass().add("landing-card-title");
        Label sl = new Label(sub);   sl.getStyleClass().add("landing-card-sub");
        VBox txt = new VBox(7, tl, sl);
        HBox.setHgrow(txt, Priority.ALWAYS);

        FontIcon chv = new FontIcon(FontAwesomeSolid.CHEVRON_DOWN);
        chv.setIconSize(11); chv.setIconColor(Color.web(accent, 0.25));

        HBox fore = new HBox(20, fg, txt, chv);
        fore.setAlignment(Pos.CENTER_LEFT);

        FontIcon wm = new FontIcon(icon);
        wm.setIconSize(130); wm.setIconColor(Color.web(accent, 0.08));
        wm.setMouseTransparent(true);

        StackPane header = new StackPane(wm, fore);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(36, 32, 36, 32));
        header.getStyleClass().add("landing-card-header");
        StackPane.setAlignment(wm, Pos.CENTER_RIGHT);

        VBox body = new VBox(12);
        body.setPadding(new Insets(0, 28, 32, 28));
        body.setManaged(false); body.setVisible(false);
        body.setMinHeight(0);  body.setMaxHeight(0);

        VBox cardBox = new VBox(header, body);
        cardBox.getStyleClass().addAll("landing-card", styleClass);
        return new VBox[]{cardBox, body};
    }

    // ── Body content: Play ────────────────────────────────────────────────────

    private void populatePlay(VBox body, GameController ctrl,
                               BoardView board, Consumer<Boolean> flipSelected) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton hvhTab  = tabBtn("2 Players", tabs);
        ToggleButton vsAiTab = tabBtn("vs AI",     tabs);

        Button startHvH = actionBtn("Start Game", ACC_PLAY);
        VBox hvhPanel = new VBox(startHvH);
        hvhPanel.setPadding(new Insets(18, 0, 0, 0));

        ComboBox<Difficulty> combo = combo();
        ToggleGroup cg = new ToggleGroup();
        ToggleButton pr = dot("color-pick-p1", cg), pb = dot("color-pick-p2", cg);
        pr.setSelected(true);
        HBox cr = new HBox(10, cfgLabel("PLAY AS"), pr, pb);
        cr.setAlignment(Pos.CENTER_LEFT);
        Button startAi = actionBtn("Start Game", ACC_PLAY);
        VBox aiPanel = new VBox(14, cfgLabel("OPPONENT"), combo, cr, startAi);
        aiPanel.setPadding(new Insets(18, 0, 0, 0));
        aiPanel.setManaged(false); aiPanel.setVisible(false); aiPanel.setOpacity(0);

        hvhTab.setSelected(true);
        tabs.selectedToggleProperty().addListener((o, ov, v) -> {
            if (v == hvhTab) switchTab(body, aiPanel, hvhPanel);
            else             switchTab(body, hvhPanel, aiPanel);
        });

        startHvH.setOnAction(e -> {
            ctrl.startGame(null, null, "Player 1", "Player 2");
            board.setFlipped(false); flipSelected.accept(false);
            exitToGame(() -> {});
        });
        startAi.setOnAction(e -> {
            Difficulty d = combo.getValue(); boolean blue = pb.isSelected();
            ctrl.startGame(
                blue ? d.createStrategy(Player.ONE) : null,
                blue ? null : d.createStrategy(Player.TWO),
                blue ? d.sample().displayName() : "Player 1",
                blue ? "Player 2" : d.sample().displayName());
            board.setFlipped(blue); flipSelected.accept(blue);
            exitToGame(() -> {});
        });

        body.getChildren().addAll(tabRow(hvhTab, vsAiTab), hvhPanel, aiPanel);
    }

    // ── Body content: Simulate ────────────────────────────────────────────────

    private void populateSimulate(VBox body, GameController ctrl, BoardView board,
                                   Consumer<Boolean> flipSelected, Runnable onTournament) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton tourTab = tabBtn("Tournament", tabs);
        ToggleButton oneTab  = tabBtn("1 vs 1",     tabs);

        Button launchTour = actionBtn("Launch Tournament", ACC_SIM);
        VBox tourPanel = new VBox(launchTour);
        tourPanel.setPadding(new Insets(18, 0, 0, 0));

        ComboBox<Difficulty> s1 = combo(), s2 = combo();
        if (s2.getItems().size() > 1) s2.getSelectionModel().select(1);
        Button startMatch = actionBtn("Start Match", ACC_SIM);
        VBox onePanel = new VBox(12, cfgLabel("RED AI"), s1, cfgLabel("BLUE AI"), s2, startMatch);
        onePanel.setPadding(new Insets(18, 0, 0, 0));
        onePanel.setManaged(false); onePanel.setVisible(false); onePanel.setOpacity(0);

        tourTab.setSelected(true);
        tabs.selectedToggleProperty().addListener((o, ov, v) -> {
            if (v == tourTab) switchTab(body, onePanel, tourPanel);
            else              switchTab(body, tourPanel, onePanel);
        });

        launchTour.setOnAction(e -> {
            root.setVisible(false);
            if (onTournament != null) onTournament.run();
        });
        startMatch.setOnAction(e -> {
            Difficulty d1 = s1.getValue(), d2 = s2.getValue();
            ctrl.startGame(d1.createStrategy(Player.ONE), d2.createStrategy(Player.TWO),
                d1.sample().displayName(), d2.sample().displayName());
            board.setFlipped(false); flipSelected.accept(false);
            exitToGame(() -> {});
        });

        body.getChildren().addAll(tabRow(tourTab, oneTab), tourPanel, onePanel);
    }

    // ── Body content: Settings ────────────────────────────────────────────────

    private static void populateSettings(VBox body) {
        Label soon = new Label("Coming Soon"); soon.getStyleClass().add("landing-coming-soon");
        Label det  = new Label("Sound, themes, and more."); det.getStyleClass().add("landing-card-sub");
        VBox c = new VBox(10, soon, det); c.setPadding(new Insets(12, 0, 4, 0));
        body.getChildren().add(c);
    }

    // ── Widgets ───────────────────────────────────────────────────────────────

    private static ToggleButton tabBtn(String text, ToggleGroup g) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(g); b.getStyleClass().add("landing-tab-btn");
        HBox.setHgrow(b, Priority.ALWAYS); b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static HBox tabRow(ToggleButton... btns) {
        HBox r = new HBox(0);
        r.getChildren().addAll(btns);
        r.getStyleClass().add("landing-tab-row");
        return r;
    }

    private static Button actionBtn(String text, String accent) {
        Button b = new Button(text); b.getStyleClass().add("landing-action-btn");
        b.setStyle("-fx-background-color:" + accent + ";-fx-border-color:derive(" + accent + ",22%);");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static Label cfgLabel(String t) {
        Label l = new Label(t); l.getStyleClass().add("landing-config-label"); return l;
    }

    private static ComboBox<Difficulty> combo() {
        ComboBox<Difficulty> c = new ComboBox<>();
        c.getItems().addAll(Difficulty.values());
        c.getStyleClass().add("strategy-combo"); c.setMaxWidth(Double.MAX_VALUE);
        c.setVisibleRowCount(ROWS);
        c.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Difficulty d, boolean e) {
                super.updateItem(d, e);
                if (e || d == null) { setGraphic(null); setText(null); return; }
                Label n  = new Label(d.sample().displayName()); n.getStyleClass().add("strategy-name");
                Label ds = new Label(d.sample().description()); ds.getStyleClass().add("strategy-desc"); ds.setWrapText(true);
                setGraphic(new VBox(2, n, ds)); setText(null);
            }
        });
        c.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Difficulty d, boolean e) {
                super.updateItem(d, e);
                setText(e || d == null ? "" : d.sample().displayName());
                setStyle("-fx-text-fill:#8AAADA;-fx-font-weight:bold;-fx-font-size:15px;");
            }
        });
        c.getSelectionModel().selectFirst();
        return c;
    }

    private static ToggleButton dot(String cls, ToggleGroup g) {
        ToggleButton b = new ToggleButton(); b.getStyleClass().addAll("color-pick-button", cls);
        b.setToggleGroup(g); b.setPrefSize(30, 30); b.setMinSize(30, 30); b.setMaxSize(30, 30);
        return b;
    }
}

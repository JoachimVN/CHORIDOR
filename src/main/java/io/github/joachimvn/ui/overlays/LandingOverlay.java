package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full-screen landing page. Three cards in a radial carousel — clicking a side card rotates it
 * to centre, where it auto-expands. Exiting to a game fades the overlay, revealing the live board.
 */
public final class LandingOverlay {

    // ── Carousel positions ────────────────────────────────────────────────────
    private record Pos3D(double tx, double ty, double rot, double sc, double op) {}

    private static final Pos3D P_LEFT   = new Pos3D(-350, 24, -7.0, 0.82, 0.85);
    private static final Pos3D P_CENTER = new Pos3D(0,     0,  0.0, 1.00, 1.00);
    private static final Pos3D P_RIGHT  = new Pos3D( 350, 24,  7.0, 0.82, 0.85);
    private static final Pos3D P_L_HOV  = new Pos3D(-350, 12, -7.0, 0.91, 0.92);
    private static final Pos3D P_R_HOV  = new Pos3D( 350, 12,  7.0, 0.91, 0.92);
    private static final double EXPAND_DRIFT = 22;
    private static final Pos3D[] SLOTS = { P_LEFT, P_CENTER, P_RIGHT };

    // ── Durations ─────────────────────────────────────────────────────────────
    private static final Duration DUR_ROTATE  = Duration.millis(420);
    private static final Duration DUR_EXPAND  = Duration.millis(420);
    private static final Duration DUR_FADE_IO = Duration.millis(110);
    private static final Duration DUR_GROW    = Duration.millis(360);
    private static final Duration DUR_HOVER   = Duration.millis(160);
    private static final Interpolator EASE    = Interpolator.EASE_BOTH;

    // ── Accents ───────────────────────────────────────────────────────────────
    private static final String ACC_PLAY = "#9E4A40";
    private static final String ACC_SIM  = "#3E68A8";
    private static final String ACC_SET  = "#5A6090";

    private static final double CARD_W = 460;
    private static final int    ROWS   = 4;

    // ── Mutable state ─────────────────────────────────────────────────────────
    private final StackPane root;
    private final StackPane arena = new StackPane();
    private final GameController ctrl;
    private List<VBox> allCards;
    /** order[slot] = card index occupying that slot (0=left, 1=centre, 2=right). */
    private final int[] order = {2, 0, 1};
    private boolean rotating = false;
    private VBox    openBody = null;
    private final Map<VBox, Timeline> rotTl  = new HashMap<>();
    private final Map<VBox, Timeline> bodyTl = new HashMap<>();
    private List<Region> indicatorBars;
    private final Map<Region, Timeline> barTl = new HashMap<>();
    private final Map<Region, Color>    barColor = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public LandingOverlay(GameController ctrl, BoardView board,
                       Consumer<Boolean> flipSelected, Runnable onTournament) {
        this.ctrl = ctrl;
        root = new StackPane();
        root.getStyleClass().add("landing-root");

        ImageView logo = new ImageView(new Image(
            getClass().getResourceAsStream("/images/logos/CHORIDOR_Logo.png")));
        logo.setPreserveRatio(true);
        logo.setFitWidth(310);
        logo.setSmooth(true);

        VBox[] play = card(FontAwesomeSolid.PLAY,        "PLAY",     "Local or vs AI",    ACC_PLAY, "landing-card-play");
        VBox[] sim  = card(FontAwesomeSolid.ROBOT,       "SIMULATE", "Watch AIs compete", ACC_SIM,  "landing-card-sim");
        VBox[] set  = card(FontAwesomeSolid.COG,         "SETTINGS", "Preferences",       ACC_SET,  "landing-card-set");

        VBox playCard = play[0];
        VBox playBody = play[1];
        VBox simCard  = sim[0];
        VBox simBody  = sim[1];
        VBox setCard  = set[0];
        VBox setBody  = set[1];
        allCards = List.of(playCard, simCard, setCard);

        populatePlay(playBody, ctrl, board, flipSelected);
        populateSimulate(simBody, ctrl, board, flipSelected, onTournament);
        populateSettings(setBody);

        arena.setAlignment(Pos.TOP_CENTER);
        for (int i = 0; i < 3; i++) {
            VBox c = allCards.get(i);
            c.setPrefWidth(CARD_W);
            c.setMaxWidth(CARD_W);
            c.setMaxHeight(Region.USE_PREF_SIZE);
            place(c, SLOTS[slotOf(i)]);
            arena.getChildren().add(c);
        }
        bringCenterFront();
        wireCarousel();
        wireAutoExpand();

        // Ratchet arena's minHeight upward only — so the indicator below never moves up
        arena.heightProperty().addListener((obs, ov, nv) -> {
            if (nv.doubleValue() > arena.getMinHeight())
                arena.setMinHeight(nv.doubleValue());
        });

        HBox indicator = buildCarouselIndicator();
        updateCursors();
        VBox carouselWithIndicator = new VBox(24, arena, indicator);
        carouselWithIndicator.setAlignment(Pos.TOP_CENTER);

        // Spacer above logo — proportional to viewport height so content sits
        // in a visually balanced position. Bound to scroll.height only (not page.height),
        // so it never changes when cards expand, keeping the card tops fixed.
        Region topSpacer = new Region();
        topSpacer.setMinHeight(0);

        VBox page = new VBox(48, topSpacer, logo, carouselWithIndicator);
        page.setAlignment(Pos.TOP_CENTER);
        page.setPadding(new Insets(0, 40, 64, 40));
        page.setMaxWidth(1280);
        page.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane centred = new StackPane(page);
        centred.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(centred);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("landing-scroll");
        centred.minHeightProperty().bind(scroll.heightProperty());
        topSpacer.prefHeightProperty().bind(scroll.heightProperty().multiply(0.25));

        root.getChildren().add(scroll);
    }

    public StackPane getRoot() { return root; }

    // ── Game exit transition ──────────────────────────────────────────────────

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
            openBody = null;
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
            // cursor set dynamically via updateCursors()
        }
        // Arrow keys navigate the carousel when the landing is visible
        root.sceneProperty().addListener((obs, ov, sc) -> {
            if (sc != null) {
                sc.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (!root.isVisible() || rotating) return;
                    if      (e.getCode() == KeyCode.LEFT)  { onCardClick(order[0]); e.consume(); }
                    else if (e.getCode() == KeyCode.RIGHT) { onCardClick(order[2]); e.consume(); }
                });
            }
        });
    }

    private void wireAutoExpand() {
        // Expand the initial centre card once it has been laid out
        VBox initialCenter = allCards.get(order[1]);
        initialCenter.heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> obs, Number ov, Number nv) {
                if (nv.doubleValue() > 0) {
                    initialCenter.heightProperty().removeListener(this);
                    if (openBody == null)
                        expandBody(initialCenter, (VBox) initialCenter.getChildren().get(1));
                }
            }
        });
        // Re-expand centre card whenever the overlay becomes visible (after game exit)
        root.visibleProperty().addListener((obs, ov, nv) -> {
            if (Boolean.TRUE.equals(nv) && openBody == null) {
                VBox cc = allCards.get(order[1]);
                expandBody(cc, (VBox) cc.getChildren().get(1));
            }
        });
    }

    private void onCardClick(int cardIdx) {
        if (slotOf(cardIdx) != 1) rotate(cardIdx);
        // Centre card: already expanded — action buttons inside handle game start
    }

    private void rotate(int targetIdx) {
        ctrl.playWall();
        // Collapse departing centre immediately (concurrent with rotation)
        if (openBody != null) collapseBody(allCards.get(order[1]), openBody);

        int tSlot = slotOf(targetIdx);
        int shift = (tSlot == 0) ? 1 : -1;
        int[] nw = new int[3];
        for (int i = 0; i < 3; i++) nw[((i + shift) % 3 + 3) % 3] = order[i];
        System.arraycopy(nw, 0, order, 0, 3);

        rotating = true;

        // Bring incoming card to front before animation starts — no z-order snap at landing
        bringCenterFront();

        for (int p = 0; p < 3; p++) animTo(allCards.get(order[p]), SLOTS[p]);

        updateIndicator();
        updateCursors();

        // Expand incoming card immediately — it grows while sliding to centre
        VBox cc = allCards.get(order[1]);
        expandBody(cc, (VBox) cc.getChildren().get(1));

        new Timeline(new KeyFrame(DUR_ROTATE, e -> rotating = false)).play();
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
        Pos3D hovered = slot == 0 ? P_L_HOV : P_R_HOV;
        Pos3D tgt = enter ? hovered : SLOTS[slot];
        VBox c = allCards.get(cardIdx);
        new Timeline(new KeyFrame(DUR_HOVER,
            new KeyValue(c.scaleXProperty(),     tgt.sc(),  EASE),
            new KeyValue(c.scaleYProperty(),     tgt.sc(),  EASE),
            new KeyValue(c.opacityProperty(),    tgt.op(),  EASE),
            new KeyValue(c.translateYProperty(), tgt.ty(),  EASE))).play();
    }

    // ── Body expansion ────────────────────────────────────────────────────────

    private void expandBody(VBox card, VBox body) {
        if (openBody != null && openBody != body) {
            for (VBox c : allCards) {
                if (c.getChildren().get(1) == openBody) { collapseBody(c, openBody); break; }
            }
        }
        openBody = body;

        // Measure natural height before the layout system sees the body
        body.setVisible(true);
        body.setPrefHeight(Region.USE_COMPUTED_SIZE);
        body.applyCss();
        double h = body.prefHeight(CARD_W - 48);
        if (h < 10) h = 220;

        // Add body to layout at zero height so the card doesn't jump
        body.setManaged(true);
        body.setPrefHeight(0);

        // Clip body content so text/controls don't bleed above the growing edge
        Rectangle bodyClip = new Rectangle(CARD_W + 8, 0);
        body.setClip(bodyClip);

        // Clip the entire card so the CSS background gradient grows with the animation
        StackPane header = (StackPane) card.getChildren().get(0);
        double headerH = Math.max(header.getHeight(), 150.0);
        Rectangle cardClip = new Rectangle(CARD_W + 8, headerH);
        card.setClip(cardClip);

        final double fh = h;
        final double fHeaderH = headerH;

        Timeline prev = bodyTl.get(body);
        if (prev != null) prev.stop();

        Timeline open = new Timeline(new KeyFrame(DUR_EXPAND,
            new KeyValue(body.prefHeightProperty(), fh,              EASE),
            new KeyValue(bodyClip.heightProperty(), fh,              EASE),
            new KeyValue(cardClip.heightProperty(), fHeaderH + fh,   EASE)));
        open.setOnFinished(e -> {
            body.setClip(null);
            body.setPrefHeight(Region.USE_COMPUTED_SIZE);
            bodyTl.remove(body);
            // Defer card clip removal by one pulse so USE_COMPUTED_SIZE layout
            // settles before the clip is gone — prevents a last-frame height snap.
            Platform.runLater(() -> card.setClip(null));
        });
        bodyTl.put(body, open);
        open.play();

        if (!rotating) driftSides(card, true);
        card.setStyle("-fx-border-color: " + accent(card) + ";");
    }

    private void collapseBody(VBox card, VBox body) {
        if (openBody == body) openBody = null;

        double fromH = body.getHeight() > 0 ? body.getHeight() : body.prefHeight(CARD_W - 48);
        body.setPrefHeight(fromH);

        Rectangle bodyClip = new Rectangle(CARD_W + 8, fromH);
        body.setClip(bodyClip);

        StackPane header = (StackPane) card.getChildren().get(0);
        double headerH = Math.max(header.getHeight(), 150.0);
        Rectangle cardClip = new Rectangle(CARD_W + 8, headerH + fromH);
        card.setClip(cardClip);

        Timeline prev = bodyTl.get(body);
        if (prev != null) prev.stop();

        Timeline close = new Timeline(new KeyFrame(DUR_EXPAND,
            new KeyValue(body.prefHeightProperty(), 0.0,      EASE),
            new KeyValue(bodyClip.heightProperty(), 0.0,      EASE),
            new KeyValue(cardClip.heightProperty(), headerH,  EASE)));
        close.setOnFinished(e -> {
            body.setClip(null);
            card.setClip(null);
            body.setManaged(false);
            body.setVisible(false);
            bodyTl.remove(body);
        });
        bodyTl.put(body, close);
        close.play();

        if (!rotating) driftSides(card, false);
        card.setStyle("");
    }

    /** Animates side cards toward (deeper=true) or back from a depth offset. */
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

    // Indicator palette — active and inactive colors per card type
    private static final Color IND_PLAY_ON  = Color.web("#C85A50");
    private static final Color IND_PLAY_OFF = Color.web("#5A3A30");
    private static final Color IND_SIM_ON   = Color.web("#5A8FD8");
    private static final Color IND_SIM_OFF  = Color.web("#2A4570");
    private static final Color IND_SET_ON   = Color.web("#9B7FE8");
    private static final Color IND_SET_OFF  = Color.web("#3A2860");
    private static final Duration IND_DUR   = Duration.millis(380);

    // ── Carousel indicator ───────────────────────────────────────────────────

    private HBox buildCarouselIndicator() {
        indicatorBars = new ArrayList<>();
        HBox indicator = new HBox(8);
        indicator.setAlignment(Pos.CENTER);
        indicator.setPrefSize(120, 20);
        indicator.setMaxSize(120, 20);

        int centerCardIdx = order[1];
        for (int i = 0; i < 3; i++) {
            Region bar = new Region();
            bar.setPrefSize(28, 3);
            bar.setMaxSize(28, 3);
            bar.getStyleClass().add("carousel-indicator-bar");
            boolean isCenter = (i == centerCardIdx);
            Color[] palette = accentPalette(allCards.get(i));
            Color initial = isCenter ? palette[0] : palette[1];
            barColor.put(bar, initial);
            bar.setStyle("-fx-background-color: " + toWeb(initial) + ";");
            indicatorBars.add(bar);
            indicator.getChildren().add(bar);
        }
        return indicator;
    }

    private void updateCursors() {
        for (int i = 0; i < 3; i++) {
            VBox card = allCards.get(i);
            Cursor cur = slotOf(i) == 1 ? Cursor.DEFAULT : Cursor.HAND;
            card.setCursor(cur);
            ((StackPane) card.getChildren().get(0)).setCursor(cur);
        }
    }

    private void updateIndicator() {
        int centerCardIdx = order[1];
        VBox centerCard = allCards.get(centerCardIdx);

        for (int i = 0; i < 3; i++) {
            final Region bar = indicatorBars.get(i);
            boolean isCenter = (allCards.get(i) == centerCard);
            Color[] palette = accentPalette(allCards.get(i));
            Color target = isCenter ? palette[0] : palette[1];
            animateBarColor(bar, target);
        }
    }

    private void animateBarColor(Region bar, Color target) {
        Timeline prev = barTl.get(bar);
        if (prev != null) prev.stop();

        Color from = barColor.getOrDefault(bar, target);
        // Interpolate through 60 steps manually — JavaFX can't KeyValue a CSS string property
        final int STEPS = 60;
        KeyFrame[] frames = new KeyFrame[STEPS];
        for (int s = 1; s <= STEPS; s++) {
            double t = (double) s / STEPS;
            Color c = from.interpolate(target, EASE.interpolate(0, 1, t));
            String css = "-fx-background-color: " + toWeb(c) + ";";
            frames[s - 1] = new KeyFrame(IND_DUR.multiply((double) s / STEPS),
                    e -> bar.setStyle(css));
        }
        Timeline tl = new Timeline(frames);
        tl.setOnFinished(e -> {
            barColor.put(bar, target);
            barTl.remove(bar);
        });
        barTl.put(bar, tl);
        tl.play();
    }

    private Color[] accentPalette(VBox card) {
        if (card.getStyleClass().contains("landing-card-play")) return new Color[]{IND_PLAY_ON, IND_PLAY_OFF};
        if (card.getStyleClass().contains("landing-card-sim"))  return new Color[]{IND_SIM_ON,  IND_SIM_OFF};
        return new Color[]{IND_SET_ON, IND_SET_OFF};
    }

    private static String toWeb(Color c) {
        return String.format("rgba(%d,%d,%d,%.3f)",
            (int) Math.round(c.getRed()   * 255),
            (int) Math.round(c.getGreen() * 255),
            (int) Math.round(c.getBlue()  * 255),
            c.getOpacity());
    }

    // ── Card factory ──────────────────────────────────────────────────────────

    private static VBox[] card(FontAwesomeSolid icon, String title,
                                String sub, String accent, String styleClass) {
        Label tl = new Label(title); tl.getStyleClass().add("landing-card-title");
        Label sl = new Label(sub);   sl.getStyleClass().add("landing-card-sub");
        VBox txt = new VBox(7, tl, sl);
        HBox.setHgrow(txt, Priority.ALWAYS);

        HBox fore = new HBox(txt);
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

        ComboBox<Difficulty> combo = combo("#D07068");
        ToggleGroup cg = new ToggleGroup();
        ToggleButton pr = dot("color-pick-p1", cg);
        ToggleButton pb = dot("color-pick-p2", cg);
        pr.setSelected(true);
        HBox cr = new HBox(10, cfgLabel("PLAY AS"), pr, pb);
        cr.setAlignment(Pos.CENTER_LEFT);
        Button startAi = actionBtn("Start Game", ACC_PLAY);
        VBox aiPanel = new VBox(14, cfgLabel("OPPONENT"), combo, cr, startAi);
        aiPanel.setPadding(new Insets(18, 0, 0, 0));
        aiPanel.setManaged(false); aiPanel.setVisible(false); aiPanel.setOpacity(0);

        hvhTab.setSelected(true);
        // Tab buttons play select on switch; the selected-toggle listener handles the visual swap
        hvhTab.setOnAction(e -> ctrl.playSelect());
        vsAiTab.setOnAction(e -> ctrl.playSelect());
        tabs.selectedToggleProperty().addListener((o, ov, v) -> {
            if (v == hvhTab) switchTab(body, aiPanel, hvhPanel);
            else             switchTab(body, hvhPanel, aiPanel);
        });

        // Difficulty combo and color picker play select on interaction
        combo.setOnAction(e -> ctrl.playSelect());
        pr.setOnAction(e -> ctrl.playSelect());
        pb.setOnAction(e -> ctrl.playSelect());

        startHvH.setOnAction(e -> launchHvH(board, flipSelected));
        startAi.setOnAction(e  -> launchVsAi(combo, pb, board, flipSelected));

        body.getChildren().addAll(tabRow(hvhTab, vsAiTab), hvhPanel, aiPanel);
    }

    // ── Body content: Simulate ────────────────────────────────────────────────

    private void populateSimulate(VBox body, GameController ctrl, BoardView board,
                                   Consumer<Boolean> flipSelected, Runnable onTournament) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton oneTab  = tabBtn("1 vs 1",     tabs);
        ToggleButton tourTab = tabBtn("Tournament", tabs);

        ComboBox<Difficulty> s1 = combo("#8AAADA");
        ComboBox<Difficulty> s2 = combo("#8AAADA");
        if (s2.getItems().size() > 1) s2.getSelectionModel().select(1);
        Button startMatch = actionBtn("Start Match", ACC_SIM);
        VBox onePanel = new VBox(12, cfgLabel("RED AI"), s1, cfgLabel("BLUE AI"), s2, startMatch);
        onePanel.setPadding(new Insets(18, 0, 0, 0));

        Button launchTour = actionBtn("Launch Tournament", ACC_SIM);
        VBox tourPanel = new VBox(launchTour);
        tourPanel.setPadding(new Insets(18, 0, 0, 0));
        tourPanel.setManaged(false); tourPanel.setVisible(false); tourPanel.setOpacity(0);

        oneTab.setSelected(true);
        oneTab.setOnAction(e -> ctrl.playSelect());
        tourTab.setOnAction(e -> ctrl.playSelect());
        tabs.selectedToggleProperty().addListener((o, ov, v) -> {
            if (v == oneTab) switchTab(body, tourPanel, onePanel);
            else             switchTab(body, onePanel, tourPanel);
        });

        s1.setOnAction(e -> ctrl.playSelect());
        s2.setOnAction(e -> ctrl.playSelect());

        startMatch.setOnAction(e -> launchSimMatch(s1, s2, board, flipSelected));
        launchTour.setOnAction(e -> {
            ctrl.playSelect();
            root.setVisible(false);
            if (onTournament != null) onTournament.run();
        });

        body.getChildren().addAll(tabRow(oneTab, tourTab), onePanel, tourPanel);
    }

    // ── Body content: Settings ────────────────────────────────────────────────

    private static void populateSettings(VBox body) {
        Label soon = new Label("Coming Soon"); soon.getStyleClass().add("landing-coming-soon");
        Label det  = new Label("Sound, themes, and more."); det.getStyleClass().add("landing-card-sub");
        VBox c = new VBox(10, soon, det); c.setPadding(new Insets(12, 0, 4, 0));
        body.getChildren().add(c);
    }

    // ── Game launch helpers ───────────────────────────────────────────────────

    private void launchHvH(BoardView board, Consumer<Boolean> flipSelected) {
        ctrl.playSelect();
        ctrl.startGame(null, null, "Player 1", "Player 2");
        board.setFlipped(false); flipSelected.accept(false);
        exitToGame(() -> {});
    }

    private void launchVsAi(ComboBox<Difficulty> combo, ToggleButton pb,
                             BoardView board, Consumer<Boolean> flipSelected) {
        ctrl.playSelect();
        Difficulty d = combo.getValue();
        boolean blue = pb.isSelected();
        ctrl.startGame(
            blue ? d.createStrategy(Player.ONE) : null,
            blue ? null : d.createStrategy(Player.TWO),
            blue ? d.sample().displayName() : "Player 1",
            blue ? "Player 2" : d.sample().displayName());
        board.setFlipped(blue); flipSelected.accept(blue);
        exitToGame(() -> {});
    }

    private void launchSimMatch(ComboBox<Difficulty> s1, ComboBox<Difficulty> s2,
                                 BoardView board, Consumer<Boolean> flipSelected) {
        ctrl.playSelect();
        Difficulty d1 = s1.getValue(), d2 = s2.getValue();
        ctrl.startGame(d1.createStrategy(Player.ONE), d2.createStrategy(Player.TWO),
            d1.sample().displayName(), d2.sample().displayName());
        board.setFlipped(false); flipSelected.accept(false);
        exitToGame(() -> {});
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
        b.setStyle(
            "-fx-background-color: linear-gradient(to bottom, derive(" + accent + ",-5%), derive(" + accent + ",-28%));" +
            "-fx-border-color: derive(" + accent + ",18%);"
        );
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static Label cfgLabel(String t) {
        Label l = new Label(t); l.getStyleClass().add("landing-config-label"); return l;
    }

    private static ComboBox<Difficulty> combo(String textFill) {
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
                setStyle("-fx-text-fill:" + textFill + ";-fx-font-weight:bold;-fx-font-size:15px;");
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

package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

/** Full-screen landing page with horizontally-expanding animated mode cards. */
public final class LandingView {

    // Animation timings
    private static final Duration DUR_EXPAND    = Duration.millis(320);
    private static final Duration DUR_COLLAPSE  = Duration.millis(220);
    private static final Duration DUR_FADE_OUT  = Duration.millis(120);
    private static final Duration DUR_GROW      = Duration.millis(360);
    private static final Duration DUR_DIM       = Duration.millis(220);
    private static final Interpolator EASE      = Interpolator.EASE_BOTH;

    // Card accent colours
    private static final String ACCENT_PLAY     = "#9E4A40";
    private static final String ACCENT_SIMULATE = "#3E68A8";
    private static final String ACCENT_SETTINGS = "#5A6090";

    // Width distribution when a card is expanded
    private static final double W_WIDE   = 0.46; // selected card fraction
    private static final double W_NARROW = 0.27; // each other card fraction
    private static final double MAX_W    = 8_000; // "unconstrained" sentinel
    private static final int    COMBO_ROWS = 4;
    private static final double CHEVRON_DIM = 0.25;

    private final StackPane root;
    private HBox cardsHBox;
    private List<VBox> allCards;
    private final Map<Node, Timeline>        widthTl  = new HashMap<>();
    private final Map<Node, FadeTransition>  fadeMap  = new HashMap<>();

    public LandingView(GameController ctrl, BoardView board,
                       Consumer<Boolean> flipSelected, Runnable onTournament) {
        root = new StackPane();
        root.getStyleClass().add("landing-root");

        // ── Board-silhouette background ───────────────────────────────────────
        Canvas bgCanvas = new Canvas(1, 1);
        bgCanvas.setMouseTransparent(true);
        root.widthProperty().addListener((o, ov, w) -> {
            bgCanvas.setWidth(w.doubleValue());
            drawSilhouette(bgCanvas);
        });
        root.heightProperty().addListener((o, ov, h) -> {
            bgCanvas.setHeight(h.doubleValue());
            drawSilhouette(bgCanvas);
        });

        // ── Logo ──────────────────────────────────────────────────────────────
        ImageView logo = new ImageView(new Image(
            getClass().getResourceAsStream("/images/logos/CHORIDOR_Logo.png")));
        logo.setPreserveRatio(true);
        logo.setFitWidth(310);
        logo.setSmooth(true);

        // ── Cards ─────────────────────────────────────────────────────────────
        VBox[] play = makeCard(FontAwesomeSolid.CHESS,   "PLAY",     "Local or vs AI",    ACCENT_PLAY,     "landing-card-play");
        VBox[] sim  = makeCard(FontAwesomeSolid.ROBOT,   "SIMULATE", "Watch AIs compete", ACCENT_SIMULATE, "landing-card-sim");
        VBox[] set  = makeCard(FontAwesomeSolid.COG,     "SETTINGS", "Preferences",       ACCENT_SETTINGS, "landing-card-set");

        VBox playCard = play[0], playBody = play[1];
        VBox simCard  = sim[0],  simBody  = sim[1];
        VBox setCard  = set[0],  setBody  = set[1];
        allCards = List.of(playCard, simCard, setCard);

        populatePlay(playBody, ctrl, board, flipSelected);
        populateSimulate(simBody, ctrl, board, flipSelected, onTournament);
        populateSettings(setBody);

        wireToggle(playCard,  playBody, ACCENT_PLAY);
        wireToggle(simCard,   simBody,  ACCENT_SIMULATE);
        wireToggle(setCard,   setBody,  ACCENT_SETTINGS);

        // ── Layout ────────────────────────────────────────────────────────────
        cardsHBox = new HBox(16, playCard, simCard, setCard);
        cardsHBox.setAlignment(Pos.TOP_CENTER);
        for (VBox c : allCards) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(MAX_W);
        }

        VBox page = new VBox(44, logo, cardsHBox);
        page.setAlignment(Pos.TOP_CENTER);
        page.setPadding(new Insets(0, 40, 64, 40));
        page.setMaxWidth(1180);
        page.setMaxHeight(Region.USE_PREF_SIZE); // required for vertical centering

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

    // ── Board silhouette background ───────────────────────────────────────────

    private static void drawSilhouette(Canvas canvas) {
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, cw, ch);

        int n    = 9;
        double cell = 54, gap = 10, step = 64;
        double total = n * cell + (n - 1) * gap;
        double boardPx = Math.min(cw, ch) * 0.62;
        double sc = boardPx / total;
        double cs = cell * sc, gs = gap * sc, ss = step * sc;
        double ox = (cw - boardPx) / 2.0;
        double oy = (ch - boardPx) / 2.0;

        // Cells
        g.setFill(Color.web("#B0C0FF", 0.020));
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                g.fillRoundRect(ox + c * ss, oy + r * ss, cs, cs, 3 * sc, 3 * sc);

        // Goal strips
        g.setFill(Color.web("#9E4A40", 0.032));
        for (int c = 0; c < n; c++)
            g.fillRect(ox + c * ss, oy + (n - 1) * ss + cs - cs * 3 / 54, cs, cs * 3 / 54);
        g.setFill(Color.web("#3E68A8", 0.032));
        for (int c = 0; c < n; c++)
            g.fillRect(ox + c * ss, oy, cs, cs * 3 / 54);

        // Decorative walls
        g.setFill(Color.web("#9E4A40", 0.042));
        g.fillRoundRect(ox + 2 * ss, oy + 4 * ss + cs, cs * 2 + gs, gs, 2, 2);
        g.fillRoundRect(ox + 5 * ss, oy + 6 * ss + cs, cs * 2 + gs, gs, 2, 2);
        g.setFill(Color.web("#3E68A8", 0.042));
        g.fillRoundRect(ox + 5 * ss + cs, oy + 2 * ss, gs, cs * 2 + gs, 2, 2);
        g.fillRoundRect(ox + 3 * ss + cs, oy + 4 * ss, gs, cs * 2 + gs, 2, 2);

        // Pawns
        double pad = cs * 0.16;
        g.setFill(Color.web("#9E4A40", 0.07));
        g.fillOval(ox + 4 * ss + pad, oy + 8 * ss + pad, cs - 2 * pad, cs - 2 * pad);
        g.setFill(Color.web("#3E68A8", 0.07));
        g.fillOval(ox + 4 * ss + pad, oy + pad, cs - 2 * pad, cs - 2 * pad);
    }

    // ── Card factory ──────────────────────────────────────────────────────────

    /** Returns [card, body]. Body is pre-added to card, hidden. */
    private static VBox[] makeCard(FontAwesomeSolid iconCode, String title,
                                    String subtitle, String accent, String styleClass) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(24);
        icon.setIconColor(Color.web(accent));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("landing-card-title");
        Label subLbl = new Label(subtitle);
        subLbl.getStyleClass().add("landing-card-sub");
        VBox textBox = new VBox(6, titleLbl, subLbl);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        FontIcon chevron = new FontIcon(FontAwesomeSolid.CHEVRON_DOWN);
        chevron.setIconSize(11);
        chevron.setIconColor(Color.web(accent, CHEVRON_DIM));

        HBox foreground = new HBox(18, icon, textBox, chevron);
        foreground.setAlignment(Pos.CENTER_LEFT);

        FontIcon watermark = new FontIcon(iconCode);
        watermark.setIconSize(120);
        watermark.setIconColor(Color.web(accent, 0.08));
        watermark.setMouseTransparent(true);

        StackPane header = new StackPane(watermark, foreground);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(34, 28, 34, 28));
        header.getStyleClass().add("landing-card-header");
        StackPane.setAlignment(watermark, Pos.CENTER_RIGHT);

        VBox body = new VBox(10);
        body.setPadding(new Insets(0, 24, 28, 24));
        body.setManaged(false);
        body.setVisible(false);
        body.setMinHeight(0);
        body.setMaxHeight(0);

        VBox card = new VBox(header, body);
        card.getStyleClass().addAll("landing-card", styleClass);
        return new VBox[]{card, body};
    }

    // ── Card toggle ───────────────────────────────────────────────────────────

    private void wireToggle(VBox card, VBox body, String accent) {
        StackPane header = (StackPane) card.getChildren().get(0);
        HBox fg          = (HBox)      header.getChildren().get(1);
        FontIcon chevron = (FontIcon)  fg.getChildren().get(2);
        header.setOnMouseClicked(e -> {
            boolean wasOpen = body.isManaged();
            collapseAll();
            if (!wasOpen) expandCard(card, body, chevron, accent);
        });
    }

    private void expandCard(VBox card, VBox body, FontIcon chevron, String accent) {
        // Height animation
        body.setManaged(true);
        body.setVisible(true);
        body.setMinHeight(0);
        body.setMaxHeight(0);
        double targetH = Math.max(body.prefHeight(card.getWidth() - 48), 160.0);
        Rectangle clip = new Rectangle(MAX_W, 0);
        body.setClip(clip);
        new Timeline(new KeyFrame(DUR_EXPAND,
            new KeyValue(body.minHeightProperty(), targetH, EASE),
            new KeyValue(body.maxHeightProperty(), targetH, EASE),
            new KeyValue(clip.heightProperty(),    targetH, EASE)))
            .play();
        body.getParent().getParent(); // force layout
        Timeline finish = new Timeline(new KeyFrame(DUR_EXPAND, e2 -> {
            body.setClip(null);
            body.setMinHeight(Region.USE_COMPUTED_SIZE);
            body.setMaxHeight(Double.MAX_VALUE);
        }));
        finish.play();

        // Width animation: expand selected, narrow others
        double avail  = cardsHBox.getWidth() - 32;
        double wideW  = avail * W_WIDE;
        double narrowW = avail * W_NARROW;
        for (VBox c : allCards) {
            double target = (c == card) ? wideW : narrowW;
            animateWidth(c, target);
            if (c != card) fadeTo(c, 0.42);
        }

        // Style
        card.setStyle("-fx-border-color: " + accent + ";"
                    + "-fx-effect: dropshadow(gaussian," + accent + ",22,0.18,0,0);");
        chevron.setIconCode(FontAwesomeSolid.CHEVRON_UP);
        chevron.setIconColor(Color.web(accent));
    }

    private void collapseAll() {
        for (VBox c : allCards) {
            VBox b         = (VBox)      c.getChildren().get(1);
            StackPane h    = (StackPane) c.getChildren().get(0);
            HBox fg        = (HBox)      h.getChildren().get(1);
            FontIcon chv   = (FontIcon)  fg.getChildren().get(2);

            if (b.isManaged()) {
                double fromH = b.getHeight();
                Rectangle clip = new Rectangle(MAX_W, fromH);
                b.setClip(clip);
                b.setMinHeight(fromH);
                b.setMaxHeight(fromH);
                Timeline tl = new Timeline(new KeyFrame(DUR_COLLAPSE,
                    new KeyValue(b.minHeightProperty(), 0, EASE),
                    new KeyValue(b.maxHeightProperty(), 0, EASE),
                    new KeyValue(clip.heightProperty(), 0, EASE)));
                tl.setOnFinished(ev -> {
                    b.setClip(null);
                    b.setManaged(false);
                    b.setVisible(false);
                    b.setMinHeight(0);
                    b.setMaxHeight(0);
                });
                tl.play();
            }

            animateWidth(c, MAX_W);
            fadeTo(c, 1.0);
            c.setStyle("");
            chv.setIconCode(FontAwesomeSolid.CHEVRON_DOWN);
            chv.setIconColor(Color.web(getAccent(c), CHEVRON_DIM));
        }
    }

    private String getAccent(VBox card) {
        if (card.getStyleClass().contains("landing-card-play"))     return ACCENT_PLAY;
        if (card.getStyleClass().contains("landing-card-sim"))      return ACCENT_SIMULATE;
        return ACCENT_SETTINGS;
    }

    private void animateWidth(VBox card, double target) {
        Timeline prev = widthTl.get(card);
        if (prev != null) prev.stop();
        Timeline tl = new Timeline(new KeyFrame(DUR_EXPAND,
            new KeyValue(card.maxWidthProperty(), target, EASE)));
        widthTl.put(card, tl);
        tl.play();
    }

    private void fadeTo(Node node, double target) {
        FadeTransition prev = fadeMap.get(node);
        if (prev != null) prev.stop();
        FadeTransition ft = new FadeTransition(DUR_DIM, node);
        ft.setToValue(target);
        fadeMap.put(node, ft);
        ft.play();
    }

    // ── Animated tab switch ───────────────────────────────────────────────────

    private void switchTab(VBox body, VBox from, VBox to) {
        FadeTransition fo = new FadeTransition(DUR_FADE_OUT, from);
        fo.setToValue(0);
        fo.setOnFinished(ev -> {
            double fromH = body.getHeight();
            from.setManaged(false);
            from.setVisible(false);
            to.setManaged(true);
            to.setVisible(true);
            to.setOpacity(0);

            double toH = Math.max(body.prefHeight(body.getWidth()), 40.0);
            body.setMinHeight(fromH);
            body.setMaxHeight(fromH);
            Rectangle clip = new Rectangle(MAX_W, fromH);
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

            FadeTransition fi = new FadeTransition(Duration.millis(220), to);
            fi.setDelay(Duration.millis(80));
            fi.setFromValue(0);
            fi.setToValue(1);
            fi.play();
        });
        fo.play();
    }

    // ── Play body ─────────────────────────────────────────────────────────────

    private void populatePlay(VBox body, GameController ctrl,
                               BoardView board, Consumer<Boolean> flipSelected) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton hvhTab  = tabBtn("2 Players", tabs);
        ToggleButton vsAiTab = tabBtn("vs AI",     tabs);

        Button startHvH = actionBtn("Start Game", ACCENT_PLAY);
        VBox hvhPanel = new VBox(startHvH);
        hvhPanel.setPadding(new Insets(16, 0, 0, 0));

        ComboBox<Difficulty> stratCombo = stratCombo();
        ToggleGroup colorGroup = new ToggleGroup();
        ToggleButton pickRed  = colorDot("color-pick-p1", colorGroup);
        ToggleButton pickBlue = colorDot("color-pick-p2", colorGroup);
        pickRed.setSelected(true);
        HBox colorRow = new HBox(10, configLabel("PLAY AS"), pickRed, pickBlue);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        Button startVsAi = actionBtn("Start Game", ACCENT_PLAY);
        VBox vsAiPanel = new VBox(14, configLabel("OPPONENT"), stratCombo, colorRow, startVsAi);
        vsAiPanel.setPadding(new Insets(16, 0, 0, 0));
        vsAiPanel.setManaged(false);
        vsAiPanel.setVisible(false);
        vsAiPanel.setOpacity(0);

        hvhTab.setSelected(true);
        tabs.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == hvhTab) switchTab(body, vsAiPanel, hvhPanel);
            else               switchTab(body, hvhPanel, vsAiPanel);
        });

        startHvH.setOnAction(e -> {
            ctrl.startGame(null, null, "Player 1", "Player 2");
            board.setFlipped(false); flipSelected.accept(false);
            root.setVisible(false);
        });
        startVsAi.setOnAction(e -> {
            Difficulty d = stratCombo.getValue();
            boolean blue = pickBlue.isSelected();
            ctrl.startGame(
                blue ? d.createStrategy(Player.ONE) : null,
                blue ? null : d.createStrategy(Player.TWO),
                blue ? d.sample().displayName() : "Player 1",
                blue ? "Player 2" : d.sample().displayName());
            board.setFlipped(blue); flipSelected.accept(blue);
            root.setVisible(false);
        });

        body.getChildren().addAll(tabRow(hvhTab, vsAiTab), hvhPanel, vsAiPanel);
    }

    // ── Simulate body ─────────────────────────────────────────────────────────

    private void populateSimulate(VBox body, GameController ctrl, BoardView board,
                                   Consumer<Boolean> flipSelected, Runnable onTournament) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton tourTab = tabBtn("Tournament", tabs);
        ToggleButton oneTab  = tabBtn("1 vs 1",     tabs);

        Button launchTour = actionBtn("Launch Tournament", ACCENT_SIMULATE);
        VBox tourPanel = new VBox(launchTour);
        tourPanel.setPadding(new Insets(16, 0, 0, 0));

        ComboBox<Difficulty> s1 = stratCombo();
        ComboBox<Difficulty> s2 = stratCombo();
        if (s2.getItems().size() > 1) s2.getSelectionModel().select(1);
        Button startMatch = actionBtn("Start Match", ACCENT_SIMULATE);
        VBox onePanel = new VBox(12,
            configLabel("RED AI"), s1, configLabel("BLUE AI"), s2, startMatch);
        onePanel.setPadding(new Insets(16, 0, 0, 0));
        onePanel.setManaged(false);
        onePanel.setVisible(false);
        onePanel.setOpacity(0);

        tourTab.setSelected(true);
        tabs.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == tourTab) switchTab(body, onePanel, tourPanel);
            else                switchTab(body, tourPanel, onePanel);
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
            root.setVisible(false);
        });

        body.getChildren().addAll(tabRow(tourTab, oneTab), tourPanel, onePanel);
    }

    // ── Settings body ─────────────────────────────────────────────────────────

    private static void populateSettings(VBox body) {
        Label soon   = new Label("Coming Soon");
        soon.getStyleClass().add("landing-coming-soon");
        Label detail = new Label("Sound, themes, and more.");
        detail.getStyleClass().add("landing-card-sub");
        VBox content = new VBox(8, soon, detail);
        content.setPadding(new Insets(10, 0, 4, 0));
        body.getChildren().add(content);
    }

    // ── Widget helpers ────────────────────────────────────────────────────────

    private static ToggleButton tabBtn(String text, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.getStyleClass().add("landing-tab-btn");
        HBox.setHgrow(btn, Priority.ALWAYS);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private static HBox tabRow(ToggleButton... btns) {
        HBox row = new HBox(0);
        row.getChildren().addAll(btns);
        row.getStyleClass().add("landing-tab-row");
        return row;
    }

    private static Button actionBtn(String text, String accent) {
        Button btn = new Button(text);
        btn.getStyleClass().add("landing-action-btn");
        btn.setStyle("-fx-background-color: " + accent + "; "
                   + "-fx-border-color: derive(" + accent + ", 25%);");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private static Label configLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("landing-config-label");
        return lbl;
    }

    private static ComboBox<Difficulty> stratCombo() {
        ComboBox<Difficulty> combo = new ComboBox<>();
        combo.getItems().addAll(Difficulty.values());
        combo.getStyleClass().add("strategy-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setVisibleRowCount(COMBO_ROWS);
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Difficulty d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) { setGraphic(null); setText(null); return; }
                Label name = new Label(d.sample().displayName());
                name.getStyleClass().add("strategy-name");
                Label desc = new Label(d.sample().description());
                desc.getStyleClass().add("strategy-desc");
                desc.setWrapText(true);
                setGraphic(new VBox(2, name, desc));
                setText(null);
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Difficulty d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? "" : d.sample().displayName());
                setStyle("-fx-text-fill: #8AAADA; -fx-font-weight: bold; -fx-font-size: 15px;");
            }
        });
        combo.getSelectionModel().selectFirst();
        return combo;
    }

    private static ToggleButton colorDot(String styleClass, ToggleGroup group) {
        ToggleButton btn = new ToggleButton();
        btn.getStyleClass().addAll("color-pick-button", styleClass);
        btn.setToggleGroup(group);
        btn.setPrefSize(30, 30); btn.setMinSize(30, 30); btn.setMaxSize(30, 30);
        return btn;
    }
}

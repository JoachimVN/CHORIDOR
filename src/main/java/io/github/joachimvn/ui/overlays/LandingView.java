package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full-screen landing page with three expandable mode cards:
 * Play (local / vs AI), Simulate (tournament / 1v1), Settings.
 */
public final class LandingView {

    private static final Duration FAST = Duration.millis(140);
    private static final Duration MED  = Duration.millis(220);
    private static final double   DIM  = 0.35;

    private static final String ACCENT_PLAY     = "#9E4A40";
    private static final String ACCENT_SIMULATE = "#3E68A8";
    private static final String ACCENT_SETTINGS = "#5A608A";
    private static final String CHEVRON_OFF     = "#2A2D40";
    private static final int    COMBO_ROWS      = 4;

    private final StackPane root;
    private List<VBox> allCards;
    private final Map<Node, FadeTransition> transitions = new HashMap<>();

    public LandingView(GameController ctrl, BoardView board,
                       Consumer<Boolean> flipSelected, Runnable onTournament) {
        root = new StackPane();
        root.getStyleClass().add("landing-root");

        // ── Logo ──────────────────────────────────────────────────────────────
        ImageView logo = new ImageView(new Image(
            getClass().getResourceAsStream("/images/logos/CHORIDOR_Logo.png")));
        logo.setPreserveRatio(true);
        logo.setFitWidth(230);
        logo.setSmooth(true);

        // ── Cards ─────────────────────────────────────────────────────────────
        VBox[] play     = makeCard(FontAwesomeSolid.CHESS,  "PLAY",     "Local or vs AI",      ACCENT_PLAY);
        VBox[] simulate = makeCard(FontAwesomeSolid.ROBOT,  "SIMULATE", "Watch AIs compete",   ACCENT_SIMULATE);
        VBox[] settings = makeCard(FontAwesomeSolid.COG,    "SETTINGS", "Preferences",         ACCENT_SETTINGS);

        VBox playCard = play[0], playBody = play[1];
        VBox simCard  = simulate[0], simBody  = simulate[1];
        VBox setCard  = settings[0], setBody  = settings[1];
        allCards = List.of(playCard, simCard, setCard);

        populatePlay(playBody, ctrl, board, flipSelected);
        populateSimulate(simBody, ctrl, board, flipSelected, onTournament);
        populateSettings(setBody);

        wireToggle(playCard,    playBody, ACCENT_PLAY);
        wireToggle(simCard,     simBody,  ACCENT_SIMULATE);
        wireToggle(setCard,     setBody,  ACCENT_SETTINGS);

        // ── Layout ────────────────────────────────────────────────────────────
        HBox cards = new HBox(16, playCard, simCard, setCard);
        cards.setAlignment(Pos.TOP_CENTER);
        for (VBox c : allCards) HBox.setHgrow(c, Priority.ALWAYS);

        VBox page = new VBox(52, logo, cards);
        page.setAlignment(Pos.TOP_CENTER);
        page.setPadding(new Insets(72, 44, 44, 44));
        page.setMaxWidth(1140);

        StackPane centred = new StackPane(page);
        centred.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(centred);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("landing-scroll");
        root.getChildren().add(scroll);
    }

    public StackPane getRoot() { return root; }

    // ── Card factory ──────────────────────────────────────────────────────────

    /** Returns [card, body]. Body is already added to card, initially hidden. */
    private static VBox[] makeCard(FontAwesomeSolid iconCode, String title,
                                    String subtitle, String accent) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(24);
        icon.setIconColor(Color.web(accent));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("landing-card-title");
        Label subLbl = new Label(subtitle);
        subLbl.getStyleClass().add("landing-card-sub");
        VBox text = new VBox(4, titleLbl, subLbl);
        HBox.setHgrow(text, Priority.ALWAYS);

        FontIcon chevron = new FontIcon(FontAwesomeSolid.CHEVRON_DOWN);
        chevron.setIconSize(11);
        chevron.setIconColor(Color.web(CHEVRON_OFF));

        HBox header = new HBox(18, icon, text, chevron);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(26, 22, 26, 22));
        header.getStyleClass().add("landing-card-header");

        VBox body = new VBox(10);
        body.setPadding(new Insets(0, 22, 24, 22));
        body.setManaged(false);
        body.setVisible(false);
        body.setOpacity(0);

        VBox card = new VBox(header, body);
        card.getStyleClass().add("landing-card");
        return new VBox[]{card, body};
    }

    // ── Card toggle ───────────────────────────────────────────────────────────

    private void wireToggle(VBox card, VBox body, String accent) {
        HBox header  = (HBox)     card.getChildren().get(0);
        FontIcon chv = (FontIcon) header.getChildren().get(2);
        header.setOnMouseClicked(e -> {
            boolean wasOpen = body.isManaged();
            collapseAll();
            if (!wasOpen) openCard(card, body, chv, accent);
        });
    }

    private void openCard(VBox card, VBox body, FontIcon chevron, String accent) {
        card.setStyle("-fx-border-color: " + accent + ";");
        chevron.setIconColor(Color.web(accent));
        body.setManaged(true);
        body.setVisible(true);
        fade(body, 0, 1, MED, null);
        for (VBox c : allCards) {
            if (c != card) fade(c, c.getOpacity(), DIM, MED, null);
        }
        fade(card, card.getOpacity(), 1.0, MED, null);
    }

    private void collapseAll() {
        for (VBox c : allCards) {
            VBox b   = (VBox)     c.getChildren().get(1);
            HBox h   = (HBox)     c.getChildren().get(0);
            FontIcon ch = (FontIcon) h.getChildren().get(2);
            if (b.isManaged()) {
                fade(b, 1, 0, FAST, () -> { b.setManaged(false); b.setVisible(false); });
            }
            c.setStyle("");
            ch.setIconColor(Color.web(CHEVRON_OFF));
            fade(c, c.getOpacity(), 1.0, MED, null);
        }
    }

    private void fade(Node node, double from, double to, Duration dur, Runnable onDone) {
        FadeTransition prev = transitions.get(node);
        if (prev != null) prev.stop();
        FadeTransition ft = new FadeTransition(dur, node);
        ft.setFromValue(from);
        ft.setToValue(to);
        if (onDone != null) ft.setOnFinished(e -> onDone.run());
        transitions.put(node, ft);
        ft.play();
    }

    // ── Play body ─────────────────────────────────────────────────────────────

    private void populatePlay(VBox body, GameController ctrl, BoardView board,
                               Consumer<Boolean> flipSelected) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton hvhTab  = tabBtn("2 Players", tabs);
        ToggleButton vsAiTab = tabBtn("vs AI",     tabs);
        HBox tabRow = tabRow(hvhTab, vsAiTab);

        // ── 2-player panel
        Button startHvH = actionBtn("Start Game", ACCENT_PLAY);
        VBox hvhPanel = new VBox(startHvH);
        hvhPanel.setPadding(new Insets(14, 0, 0, 0));

        // ── vs-AI panel
        Label stratLabel = configLabel("OPPONENT");
        ComboBox<Difficulty> stratCombo = stratCombo();

        ToggleGroup colorGroup = new ToggleGroup();
        ToggleButton pickRed  = colorDot("color-pick-p1", colorGroup);
        ToggleButton pickBlue = colorDot("color-pick-p2", colorGroup);
        pickRed.setSelected(true);
        HBox colorRow = new HBox(10, configLabel("PLAY AS"), pickRed, pickBlue);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        Button startVsAi = actionBtn("Start Game", ACCENT_PLAY);
        VBox vsAiPanel = new VBox(14, stratLabel, stratCombo, colorRow, startVsAi);
        vsAiPanel.setPadding(new Insets(14, 0, 0, 0));
        vsAiPanel.setManaged(false);
        vsAiPanel.setVisible(false);
        vsAiPanel.setOpacity(0);

        hvhTab.setSelected(true);

        tabs.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == hvhTab) {
                fade(vsAiPanel, 1, 0, FAST, () -> { vsAiPanel.setManaged(false); vsAiPanel.setVisible(false); });
                hvhPanel.setManaged(true); hvhPanel.setVisible(true);
                fade(hvhPanel, 0, 1, MED, null);
            } else {
                fade(hvhPanel, 1, 0, FAST, () -> { hvhPanel.setManaged(false); hvhPanel.setVisible(false); });
                vsAiPanel.setManaged(true); vsAiPanel.setVisible(true);
                fade(vsAiPanel, 0, 1, MED, null);
            }
        });

        startHvH.setOnAction(e -> {
            ctrl.startGame(null, null, "Player 1", "Player 2");
            board.setFlipped(false); flipSelected.accept(false);
            root.setVisible(false);
        });

        startVsAi.setOnAction(e -> {
            Difficulty d  = stratCombo.getValue();
            boolean blue  = pickBlue.isSelected();
            ctrl.startGame(
                blue ? d.createStrategy(Player.ONE) : null,
                blue ? null : d.createStrategy(Player.TWO),
                blue ? d.sample().displayName() : "Player 1",
                blue ? "Player 2" : d.sample().displayName());
            board.setFlipped(blue); flipSelected.accept(blue);
            root.setVisible(false);
        });

        body.getChildren().addAll(tabRow, hvhPanel, vsAiPanel);
    }

    // ── Simulate body ─────────────────────────────────────────────────────────

    private void populateSimulate(VBox body, GameController ctrl, BoardView board,
                                   Consumer<Boolean> flipSelected, Runnable onTournament) {
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton tourTab = tabBtn("Tournament", tabs);
        ToggleButton oneTab  = tabBtn("1 vs 1",     tabs);
        HBox tabRow = tabRow(tourTab, oneTab);

        // ── Tournament panel
        Button launchTour = actionBtn("Launch Tournament", ACCENT_SIMULATE);
        VBox tourPanel = new VBox(launchTour);
        tourPanel.setPadding(new Insets(14, 0, 0, 0));

        // ── 1v1 panel
        ComboBox<Difficulty> s1 = stratCombo();
        ComboBox<Difficulty> s2 = stratCombo();
        if (s2.getItems().size() > 1) s2.getSelectionModel().select(1);
        Button startMatch = actionBtn("Start Match", ACCENT_SIMULATE);
        VBox onePanel = new VBox(10, configLabel("RED AI"), s1, configLabel("BLUE AI"), s2, startMatch);
        onePanel.setPadding(new Insets(14, 0, 0, 0));
        onePanel.setManaged(false); onePanel.setVisible(false); onePanel.setOpacity(0);

        tourTab.setSelected(true);

        tabs.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == tourTab) {
                fade(onePanel, 1, 0, FAST, () -> { onePanel.setManaged(false); onePanel.setVisible(false); });
                tourPanel.setManaged(true); tourPanel.setVisible(true);
                fade(tourPanel, 0, 1, MED, null);
            } else {
                fade(tourPanel, 1, 0, FAST, () -> { tourPanel.setManaged(false); tourPanel.setVisible(false); });
                onePanel.setManaged(true); onePanel.setVisible(true);
                fade(onePanel, 0, 1, MED, null);
            }
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

        body.getChildren().addAll(tabRow, tourPanel, onePanel);
    }

    // ── Settings body ─────────────────────────────────────────────────────────

    private static void populateSettings(VBox body) {
        Label soon   = new Label("Coming Soon");
        soon.getStyleClass().add("landing-coming-soon");
        Label detail = new Label("Sound, themes, and more.");
        detail.getStyleClass().add("landing-card-sub");
        body.getChildren().addAll(soon, detail);
        body.setPadding(new Insets(4, 22, 28, 22));
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

    private static Button actionBtn(String text, String accentColor) {
        Button btn = new Button(text);
        btn.getStyleClass().add("landing-action-btn");
        btn.setStyle("-fx-background-color: " + accentColor + "; -fx-border-color: derive("
                     + accentColor + ", 30%);");
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

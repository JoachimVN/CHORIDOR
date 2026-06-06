package io.github.joachimvn;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;

import javafx.application.Application;

import javafx.beans.binding.DoubleBinding;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class App extends Application {

    private static final Color  WALL_USED_COLOR    = Color.web("#252838");
    private static final double LOGO_TARGET_HEIGHT = 30;
    private static final double SVG_WIDTH          = 2048;
    private static final double SVG_HEIGHT         = 460;
    private static final Color  LOGO_RED    = Color.web("#9d493f");
    private static final Color  LOGO_BLUE   = Color.web("#3e67a7");
    private static final String CSS_PLAYER1 = "player1";
    private static final String CSS_PLAYER2 = "player2";
    private static final String PADDING_FMT       = "-fx-padding: %.1f %.1f %.1f %.1f;";
    private static final String FONTSIZE_FMT      = "-fx-font-size: %.1fpx; ";
    private static final String SECTION_LABEL_CSS = "setup-section-label";

    @Override
    public void start(Stage stage) {
        GameController ctrl = new GameController();
        BoardView board = new BoardView(ctrl);

        DoubleBinding scaleB = board.widthProperty().divide(board.getWidth());

        // ── Top bar ──────────────────────────────────────────────────────────
        List<Rectangle> p1WallBoxes = buildWallBoxes(BoardView.P1_COLOR, scaleB);
        List<Rectangle> p2WallBoxes = buildWallBoxes(BoardView.P2_COLOR, scaleB);

        Label p1Name = new Label("Player 1");
        Label p2Name = new Label("Player 2");
        HBox p1Side = playerSide(Player.ONE, p1WallBoxes, Pos.CENTER_LEFT,  scaleB, p1Name);
        HBox p2Side = playerSide(Player.TWO, p2WallBoxes, Pos.CENTER_RIGHT, scaleB, p2Name);

        Pane logo = buildLogo(scaleB);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox sidesRow = new HBox(p1Side, topSpacer, p2Side);
        sidesRow.setMaxWidth(Double.MAX_VALUE);
        sidesRow.setAlignment(Pos.CENTER);

        StackPane topBar = new StackPane(sidesRow, logo);
        StackPane.setAlignment(logo, Pos.CENTER);
        topBar.getStyleClass().addAll("chrome-bar", "chrome-bar-top");
        scaleB.addListener((obs, old, nw) -> {
            double s = nw.doubleValue();
            topBar.setStyle(String.format(Locale.ROOT, PADDING_FMT, 10*s, 14*s, 10*s, 14*s));
            sidesRow.setStyle(String.format(Locale.ROOT, "-fx-spacing: %.1f;", 12*s));
        });

        // ── Bottom bar ───────────────────────────────────────────────────────
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        FontIcon flipIcon = new FontIcon(FontAwesomeSolid.SYNC_ALT);
        flipIcon.getStyleClass().add("bar-icon");
        ToggleButton flipButton = new ToggleButton();
        flipButton.setGraphic(flipIcon);
        flipButton.getStyleClass().add("mute-button");
        flipButton.setOnAction(e -> board.setFlipped(flipButton.isSelected()));

        FontIcon muteIcon = new FontIcon(FontAwesomeSolid.VOLUME_UP);
        muteIcon.getStyleClass().add("bar-icon");
        Button muteButton = new Button();
        muteButton.setGraphic(muteIcon);
        muteButton.getStyleClass().add("mute-button");
        muteButton.setOnAction(e -> {
            ctrl.toggleMute();
            muteIcon.setIconCode(ctrl.isMuted() ? FontAwesomeSolid.VOLUME_MUTE : FontAwesomeSolid.VOLUME_UP);
        });

        Button newGame = new Button("Play Again");
        newGame.getStyleClass().add("new-game-button");

        Button changeMode = new Button("Change Mode");
        changeMode.getStyleClass().add("ai-toggle-button");

        Region botSpacer = new Region();
        HBox.setHgrow(botSpacer, Priority.ALWAYS);

        HBox bottomBar = new HBox(statusLabel, botSpacer, flipButton, muteButton, changeMode, newGame);
        bottomBar.getStyleClass().add("chrome-bar");
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        scaleB.addListener((obs, old, nw) -> {
            double s = nw.doubleValue();
            bottomBar.setStyle(String.format(Locale.ROOT,
                PADDING_FMT + " -fx-spacing: %.1f;", 10*s, 14*s, 10*s, 14*s, 12*s));
            statusLabel.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.1fpx;", 13*s));
            newGame.setStyle(String.format(Locale.ROOT,
                FONTSIZE_FMT + PADDING_FMT, 12*s, 5*s, 16*s, 5*s, 16*s));
            changeMode.setStyle(String.format(Locale.ROOT,
                FONTSIZE_FMT + PADDING_FMT, 12*s, 5*s, 16*s, 5*s, 16*s));
            muteIcon.setIconSize((int)(13 * s));
            muteButton.setStyle(String.format(Locale.ROOT, PADDING_FMT, 5*s, 9*s, 5*s, 9*s));
            flipIcon.setIconSize((int)(13 * s));
            flipButton.setStyle(String.format(Locale.ROOT, PADDING_FMT, 5*s, 9*s, 5*s, 9*s));
        });

        // ── Setup overlay ────────────────────────────────────────────────────
        StackPane overlay = buildSetupOverlay(ctrl, board, flipButton);

        newGame.setOnAction(e -> ctrl.replay());
        changeMode.setOnAction(e -> overlay.setVisible(true));

        // ── Wiring ───────────────────────────────────────────────────────────
        ctrl.addListener(() -> {
            board.refresh();
            updateWallBoxes(p1WallBoxes, ctrl.getState(), Player.ONE);
            updateWallBoxes(p2WallBoxes, ctrl.getState(), Player.TWO);
            updateStatus(statusLabel, ctrl);
            p1Name.setText(ctrl.getPlayerName(Player.ONE));
            p2Name.setText(ctrl.getPlayerName(Player.TWO));
        });

        board.refresh();
        updateWallBoxes(p1WallBoxes, ctrl.getState(), Player.ONE);
        updateWallBoxes(p2WallBoxes, ctrl.getState(), Player.TWO);
        updateStatus(statusLabel, ctrl);

        BorderPane gamePane = new BorderPane(board);
        gamePane.setTop(topBar);
        gamePane.setBottom(bottomBar);

        StackPane sceneRoot = new StackPane(gamePane, overlay);

        Scene scene = new Scene(sceneRoot);
        scene.getStylesheets().add(
            getClass().getResource("/css/app.css").toExternalForm()
        );
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && ctrl.isGameOver()) {
                overlay.setVisible(true);
                e.consume();
            }
        });

        stage.getIcons().add(new Image(getClass().getResourceAsStream(
            "/images/logos/Choridor_Logo_Square_White.png")));
        stage.setTitle("CHORIDOR");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(300);
        stage.setMinHeight(360);
        stage.show();
    }

    // ── Setup overlay ─────────────────────────────────────────────────────────

    private StackPane buildSetupOverlay(GameController ctrl, BoardView board, ToggleButton flipButton) {
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("setup-overlay");

        // Title — SVG logo at fixed size
        HBox titleRow = new HBox(buildLogoFixed(65));
        titleRow.setAlignment(Pos.CENTER);

        // Mode row
        ToggleGroup modeGroup   = new ToggleGroup();
        ToggleButton modeHvH    = modeBtn("2 Players", modeGroup);
        ToggleButton modeHvAI   = modeBtn("vs AI",     modeGroup);
        ToggleButton modeAiVsAi = modeBtn("AI vs AI",  modeGroup);
        modeHvH.setSelected(true);
        HBox modeRow = new HBox(10, modeHvH, modeHvAI, modeAiVsAi);
        modeRow.setAlignment(Pos.CENTER);

        // AI 1 strategy
        Label ai1Label = new Label("AI");
        ai1Label.getStyleClass().add(SECTION_LABEL_CSS);
        ComboBox<Difficulty> strat1List = strategyCombo();
        VBox ai1Box = new VBox(10, ai1Label, strat1List);
        ai1Box.setAlignment(Pos.CENTER);

        // AI 2 strategy (AI vs AI only)
        Label ai2Label = new Label("BLUE AI");
        ai2Label.getStyleClass().add(SECTION_LABEL_CSS);
        ComboBox<Difficulty> strat2List = strategyCombo();
        VBox ai2Box = new VBox(10, ai2Label, strat2List);
        ai2Box.setAlignment(Pos.CENTER);
        ai2Box.setVisible(false);
        ai2Box.setManaged(false);

        // Color picker (vs AI only)
        ToggleGroup colorGroup = new ToggleGroup();
        ToggleButton pickRed  = colorBtn("color-pick-p1", colorGroup);
        ToggleButton pickBlue = colorBtn("color-pick-p2", colorGroup);
        pickRed.setSelected(true);
        for (ToggleButton btn : List.of(pickRed, pickBlue)) {
            btn.setPrefSize(30, 30);
            btn.setMinSize(30, 30);
            btn.setMaxSize(30, 30);
        }
        Label colorLabel = new Label("PLAY AS");
        colorLabel.getStyleClass().add(SECTION_LABEL_CSS);
        HBox colorRow = new HBox(12, pickRed, pickBlue);
        colorRow.setAlignment(Pos.CENTER);
        VBox colorBox = new VBox(10, colorLabel, colorRow);
        colorBox.setAlignment(Pos.CENTER);
        colorBox.setVisible(false);
        colorBox.setManaged(false);

        Region sep = new Region();
        sep.getStyleClass().add("overlay-separator");
        sep.setVisible(false);
        sep.setManaged(false);

        VBox aiSection = new VBox(22, ai1Box, ai2Box, colorBox);
        aiSection.setAlignment(Pos.CENTER);
        aiSection.setVisible(false);
        aiSection.setManaged(false);

        modeGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            boolean vsAI   = sel == modeHvAI;
            boolean aiVsAi = sel == modeAiVsAi;
            boolean hasAi  = vsAI || aiVsAi;
            aiSection.setVisible(hasAi);
            aiSection.setManaged(hasAi);
            sep.setVisible(hasAi);
            sep.setManaged(hasAi);
            ai2Box.setVisible(aiVsAi);
            ai2Box.setManaged(aiVsAi);
            colorBox.setVisible(vsAI);
            colorBox.setManaged(vsAI);
            ai1Label.setText(aiVsAi ? "RED AI" : "AI");
        });

        // Start button
        Button startBtn = new Button("Start Game");
        startBtn.getStyleClass().add("start-button");
        startBtn.setMaxWidth(Double.MAX_VALUE);
        startBtn.setOnAction(e -> {
            Toggle sel = modeGroup.getSelectedToggle();
            Strategy s1 = null;
            Strategy s2 = null;
            Player human = Player.ONE;
            String p1Name = "Player 1";
            String p2Name = "Player 2";
            if (sel == modeHvAI) {
                Difficulty d = selectedDifficulty(strat1List);
                human = pickBlue.isSelected() ? Player.TWO : Player.ONE;
                if (human == Player.ONE) s2 = d.createStrategy(Player.TWO);
                else                     s1 = d.createStrategy(Player.ONE);
            } else if (sel == modeAiVsAi) {
                Difficulty d1 = selectedDifficulty(strat1List);
                Difficulty d2 = selectedDifficulty(strat2List);
                s1 = d1.createStrategy(Player.ONE);
                s2 = d2.createStrategy(Player.TWO);
                p1Name = d1.sample().displayName();
                p2Name = d2.sample().displayName();
            }
            ctrl.startGame(s1, s2, human, p1Name, p2Name);
            boolean flip = sel == modeHvAI && human == Player.TWO;
            board.setFlipped(flip);
            flipButton.setSelected(flip);
            overlay.setVisible(false);
        });

        VBox card = new VBox(28, titleRow, modeRow, sep, aiSection, startBtn);
        card.getStyleClass().add("setup-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(700);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        // Center card and allow scrolling on small screens
        StackPane scrollContent = new StackPane(card);
        scrollContent.setAlignment(Pos.CENTER);
        ScrollPane scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("setup-scroll");
        scrollContent.minHeightProperty().bind(scroll.heightProperty());

        overlay.getChildren().add(scroll);
        return overlay;
    }

    private ToggleButton modeBtn(String text, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.getStyleClass().add("mode-button");
        return btn;
    }

    private ComboBox<Difficulty> strategyCombo() {
        ComboBox<Difficulty> combo = new ComboBox<>();
        combo.getItems().addAll(Difficulty.values());
        combo.getStyleClass().add("strategy-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Difficulty d, boolean empty) {
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
            @Override
            protected void updateItem(Difficulty d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? "" : d.sample().displayName());
                setStyle("-fx-text-fill: #8AAADA; -fx-font-weight: bold; -fx-font-size: 15px;");
            }
        });
        combo.getSelectionModel().selectLast();
        return combo;
    }

    private static Difficulty selectedDifficulty(ComboBox<Difficulty> combo) {
        return combo.getValue();
    }

    private ToggleButton colorBtn(String colorClass, ToggleGroup group) {
        ToggleButton btn = new ToggleButton();
        btn.getStyleClass().addAll("color-pick-button", colorClass);
        btn.setToggleGroup(group);
        btn.setPrefSize(20, 20);
        btn.setMinSize(20, 20);
        btn.setMaxSize(20, 20);
        return btn;
    }

    // ── Logo ─────────────────────────────────────────────────────────────────

    private static String[][] buildLogoPaths() {
        return new String[][] {
            { "m128.5 321.2q14 0 21.8-2.9 8-2.9 12.4-7.1 4.4-4.2 7.7-8.4 3-3.8 4.9-5.7 2.1-2 5.8-2.7 3-1 9.7-1.2 6.9-0.3 13.6-0.3 6.9 0 9.5 0.3 4 0.2 5.6 1.6 1.5 1.3 1.9 5.7 0.5 6.1 0.5 14.5 0.2 8.4-0.1 14.6-0.2 1.9-0.8 3.2-0.4 1.4-1.9 4-1.9 3.1-7.5 9-5.5 5.8-16.2 12.1-10.7 6.1-27.7 10.5-16.8 4.4-41.4 4.4-26.4 0-49.3-6.7-22.9-6.9-40.3-23-17.2-16-27-43.4-9.5-27.3-9.5-68.2 0-45.5 15.7-77.3 15.8-31.9 44.8-48.5 29.1-16.7 68.8-16.7 27.9 0 44.7 5.8 17 5.7 25.8 13.5 8.8 7.7 12.4 13.4 1.3 2.1 1.9 3.8 0.6 1.6 0.8 5 0.5 4.6 0.2 13.4-0.2 8.8-1.4 15.5-0.7 4.2-2.5 5.5-0.7 0.8-2.6 1.4-1.9 0.5-5.8 0.5-6.1 0-13.7-0.3-7.7-0.6-11.5-1.2-4.6-0.7-6.7-2.1-2.1-1.5-4.2-4.6-3.4-4.9-11.8-11.5-8.4-6.5-27.7-6.5-27.1 0-44.7 21.1-17.6 20.8-17.6 67.7 0 46.6 18.4 69.2 18.5 22.6 45 22.6zm311.2-118v-78.8q0-5.5 1.9-8.4 8.6-12.6 24.1-26.6 2.9-2.3 5-2.3 2.2 0 4.7 2.3 8.4 7.3 14.2 14 5.7 6.5 9.9 12.6 1.9 3 1.9 8.4v233.5q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.7 0-8.2-2.1-2.3-2.3-2.3-8v-101.6h-113.4v101.6q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.8 0-8.2-2.1-2.3-2.3-2.3-8v-254q0-5.7 2.3-7.8 2.4-2.3 8.2-2.3h40.7q5.7 0 8 2.3 2.5 2.1 2.5 7.8v99.3zm240.3 169.6q-39.8 0-69.2-15.3-29.4-15.3-45.6-46.1-16.1-30.8-16.1-76.9 0-73.2 35.8-109.1 35.7-36.2 99.5-36.2 59.7 0 94.5 35.4 34.8 35.2 34.8 104.4 0 48.9-16.3 80.9-16.2 31.7-46.2 47.4-30 15.5-71.2 15.5zm1.1-51.6q34.4 0 51-24.7 16.6-24.7 16.6-64.1 0-45.7-15.6-68.6-15.7-23-50.5-23-68.5 0-68.5 90.5 0 40.3 16.4 65.2 16.6 24.7 50.6 24.7zm242.1-156.3v193q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.7 0-8.2-2.1-2.3-2.3-2.3-8v-212.9q0-19.1 3.4-30.2 3.7-11.2 13.8-16 10.3-5 30-5h50.6q99.7 0 99.7 76.7 0 23.3-7.8 38.2-7.9 14.9-19.5 23.3-11.7 8.3-22.9 12.5v0.9q12.8 8.4 24.2 22.2 11.7 13.6 21 29.7 9.4 15.8 15.9 31.5 6.5 15.5 9 27.7 1.1 5.8 0 8.6-1 2.9-6.5 2.9h-44.1q-5 0-8.8-2.1-3.7-2.1-6.7-9.8-14.5-36.5-33.4-59.4-19-23.2-36.5-37.1-11.3-9-11.3-12.8 0-3.1 3.6-8.4 2.3-3.3 6.7-7.9 4.4-4.6 7.7-6.9 3.2-2.3 5.1-2.8 2.1-0.6 6.3-0.6 14.2 0 23.5-9.8 9.6-9.7 9.6-30.7 0-19.9-10.2-29.1-9.9-9.4-27.5-9.4h-11.4q-12.6 0-17.2 5.4-4.6 5.3-4.6 18.3z", "s0" },
            { "m1178.3 124.4v233.5q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.8 0-8.2-2.1-2.3-2.3-2.3-8v-233.5q0-5.5 1.9-8.4 8.6-12.6 24-26.6 2.9-2.3 5-2.3 2.3 0 4.8 2.3 8.4 7.3 14.1 14 5.7 6.5 9.9 12.6 2 3 2 8.4zm167.1 243.6h-58.7q-19.6 0-29.9-5-10.2-4.9-13.8-16.2-3.4-11.3-3.4-30v-171.8q0-19.1 3.4-30.2 3.6-11.2 13.8-16 10.3-5 29.9-5h58.7q41 0 71.8 12.2 30.9 12.3 48.1 41.5 17.2 29.1 17.2 80 0 50.8-17.2 81.8-17.2 30.8-48.1 44.7-30.8 14-71.8 14zm-14-223.9h-8.4q-12.6 0-17.2 5.3-4.5 5.4-4.5 18.4v126.2q0 12.8 4.5 18.4 4.6 5.5 17.2 5.5h8.8q40.9 0 63.2-20.1 22.6-20.1 22.6-69.6 0-33.1-10.3-51.2-10.2-18.4-29.5-25.6-19.2-7.3-46.4-7.3zm315.6 228.7q-39.7 0-69.1-15.3-29.4-15.3-45.7-46.1-16-30.8-16-76.9 0-73.2 35.7-109.1 35.7-36.2 99.5-36.2 59.8 0 94.6 35.4 34.7 35.2 34.7 104.4 0 48.9-16.2 80.9-16.2 31.7-46.2 47.4-30 15.5-71.3 15.5zm1.2-51.6q34.4 0 51-24.7 16.6-24.7 16.6-64.1 0-45.7-15.7-68.6-15.6-23-50.4-23-68.6 0-68.6 90.5 0 40.3 16.5 65.2 16.6 24.7 50.6 24.7zm242.1-156.3v193q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.8 0-8.2-2.1-2.3-2.3-2.3-8v-212.9q0-19.1 3.4-30.2 3.6-11.2 13.8-16 10.3-5 29.9-5h50.7q99.7 0 99.7 76.7 0 23.3-7.9 38.2-7.8 14.9-19.5 23.3-11.6 8.3-22.9 12.5v0.9q12.8 8.4 24.3 22.2 11.6 13.6 21 29.7 9.4 15.8 15.8 31.5 6.5 15.5 9 27.7 1.2 5.8 0 8.6-0.9 2.9-6.5 2.9h-44.1q-5 0-8.8-2.1-3.6-2.1-6.7-9.8-14.5-36.5-33.4-59.4-18.9-23.2-36.5-37.1-11.2-9-11.2-12.8 0-3.1 3.6-8.4 2.3-3.3 6.7-7.9 4.4-4.6 7.6-6.9 3.3-2.3 5.2-2.8 2.1-0.6 6.3-0.6 14.1 0 23.5-9.8 9.5-9.7 9.5-30.7 0-19.9-10.1-29.1-9.9-9.4-27.5-9.4h-11.5q-12.6 0-17.2 5.4-4.5 5.3-4.5 18.3z", "s1" },
            { "m1178 411.84v34.16c0 7.73-6.27 14-14 14h-1150c-7.73 0-14-6.27-14-14v-34.16c0-7.73 6.27-14 14-14h1150c7.73 0 14 6.27 14 14z", "s0" },
            { "m2048 14v34.16c0 7.73-6.27 14-14 14h-903c-7.73 0-14-6.27-14-14v-34.16c0-7.73 6.27-14 14-14h903c7.73 0 14 6.27 14 14z", "s1" },
            { "m1164.67 460h-34.09c-7.73 0-14-6.27-14-14v-202c0-7.73 6.27-14 14-14h34.09c7.73 0 14 6.27 14 14v202c0 7.73-6.27 14-14 14z", "s0" },
            { "m1164.67 230h-34.09c-7.73 0-14-6.27-14-14v-169c0-7.73 6.27-14 14-14h34.09c7.73 0 14 6.27 14 14v169c0 7.73-6.27 14-14 14z", "s1" },
        };
    }

    private static Pane buildLogo(DoubleBinding scaleB) {
        String[][] paths = buildLogoPaths();
        Group group = buildLogoGroup(paths);

        double baseLogoWidth = SVG_WIDTH * LOGO_TARGET_HEIGHT / SVG_HEIGHT;
        Scale scaleTransform = new Scale();
        scaleTransform.xProperty().bind(scaleB.multiply(LOGO_TARGET_HEIGHT / SVG_HEIGHT));
        scaleTransform.yProperty().bind(scaleB.multiply(LOGO_TARGET_HEIGHT / SVG_HEIGHT));
        group.getTransforms().add(scaleTransform);

        Pane pane = new Pane(group);
        pane.prefWidthProperty().bind(scaleB.multiply(baseLogoWidth));
        pane.prefHeightProperty().bind(scaleB.multiply(LOGO_TARGET_HEIGHT));
        pane.minWidthProperty().bind(pane.prefWidthProperty());
        pane.maxWidthProperty().bind(pane.prefWidthProperty());
        pane.minHeightProperty().bind(pane.prefHeightProperty());
        pane.maxHeightProperty().bind(pane.prefHeightProperty());
        return pane;
    }

    private static Pane buildLogoFixed(double targetHeight) {
        String[][] paths = buildLogoPaths();
        Group group = buildLogoGroup(paths);
        double scale = targetHeight / SVG_HEIGHT;
        group.getTransforms().add(new Scale(scale, scale));
        double w = SVG_WIDTH * scale;
        Pane pane = new Pane(group);
        pane.setPrefSize(w, targetHeight);
        pane.setMinSize(w, targetHeight);
        pane.setMaxSize(w, targetHeight);
        return pane;
    }

    private static Group buildLogoGroup(String[][] paths) {
        Group group = new Group();
        for (String[] entry : paths) {
            SVGPath p = new SVGPath();
            p.setContent(entry[0]);
            p.setFillRule(FillRule.EVEN_ODD);
            p.setFill("s1".equals(entry[1]) ? LOGO_BLUE : LOGO_RED);
            group.getChildren().add(p);
        }
        return group;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Rectangle> buildWallBoxes(Color color, DoubleBinding scaleB) {
        List<Rectangle> boxes = new ArrayList<>();
        for (int i = 0; i < GameState.WALLS_PER_PLAYER; i++) {
            Rectangle r = new Rectangle();
            r.widthProperty().bind(scaleB.multiply(7));
            r.heightProperty().bind(scaleB.multiply(18));
            r.arcWidthProperty().bind(scaleB.multiply(2));
            r.arcHeightProperty().bind(scaleB.multiply(2));
            r.setFill(color);
            boxes.add(r);
        }
        return boxes;
    }

    private HBox playerSide(Player player, List<Rectangle> boxes, Pos alignment, DoubleBinding scaleB, Label name) {
        Color color = player == Player.ONE ? BoardView.P1_COLOR : BoardView.P2_COLOR;

        Rectangle dot = new Rectangle();
        dot.widthProperty().bind(scaleB.multiply(8));
        dot.heightProperty().bind(scaleB.multiply(8));
        dot.arcWidthProperty().bind(scaleB.multiply(8));
        dot.arcHeightProperty().bind(scaleB.multiply(8));
        dot.setFill(color);

        name.getStyleClass().add("player-label");
        scaleB.addListener((obs, old, nw) ->
            name.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.1fpx;", 12 * nw.doubleValue())));

        HBox wallRow = new HBox();
        wallRow.setAlignment(Pos.CENTER);
        wallRow.spacingProperty().bind(scaleB.multiply(3));
        wallRow.getChildren().addAll(boxes);

        HBox side = new HBox();
        side.setAlignment(alignment);
        side.spacingProperty().bind(scaleB.multiply(8));
        scaleB.addListener((obs, old, nw) ->
            side.setPadding(new Insets(0, 4 * nw.doubleValue(), 0, 4 * nw.doubleValue())));
        side.setPadding(new Insets(0, 4, 0, 4));
        side.getChildren().addAll(dot, name, wallRow);
        return side;
    }

    private void updateWallBoxes(List<Rectangle> boxes, GameState state, Player player) {
        int remaining = state.getWallCount(player);
        Color activeColor = player == Player.ONE ? BoardView.P1_COLOR : BoardView.P2_COLOR;
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).setFill(i < remaining ? activeColor : WALL_USED_COLOR);
        }
    }

    private void updateStatus(Label label, GameController ctrl) {
        label.getStyleClass().removeAll(CSS_PLAYER1, CSS_PLAYER2);
        if (ctrl.isAiThinking()) {
            Player current = ctrl.getState().getCurrentPlayer();
            String thinkText = ctrl.isAiVsAi()
                ? ctrl.getPlayerName(current) + " is thinking..."
                : "AI is thinking...";
            label.setText(thinkText);
            label.getStyleClass().add(current == Player.ONE ? CSS_PLAYER1 : CSS_PLAYER2);
        } else if (ctrl.isGameOver()) {
            Player winner = ctrl.getWinner();
            label.setText(ctrl.getStatusText());
            label.getStyleClass().add(winner == Player.ONE ? CSS_PLAYER1 : CSS_PLAYER2);
        } else {
            Player p = ctrl.getState().getCurrentPlayer();
            label.setText(ctrl.getStatusText());
            label.getStyleClass().add(p == Player.ONE ? CSS_PLAYER1 : CSS_PLAYER2);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

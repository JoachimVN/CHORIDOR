package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.common.LogoFactory;
import io.github.joachimvn.ui.common.UiConstants;
import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.Player;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Pre-game setup card: pick 2-player / vs AI / AI-vs-AI, choose strategies and colour, then Start. */
public final class SetupOverlay {

    private final StackPane root;

    /**
     * @param flipSelected  called with the desired flip state when a game starts
     * @param onTournament  called when the user clicks the Tournament button
     */
    public SetupOverlay(GameController ctrl, BoardView board, Consumer<Boolean> flipSelected,
                        Runnable onTournament) {
        root = new StackPane();
        root.getStyleClass().add("setup-overlay");

        // Title — SVG logo at fixed size
        HBox titleRow = new HBox(LogoFactory.fixed(65));
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
        ai1Label.getStyleClass().add(UiConstants.SECTION_LABEL_CSS);
        ComboBox<Difficulty> strat1List = strategyCombo();
        strat1List.valueProperty().addListener((obs, old, sel) -> { if (sel != null) ctrl.playSelect(); });
        VBox ai1Box = new VBox(10, ai1Label, strat1List);
        ai1Box.setAlignment(Pos.CENTER);

        // AI 2 strategy (AI vs AI only)
        Label ai2Label = new Label("BLUE AI");
        ai2Label.getStyleClass().add(UiConstants.SECTION_LABEL_CSS);
        ComboBox<Difficulty> strat2List = strategyCombo();
        strat2List.valueProperty().addListener((obs, old, sel) -> { if (sel != null) ctrl.playSelect(); });
        VBox ai2Box = new VBox(10, ai2Label, strat2List);
        ai2Box.setAlignment(Pos.CENTER);
        ai2Box.setVisible(false);
        ai2Box.setManaged(false);

        // Color picker (vs AI only)
        ToggleGroup colorGroup = new ToggleGroup();
        ToggleButton pickRed  = colorBtn("color-pick-p1", colorGroup);
        ToggleButton pickBlue = colorBtn("color-pick-p2", colorGroup);
        pickRed.setSelected(true);
        colorGroup.selectedToggleProperty().addListener((obs, old, sel) -> { if (sel != null) ctrl.playSelect(); });
        Label colorLabel = new Label("PLAY AS");
        colorLabel.getStyleClass().add(UiConstants.SECTION_LABEL_CSS);
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
            if (sel != null) ctrl.playSelect();
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
                Difficulty d = strat1List.getValue();
                human = pickBlue.isSelected() ? Player.TWO : Player.ONE;
                if (human == Player.ONE) s2 = d.createStrategy(Player.TWO);
                else                     s1 = d.createStrategy(Player.ONE);
            } else if (sel == modeAiVsAi) {
                Difficulty d1 = strat1List.getValue();
                Difficulty d2 = strat2List.getValue();
                s1 = d1.createStrategy(Player.ONE);
                s2 = d2.createStrategy(Player.TWO);
                p1Name = d1.sample().displayName();
                p2Name = d2.sample().displayName();
            }
            ctrl.startGame(s1, s2, p1Name, p2Name);
            boolean flip = sel == modeHvAI && human == Player.TWO;
            board.setFlipped(flip);
            flipSelected.accept(flip);
            root.setVisible(false);
        });

        Button tournamentBtn = new Button("Tournament");
        tournamentBtn.getStyleClass().add("tournament-mode-btn");
        tournamentBtn.setMaxWidth(Double.MAX_VALUE);
        tournamentBtn.setOnAction(e -> {
            ctrl.playSelect();
            root.setVisible(false);
            if (onTournament != null) onTournament.run();
        });

        VBox card = new VBox(28, titleRow, modeRow, sep, aiSection, startBtn, tournamentBtn);
        card.getStyleClass().add("setup-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(700);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        // Center card and allow scrolling on small screens
        StackPane scrollContent = new StackPane(card);
        scrollContent.setAlignment(Pos.CENTER);
        ScrollPane scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("setup-scroll");
        scrollContent.minHeightProperty().bind(scroll.heightProperty());

        root.getChildren().add(scroll);
    }

    public StackPane getRoot() { return root; }

    private static ToggleButton modeBtn(String text, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.getStyleClass().add("mode-button");
        return btn;
    }

    /** Show at most this many strategy rows; the rest scroll, so the popup never runs off-screen. */
    private static final int STRATEGY_VISIBLE_ROWS = 4;

    private static ComboBox<Difficulty> strategyCombo() {
        ComboBox<Difficulty> combo = new ComboBox<>();
        combo.getItems().addAll(Difficulty.values());
        combo.getStyleClass().add("strategy-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        // Cap the dropdown height regardless of how many strategies exist (the CSS max-height on the
        // popup list-view alone isn't honoured, so the row count is the reliable bound).
        combo.setVisibleRowCount(STRATEGY_VISIBLE_ROWS);
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
        combo.getSelectionModel().selectFirst();
        return combo;
    }

    private static ToggleButton colorBtn(String colorClass, ToggleGroup group) {
        ToggleButton btn = new ToggleButton();
        btn.getStyleClass().addAll("color-pick-button", colorClass);
        btn.setToggleGroup(group);
        btn.setPrefSize(30, 30);
        btn.setMinSize(30, 30);
        btn.setMaxSize(30, 30);
        return btn;
    }
}

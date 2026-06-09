package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.tournament.TournamentRunner;
import io.github.joachimvn.ui.GameController;
import javafx.animation.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import javafx.scene.media.AudioClip;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Full-screen tournament view: live boards during play, rich summary after.
 * Board rendering → {@link BoardRenderer}
 * Live board card  → {@link MiniBoard}
 * Post-tournament  → {@link TournamentSummary}
 */
public final class TournamentView {

    private static final String ICON_COLOR_BTN    = "#8890A8";
    private static final String ICON_COLOR_STOP   = "#C8706A";
    private static final String TITLE_TOURNAMENT  = "TOURNAMENT";
    private static final String LBL_PAUSE         = "Pause";
    private static final String STYLE_SECTION     = "tournament-section-title";
    private static final long   FINISH_OVERLAY_NS = 1_500_000_000L;
    private static final int    MAX_RESULTS       = 20;
    private static final int    ETA_WINDOW        = 20;
    private static final double ETA_ALPHA         = 0.25;

    private final AudioClip selectSound = new AudioClip(
        getClass().getResource("/audio/sfx/Select.wav").toExternalForm());
    private final AudioClip pinSound = new AudioClip(
        getClass().getResource("/audio/sfx/Pin.wav").toExternalForm());

    // ── State ────────────────────────────────────────────────────────────────
    private final StackPane root;
    private final TournamentRunner runner    = new TournamentRunner();
    private List<Difficulty> strategies = new ArrayList<>(Arrays.asList(Difficulty.values()));
    private final ObservableList<Difficulty> tableItems;
    private final ObservableList<String> recentResults = FXCollections.observableArrayList();

    private final Label       titleLabel    = new Label(TITLE_TOURNAMENT);
    private final ProgressBar progressBar   = new ProgressBar(0);
    private final Label       progressLabel = new Label();
    private final Label       etaLabel      = new Label();
    private final FontIcon    pauseIcon     = new FontIcon(FontAwesomeSolid.PAUSE);
    private final FontIcon    stopIcon      = new FontIcon(FontAwesomeSolid.ARROW_LEFT);
    private final FontIcon    restartIcon   = new FontIcon(FontAwesomeSolid.REDO);
    private final Button      pauseBtn      = new Button(LBL_PAUSE);
    private final Button      restartBtn    = new Button("Restart");
    private final Button      actionBtn     = new Button("Back");

    private final TilePane boardGrid  = new TilePane(10, 10);
    private final VBox     summaryBox = new VBox(14);
    private       Label    boardsTitle;

    private final Map<Integer, MiniBoard>     activeBoards   = new LinkedHashMap<>();
    private record FinishingGame(MiniBoard board, long startNs) {}
    private final Map<Integer, FinishingGame> finishingGames = new LinkedHashMap<>();
    private record PinnedOverlay(MiniBoard board, long startNs,
                                 GameState finalState,
                                 java.util.concurrent.ConcurrentHashMap<Wall, Player> finalWO) {}
    private final Map<Integer, PinnedOverlay> pinnedOverlays = new LinkedHashMap<>();
    private final Map<Integer, MiniBoard>     frozenBoards   = new LinkedHashMap<>();

    private final Set<Difficulty> selectedStrategies = new HashSet<>();
    private final Set<Integer>    pinnedGameIds      = new HashSet<>();

    private TableView<Difficulty> standingsTable;
    private ScrollPane boardsScroll;
    private ScrollPane summaryScroll;
    private AnimationTimer animTimer;
    private Timeline       etaTimeline;
    private boolean running      = false;
    private boolean paused       = false;
    private long    etaDisplayMs = 0;
    private final ArrayDeque<Long> recentGameTimes = new ArrayDeque<>();
    private final GameController ctrl;
    private final Runnable onClose;
    private int gamesPerMatchup = 1;
    private int concurrentGames = computeRecommended(Difficulty.values().length);
    private final List<ToggleButton> strategyToggles = new ArrayList<>();
    private Label recommendedLabel;
    private Label setupStratCount;
    private Label setupTotalGames;
    private Label setupDuration;
    private Label gpmValueLabel;
    private Label ccValueLabel;
    private StackPane setupPane;

    private static final int  MOVES_PER_PLAYER = 30; // avg moves each player makes per game
    private static final int  MAX_CONCURRENT   = Runtime.getRuntime().availableProcessors();

    public TournamentView(Runnable onClose, GameController ctrl) {
        this.ctrl    = ctrl;
        this.onClose = onClose;
        root = new StackPane();
        root.getStyleClass().add("tournament-view");
        root.setVisible(false);

        tableItems     = FXCollections.observableArrayList(strategies);
        standingsTable = buildTable();

        // ── Top bar ──────────────────────────────────────────────────────────
        titleLabel.getStyleClass().add("tournament-title");
        progressLabel.getStyleClass().add("tournament-progress-label");
        etaLabel.getStyleClass().add("tournament-progress-label");
        progressBar.getStyleClass().add("tournament-progress-bar");

        pauseIcon.setIconSize(12);
        pauseIcon.setIconColor(Color.web(ICON_COLOR_BTN));
        pauseBtn.setGraphic(pauseIcon);
        pauseBtn.getStyleClass().add("new-game-button");
        pauseBtn.setOnAction(e -> togglePause());

        FontIcon copyIcon = new FontIcon(FontAwesomeSolid.CLIPBOARD);
        copyIcon.setIconSize(12);
        copyIcon.setIconColor(Color.web("#5A8ACA"));
        Button copyBtn = new Button("Copy");
        copyBtn.setGraphic(copyIcon);
        copyBtn.getStyleClass().add("tournament-copy-btn");
        copyBtn.setOnAction(e -> { copyToClipboard(); if (!ctrl.isMuted()) selectSound.play(); });

        restartIcon.setIconSize(12);
        restartIcon.setIconColor(Color.web(ICON_COLOR_BTN));
        restartBtn.setGraphic(restartIcon);
        restartBtn.getStyleClass().add("new-game-button");
        restartBtn.setVisible(false);
        restartBtn.setManaged(false);
        restartBtn.setOnAction(e -> showSetup());

        stopIcon.setIconSize(12);
        stopIcon.setIconColor(Color.web(ICON_COLOR_STOP));

        FontIcon muteIcon = new FontIcon(FontAwesomeSolid.VOLUME_UP);
        muteIcon.getStyleClass().add("bar-icon");
        Button muteBtn = new Button();
        muteBtn.setGraphic(muteIcon);
        muteBtn.setAlignment(Pos.CENTER_RIGHT);
        muteBtn.getStyleClass().add("mute-button");
        muteBtn.setOnAction(e -> {
            ctrl.toggleMute();
            muteIcon.setIconCode(ctrl.isMuted() ? FontAwesomeSolid.VOLUME_MUTE : FontAwesomeSolid.VOLUME_UP);
        });

        actionBtn.setAlignment(Pos.CENTER_RIGHT);
        actionBtn.setGraphic(stopIcon);
        actionBtn.getStyleClass().add("tournament-stop-btn");
        actionBtn.setOnAction(e -> {
            if (!ctrl.isMuted()) selectSound.play();
            if (running) { runner.cancel(); running = false; }
            stopAnimTimer();
            root.setVisible(false);
            onClose.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, titleLabel, progressBar, progressLabel, etaLabel,
                               pauseBtn, copyBtn, restartBtn, spacer, muteBtn, actionBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("tournament-top-bar");
        topBar.setPadding(new Insets(14, 20, 14, 20));
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // ── Live boards ───────────────────────────────────────────────────────
        boardGrid.getStyleClass().add("tournament-board-grid");
        boardGrid.setPrefTileWidth(MiniBoard.BOARD_PX + 24);
        boardGrid.setPrefTileHeight(MiniBoard.BOARD_PX + 52);
        boardGrid.setPadding(new Insets(10));

        Label noGamesLabel = new Label("Waiting for games to start…");
        noGamesLabel.getStyleClass().add("tournament-empty-label");
        StackPane boardsContent = new StackPane(boardGrid, noGamesLabel);
        boardGrid.getChildren().addListener(
            (javafx.collections.ListChangeListener<Node>) c ->
                noGamesLabel.setVisible(boardGrid.getChildren().isEmpty()));

        boardsScroll = new ScrollPane(boardsContent);
        boardsScroll.setFitToWidth(true);
        boardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        boardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        boardsScroll.getStyleClass().add("tournament-board-scroll");

        summaryBox.setPadding(new Insets(4));
        summaryScroll = new ScrollPane(summaryBox);
        summaryScroll.setFitToWidth(true);
        summaryScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        summaryScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        summaryScroll.getStyleClass().add("tournament-board-scroll");
        summaryScroll.setVisible(false);

        StackPane mainContent = new StackPane(boardsScroll, summaryScroll);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        boardsTitle = new Label("LIVE GAMES");
        boardsTitle.getStyleClass().add(STYLE_SECTION);

        VBox boardsSection = new VBox(8, boardsTitle, mainContent);
        boardsSection.getStyleClass().add("tournament-panel");
        boardsSection.setPadding(new Insets(14));
        HBox.setHgrow(boardsSection, Priority.ALWAYS);

        // ── Right panel ───────────────────────────────────────────────────────
        Label standingsTitle = new Label("STANDINGS");
        standingsTitle.getStyleClass().add(STYLE_SECTION);
        Label resultsTitle = new Label("RECENT RESULTS");
        resultsTitle.getStyleClass().add(STYLE_SECTION);

        ListView<String> resultsList = new ListView<>(recentResults);
        resultsList.getStyleClass().add("tournament-results-list");
        resultsList.setMaxHeight(Double.MAX_VALUE);
        resultsList.setPrefHeight(0);
        VBox.setVgrow(resultsList, Priority.ALWAYS);
        VBox.setVgrow(standingsTable, Priority.ALWAYS);

        VBox rightPanel = new VBox(10, standingsTitle, standingsTable, resultsTitle, resultsList);
        rightPanel.getStyleClass().add("tournament-panel");
        rightPanel.setPadding(new Insets(14));
        rightPanel.setPrefWidth(360);
        rightPanel.setMaxWidth(360);

        HBox center = new HBox(10, boardsSection, rightPanel);
        center.setPadding(new Insets(0, 10, 10, 10));
        VBox.setVgrow(center, Priority.ALWAYS);

        VBox layout = new VBox(topBar, center);
        VBox.setVgrow(center, Priority.ALWAYS);
        layout.getStyleClass().add("tournament-layout");
        root.getChildren().add(layout);
        setupPane = buildSetupPanel();
        root.getChildren().add(setupPane);
    }

    public StackPane getRoot() { return root; }

    // ── Start ─────────────────────────────────────────────────────────────────

    public void showSetup() {
        root.setVisible(true);
        setupPane.setVisible(true);
        if (gpmValueLabel != null) gpmValueLabel.setText(String.valueOf(gamesPerMatchup));
        if (ccValueLabel  != null) ccValueLabel.setText(String.valueOf(concurrentGames));
        updatePreview();
    }

    private void start() {
        running      = true;
        paused       = false;
        etaDisplayMs = 0;
        recentGameTimes.clear();
        if (etaTimeline != null) etaTimeline.stop();
        etaTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickEta()));
        etaTimeline.setCycleCount(Animation.INDEFINITE);
        etaTimeline.play();
        int total = runner.totalGames(strategies, gamesPerMatchup);
        final long startMs = System.currentTimeMillis();
        resetUiForStart(total);
        runner.start(strategies, gamesPerMatchup, concurrentGames, buildProgressCallback(), buildResultCallback(), buildCompleteCallback(startMs));
    }

    private void confirmStart() {
        List<Difficulty> selected = new ArrayList<>();
        Difficulty[] all = Difficulty.values();
        for (int i = 0; i < strategyToggles.size(); i++) {
            if (strategyToggles.get(i).isSelected()) selected.add(all[i]);
        }
        if (selected.size() < 2) return;
        strategies = selected;
        setupPane.setVisible(false);
        start();
    }

    private StackPane buildSetupPanel() {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.80);");
        overlay.setVisible(false);
        overlay.setPadding(new Insets(48, 0, 48, 0));

        VBox card = new VBox(0);
        card.getStyleClass().add("tournament-setup-card");
        card.setMaxWidth(1160);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.CENTER);

        Label title = new Label("TOURNAMENT SETUP");
        title.getStyleClass().add("tournament-title");
        VBox header = new VBox(title);
        header.setPadding(new Insets(28, 36, 22, 36));

        card.getChildren().addAll(
            header,              buildDivider(),
            buildStrategiesSection(), buildDivider(),
            buildConfigSection(), buildDivider(),
            buildSummarySection(overlay)
        );
        overlay.getChildren().add(card);
        updatePreview();
        return overlay;
    }

    private VBox buildStrategiesSection() {
        Label sectionLabel = new Label("STRATEGIES");
        sectionLabel.getStyleClass().add(STYLE_SECTION);
        Button allBtn  = new Button("All");  allBtn.getStyleClass().add("setup-neutral-btn");
        Button noneBtn = new Button("None"); noneBtn.getStyleClass().add("setup-neutral-btn");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox stratHeader = new HBox(8, sectionLabel, sp, allBtn, noneBtn);
        stratHeader.setAlignment(Pos.CENTER_LEFT);

        GridPane stratGrid = new GridPane();
        stratGrid.setHgap(8);
        stratGrid.setVgap(8);
        for (int col = 0; col < 4; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setHgrow(Priority.ALWAYS);
            cc.setFillWidth(true);
            stratGrid.getColumnConstraints().add(cc);
        }
        int rows = (int) Math.ceil(Difficulty.values().length / 4.0);
        for (int row = 0; row < rows; row++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(90);
            rc.setPrefHeight(90);
            rc.setMaxHeight(90);
            rc.setVgrow(Priority.NEVER);
            stratGrid.getRowConstraints().add(rc);
        }
        strategyToggles.clear();
        Difficulty[] diffs = Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            ToggleButton tb = buildStrategyCard(diffs[i]);
            stratGrid.add(tb, i % 4, i / 4);
            GridPane.setHgrow(tb, Priority.ALWAYS);
            GridPane.setFillWidth(tb, true);
            strategyToggles.add(tb);
        }
        allBtn.setOnAction(e  -> { if (!ctrl.isMuted()) selectSound.play(); strategyToggles.forEach(tb -> tb.setSelected(true)); });
        noneBtn.setOnAction(e -> { if (!ctrl.isMuted()) selectSound.play(); strategyToggles.forEach(tb -> tb.setSelected(false)); });

        ScrollPane stratScroll = new ScrollPane(stratGrid);
        stratScroll.setFitToWidth(true);
        stratScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stratScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        stratScroll.getStyleClass().add("tournament-board-scroll");

        VBox section = new VBox(10, stratHeader, stratScroll);
        section.setPadding(new Insets(20, 36, 24, 36));
        return section;
    }

    private VBox buildConfigSection() {
        Label gpmLabel = new Label("GAMES PER MATCHUP");
        gpmLabel.getStyleClass().add(STYLE_SECTION);
        gpmValueLabel = new Label(String.valueOf(gamesPerMatchup));
        gpmValueLabel.getStyleClass().add("tournament-spinner-value");
        Button gpmMinus = new Button("−"); gpmMinus.getStyleClass().add("tournament-spinner-btn");
        Button gpmPlus  = new Button("+"); gpmPlus.getStyleClass().add("tournament-spinner-btn");
        gpmMinus.setOnAction(e -> {
            if (gamesPerMatchup > 1) { gamesPerMatchup--; gpmValueLabel.setText(String.valueOf(gamesPerMatchup)); updatePreview(); }
        });
        gpmPlus.setOnAction(e -> {
            if (gamesPerMatchup < 20) { gamesPerMatchup++; gpmValueLabel.setText(String.valueOf(gamesPerMatchup)); updatePreview(); }
        });
        HBox gpmSpinner = new HBox(6, gpmMinus, gpmValueLabel, gpmPlus);
        gpmSpinner.setAlignment(Pos.CENTER_LEFT);
        VBox gpmBox = new VBox(10, gpmLabel, gpmSpinner);

        Label ccLabel = new Label("CONCURRENT GAMES");
        ccLabel.getStyleClass().add(STYLE_SECTION);
        ccValueLabel = new Label(String.valueOf(concurrentGames));
        ccValueLabel.getStyleClass().add("tournament-spinner-value");
        Button ccMinus = new Button("−"); ccMinus.getStyleClass().add("tournament-spinner-btn");
        Button ccPlus  = new Button("+"); ccPlus.getStyleClass().add("tournament-spinner-btn");
        ccMinus.setOnAction(e -> {
            if (concurrentGames > 1) { concurrentGames--; ccValueLabel.setText(String.valueOf(concurrentGames)); updatePreview(); }
        });
        ccPlus.setOnAction(e -> {
            if (concurrentGames < MAX_CONCURRENT) { concurrentGames++; ccValueLabel.setText(String.valueOf(concurrentGames)); updatePreview(); }
        });
        HBox ccSpinner = new HBox(6, ccMinus, ccValueLabel, ccPlus);
        ccSpinner.setAlignment(Pos.CENTER_LEFT);
        recommendedLabel = new Label();
        recommendedLabel.getStyleClass().add("tournament-progress-label");
        VBox ccBox = new VBox(10, ccLabel, ccSpinner, recommendedLabel);

        HBox row = new HBox(52, gpmBox, ccBox);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(row);
        section.setPadding(new Insets(20, 36, 24, 36));
        return section;
    }

    private VBox buildSummarySection(StackPane overlay) {
        setupStratCount = new Label("—");
        setupTotalGames = new Label("—");
        setupDuration   = new Label("—");

        VBox statStrategies = buildStatBox(setupStratCount, "Strategies");
        VBox statGames      = buildStatBox(setupTotalGames, "Total Games");
        VBox statDuration   = buildStatBox(setupDuration,   "Estimated Duration");
        HBox.setHgrow(statStrategies, Priority.ALWAYS);
        HBox.setHgrow(statGames,      Priority.ALWAYS);
        HBox.setHgrow(statDuration,   Priority.ALWAYS);
        HBox statsRow = new HBox(12, statStrategies, statGames, statDuration);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("tournament-stop-btn");
        Button startBtn = new Button("Start Tournament");
        startBtn.getStyleClass().add("tournament-setup-start-btn");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionsRow = new HBox(10, spacer, cancelBtn, startBtn);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        cancelBtn.setOnAction(e -> {
            if (!ctrl.isMuted()) selectSound.play();
            overlay.setVisible(false);
            root.setVisible(false);
            onClose.run();
        });
        startBtn.setOnAction(e -> {
            if (!ctrl.isMuted()) selectSound.play();
            confirmStart();
        });

        VBox section = new VBox(16, statsRow, actionsRow);
        section.setPadding(new Insets(20, 36, 28, 36));
        return section;
    }

    private static VBox buildStatBox(Label numLabel, String labelText) {
        numLabel.getStyleClass().add("setup-stat-number");
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("setup-stat-label");
        VBox box = new VBox(5, numLabel, lbl);
        box.getStyleClass().add("setup-stat-box");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region buildDivider() {
        Region div = new Region();
        div.setPrefHeight(1);
        div.setMaxHeight(1);
        div.setStyle("-fx-background-color: #181B27;");
        return div;
    }

    private ToggleButton buildStrategyCard(Difficulty d) {
        Label name = new Label(d.sample().displayName());
        name.getStyleClass().add("setup-card-name");
        Label desc = new Label(d.sample().description());
        desc.getStyleClass().add("setup-card-desc");
        desc.setWrapText(true);
        desc.setMaxHeight(50);
        VBox content = new VBox(5, name, desc);
        content.setMouseTransparent(true);
        ToggleButton tb = new ToggleButton();
        tb.setGraphic(content);
        tb.setAlignment(Pos.TOP_LEFT);
        tb.setSelected(true);
        tb.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        tb.getStyleClass().add("tournament-strategy-card");
        tb.selectedProperty().addListener((obs, old, nv) -> updatePreview());
        return tb;
    }

    private void updatePreview() {
        Difficulty[] all = Difficulty.values();
        List<Difficulty> selected = new ArrayList<>();
        for (int i = 0; i < strategyToggles.size(); i++)
            if (strategyToggles.get(i).isSelected()) selected.add(all[i]);

        int selCount = selected.size();
        int total = selCount * (selCount - 1) * gamesPerMatchup;
        int recommended = computeRecommended(selCount);
        if (recommendedLabel  != null) recommendedLabel.setText("Recommended: " + recommended);

        long estMs = 0;
        if (total > 0 && concurrentGames > 0) {
            long totalWork = 0;
            for (Difficulty d1 : selected)
                for (Difficulty d2 : selected)
                    if (d1 != d2)
                        totalWork += (long) MOVES_PER_PLAYER
                                   * (d1.msPerDecision() + d2.msPerDecision())
                                   * gamesPerMatchup;
            estMs = totalWork / concurrentGames;
        }

        if (setupStratCount != null) setupStratCount.setText(String.valueOf(selCount));
        if (setupTotalGames != null) setupTotalGames.setText(String.valueOf(total));
        if (setupDuration   != null) setupDuration.setText(estMs <= 0 ? "—" : formatDuration(estMs));
    }

    private static int computeRecommended(int stratCount) {
        return Math.max(1, Math.min(stratCount / 2, Runtime.getRuntime().availableProcessors() - 1));
    }

    private void resetUiForStart(int total) {
        selectedStrategies.clear();
        pinnedGameIds.clear();
        finishingGames.clear();
        pinnedOverlays.clear();
        frozenBoards.clear();
        summaryBox.getChildren().clear();
        boardsScroll.setVisible(true);
        summaryScroll.setVisible(false);
        boardsTitle.setText("LIVE GAMES");
        titleLabel.setText(TITLE_TOURNAMENT);
        stopIcon.setIconCode(FontAwesomeSolid.ARROW_LEFT);
        actionBtn.setText("Back");
        pauseIcon.setIconCode(FontAwesomeSolid.PAUSE);
        pauseBtn.setText(LBL_PAUSE);
        pauseBtn.setDisable(false);
        restartBtn.setVisible(false);
        restartBtn.setManaged(false);
        progressBar.setProgress(0);
        etaLabel.setText("");
        progressLabel.setText("0 / " + total);
        tableItems.setAll(strategies);
        recentResults.clear();
        activeBoards.clear();
        boardGrid.getChildren().clear();
        standingsTable.refresh();
        root.setVisible(true);
        startAnimTimer();
    }

    private BiConsumer<Integer, Integer> buildProgressCallback() {
        return (done, t) -> {
            progressBar.setProgress((double) done / t);
            progressLabel.setText(done + " / " + t);
            resort();
            long now = System.currentTimeMillis();
            recentGameTimes.addLast(now);
            if (recentGameTimes.size() > ETA_WINDOW) recentGameTimes.pollFirst();
            if (recentGameTimes.size() >= 2) {
                long windowMs = recentGameTimes.peekLast() - recentGameTimes.peekFirst();
                if (windowMs > 0) {
                    double rate   = (recentGameTimes.size() - 1.0) / windowMs;
                    long   newEta = (long) ((t - done) / rate);
                    etaDisplayMs  = etaDisplayMs == 0 ? newEta
                                  : (long) (ETA_ALPHA * newEta + (1.0 - ETA_ALPHA) * etaDisplayMs);
                }
            }
        };
    }

    private BiConsumer<Difficulty, Difficulty> buildResultCallback() {
        return (winner, loser) -> {
            int[] wr = runner.getResults().get(winner);
            String pct = (wr == null || wr[0] + wr[1] == 0) ? ""
                : String.format(" (%d%%)", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1])));
            recentResults.add(0, winner.sample().displayName()
                + " beat " + loser.sample().displayName() + pct);
            if (recentResults.size() > MAX_RESULTS)
                recentResults.remove(recentResults.size() - 1);
        };
    }

    private Runnable buildCompleteCallback(long startMs) {
        return () -> {
            running = false;
            if (etaTimeline != null) { etaTimeline.stop(); etaTimeline = null; }
            long durationMs = System.currentTimeMillis() - startMs;
            titleLabel.setText("TOURNAMENT RESULTS");
            boardsTitle.setText("RESULTS SUMMARY");
            stopIcon.setIconCode(FontAwesomeSolid.TIMES);
            actionBtn.setText("Close");
            pauseBtn.setDisable(true);
            progressBar.setProgress(1.0);
            progressLabel.setText(runner.totalGames(strategies, gamesPerMatchup) + " games");
            etaLabel.setText("  " + formatDuration(durationMs));
            restartBtn.setVisible(true);
            restartBtn.setManaged(true);
            resort();
            boardsScroll.setVisible(false);
            summaryScroll.setVisible(true);
            TournamentSummary.populate(summaryBox, runner, tableItems, strategies, durationMs);
        };
    }

    // ── ETA ──────────────────────────────────────────────────────────────────

    private void tickEta() {
        if (etaDisplayMs <= 0) return;
        etaDisplayMs = Math.max(0, etaDisplayMs - 1000);
        etaLabel.setText("   ~" + formatDuration(etaDisplayMs));
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    // ── Pause / resume ────────────────────────────────────────────────────────

    private void togglePause() {
        paused = !paused;
        if (!ctrl.isMuted()) selectSound.play();
        if (paused) {
            runner.pause();
            if (etaTimeline != null) etaTimeline.pause();
            pauseIcon.setIconCode(FontAwesomeSolid.PLAY);
            pauseBtn.setText("Resume");
            titleLabel.setText(TITLE_TOURNAMENT + " — PAUSED");
        } else {
            runner.resume();
            if (etaTimeline != null) etaTimeline.play();
            pauseIcon.setIconCode(FontAwesomeSolid.PAUSE);
            pauseBtn.setText(LBL_PAUSE);
            titleLabel.setText(TITLE_TOURNAMENT);
        }
    }

    // ── Animation timer ───────────────────────────────────────────────────────

    private void startAnimTimer() {
        animTimer = new AnimationTimer() {
            @Override public void handle(long now) { refreshLiveBoards(now); }
        };
        animTimer.start();
    }

    private void stopAnimTimer() {
        if (animTimer != null) { animTimer.stop(); animTimer = null; }
    }

    private void refreshLiveBoards(long now) {
        Set<Integer> current = new HashSet<>(runner.getLiveStates().keySet());
        processActiveGames(current, now);
        transitionFinishedGames(now);
        transitionPinnedOverlays(now);
        for (MiniBoard mb : frozenBoards.values())
            mb.updateSelection(selectedStrategies.contains(mb.d1), selectedStrategies.contains(mb.d2));
    }

    private void processActiveGames(Set<Integer> current, long now) {
        var liveStates   = runner.getLiveStates();
        var liveMatchups = runner.getLiveMatchups();
        var liveWO       = runner.getLiveWallOwners();

        for (int id : current) {
            GameState state    = liveStates.get(id);
            Difficulty[] match = liveMatchups.get(id);
            if (state == null || match == null) continue;
            var wallOwners = liveWO.getOrDefault(id, new java.util.concurrent.ConcurrentHashMap<>());
            MiniBoard mb = activeBoards.computeIfAbsent(id, k -> createMiniBoard(k, match));
            mb.draw(state, wallOwners);
            mb.updateSelection(selectedStrategies.contains(mb.d1), selectedStrategies.contains(mb.d2));
        }

        activeBoards.entrySet().removeIf(e -> {
            if (current.contains(e.getKey())) return false;
            onGameFinished(e.getKey(), e.getValue(), now);
            return true;
        });
    }

    private MiniBoard createMiniBoard(int id, Difficulty[] match) {
        MiniBoard b = new MiniBoard(match[0], match[1]);
        b.card.setCursor(Cursor.HAND);
        b.card.setOnMouseClicked(e -> { togglePin(id, b); if (!ctrl.isMuted()) pinSound.play(); });
        boardGrid.getChildren().add(b.card);
        return b;
    }

    private void onGameFinished(int id, MiniBoard mb, long now) {
        GameState finalState = runner.getFinalGameStates().get(id);
        var finalWO = runner.getFinalWallOwners().getOrDefault(id,
                          new java.util.concurrent.ConcurrentHashMap<>());
        if (finalState != null) mb.draw(finalState, finalWO);

        Difficulty winner = runner.getLiveWinners().get(id);
        Color wColor = (winner == mb.d1) ? BoardRenderer.P1_COLOR : BoardRenderer.P2_COLOR;
        mb.drawWinnerOverlay(winner != null ? winner.sample().displayName() : null, wColor);

        if (pinnedGameIds.remove(id)) {
            mb.setPinned(true);
            mb.card.setOnMouseClicked(ev -> { dismissFrozen(id, mb); if (!ctrl.isMuted()) pinSound.play(); });
            var fo = runner.getFinalWallOwners().getOrDefault(id, new java.util.concurrent.ConcurrentHashMap<>());
            pinnedOverlays.put(id, new PinnedOverlay(mb, now, finalState != null ? finalState : new GameState(), fo));
        } else {
            finishingGames.put(id, new FinishingGame(mb, now));
        }
    }

    private void transitionFinishedGames(long now) {
        finishingGames.entrySet().removeIf(e -> {
            if (now - e.getValue().startNs() < FINISH_OVERLAY_NS) return false;
            MiniBoard mb = e.getValue().board();
            FadeTransition ft = new FadeTransition(Duration.millis(300), mb.card);
            ft.setToValue(0);
            ft.setOnFinished(ev -> { mb.card.setOpacity(1); removeWithShift(mb.card); });
            ft.play();
            return true;
        });
    }

    private void transitionPinnedOverlays(long now) {
        pinnedOverlays.entrySet().removeIf(e -> {
            PinnedOverlay po = e.getValue();
            if (now - po.startNs() < FINISH_OVERLAY_NS) return false;
            po.board().draw(po.finalState(), po.finalWO());
            frozenBoards.put(e.getKey(), po.board());
            return true;
        });
    }

    private void togglePin(int gameId, MiniBoard board) {
        if (!pinnedGameIds.remove(gameId)) { pinnedGameIds.add(gameId); board.setPinned(true); }
        else                               { board.setPinned(false); }
    }

    private void dismissFrozen(int gameId, MiniBoard board) {
        frozenBoards.remove(gameId);
        pinnedOverlays.remove(gameId);
        removeWithShift(board.card);
    }

    private void removeWithShift(Node card) {
        List<Node> siblings = new ArrayList<>(boardGrid.getChildren());
        siblings.remove(card);
        Map<Node, Point2D> before = new LinkedHashMap<>();
        for (Node n : siblings) before.put(n, new Point2D(n.getLayoutX(), n.getLayoutY()));
        boardGrid.getChildren().remove(card);
        boardGrid.requestLayout();
        boardGrid.layout();
        for (Map.Entry<Node, Point2D> entry : before.entrySet()) {
            Node n = entry.getKey();
            double dx = entry.getValue().getX() - n.getLayoutX();
            double dy = entry.getValue().getY() - n.getLayoutY();
            if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) continue;
            n.setTranslateX(dx); n.setTranslateY(dy);
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), n);
            tt.setToX(0); tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
        }
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    private void resort() {
        Map<Difficulty, int[]> res = runner.getResults();
        tableItems.sort(Comparator
            .comparingDouble((Difficulty d) -> {
                int[] wr = res.get(d);
                return (wr == null || wr[0] + wr[1] == 0) ? 0.0 : -(double) wr[0] / (wr[0] + wr[1]);
            })
            .thenComparingInt(d -> { int[] wr = res.get(d); return wr == null ? 0 : -wr[0]; }));
        standingsTable.refresh();
    }

    @SuppressWarnings("unchecked")
    private TableView<Difficulty> buildTable() {
        TableView<Difficulty> tv = new TableView<>(tableItems);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getStyleClass().add("tournament-table");
        tv.setFocusTraversable(false);
        tv.setRowFactory(t -> buildRow());
        tv.getColumns().addAll(
            buildRankColumn(), buildNameColumn(),
            buildWinColumn(), buildLossColumn(), buildWinPctColumn());
        return tv;
    }

    private TableRow<Difficulty> buildRow() {
        return new TableRow<>() {
            {
                setCursor(Cursor.HAND);
                setOnMouseClicked(e -> {
                    if (!ctrl.isMuted()) pinSound.play();
                    Difficulty item = getItem();
                    if (item == null || isEmpty()) return;
                    if (!selectedStrategies.remove(item)) selectedStrategies.add(item);
                    standingsTable.refresh();
                });
            }
            @Override protected void updateItem(Difficulty item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("rank-top", "rank-second", "rank-third");
                setStyle("");
                if (empty || item == null) return;
                applyRankStyle(this, tableItems.indexOf(item));
                if (selectedStrategies.contains(item)) applySelectedRowStyle(this);
            }
        };
    }

    private static void applyRankStyle(TableRow<?> row, int idx) {
        switch (idx) {
            case 0 -> row.getStyleClass().add("rank-top");
            case 1 -> row.getStyleClass().add("rank-second");
            case 2 -> row.getStyleClass().add("rank-third");
            default -> { /* no rank styling beyond top 3 */ }
        }
    }

    private static void applySelectedRowStyle(TableRow<?> row) {
        row.setStyle("-fx-border-color: transparent transparent #191C2A #D4AC0D;"
                   + "-fx-border-width: 0 0 1 3;");
    }

    private TableColumn<Difficulty, Number> buildRankColumn() {
        TableColumn<Difficulty, Number> col = new TableColumn<>("#");
        col.setCellValueFactory(cd -> new SimpleIntegerProperty(tableItems.indexOf(cd.getValue()) + 1));
        col.setPrefWidth(36); col.setMinWidth(32); col.setMaxWidth(44); col.setSortable(false);
        return col;
    }

    private TableColumn<Difficulty, String> buildNameColumn() {
        TableColumn<Difficulty, String> col = new TableColumn<>("Strategy");
        col.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().sample().displayName()));
        col.setMinWidth(100); col.setSortable(false);
        return col;
    }

    private TableColumn<Difficulty, Number> buildWinColumn() {
        TableColumn<Difficulty, Number> col = new TableColumn<>("W");
        col.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            return new SimpleIntegerProperty(wr == null ? 0 : wr[0]);
        });
        col.setPrefWidth(44); col.setMinWidth(38); col.setMaxWidth(56); col.setSortable(false);
        return col;
    }

    private TableColumn<Difficulty, Number> buildLossColumn() {
        TableColumn<Difficulty, Number> col = new TableColumn<>("L");
        col.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            return new SimpleIntegerProperty(wr == null ? 0 : wr[1]);
        });
        col.setPrefWidth(44); col.setMinWidth(38); col.setMaxWidth(56); col.setSortable(false);
        return col;
    }

    private TableColumn<Difficulty, String> buildWinPctColumn() {
        TableColumn<Difficulty, String> col = new TableColumn<>("Win%");
        col.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            if (wr == null || wr[0] + wr[1] == 0) return new SimpleStringProperty("—");
            return new SimpleStringProperty(String.format("%d%%", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1]))));
        });
        col.setPrefWidth(52); col.setMinWidth(46); col.setMaxWidth(66); col.setSortable(false);
        return col;
    }

    // ── Clipboard export ──────────────────────────────────────────────────────

    private void copyToClipboard() {
        ClipboardContent c = new ClipboardContent();
        c.putString(formatResults());
        Clipboard.getSystemClipboard().setContent(c);
    }

    private String formatResults() {
        int total = runner.totalGames(strategies, gamesPerMatchup);
        Map<Difficulty, int[]> res = runner.getResults();
        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        StringBuilder sb = new StringBuilder();
        sb.append("CHORIDOR TOURNAMENT RESULTS\n")
          .append(strategies.size()).append(" strategies · ").append(total).append(" games\n\n");
        appendStandingsTable(sb, res);
        appendH2hSection(sb, res, mw);
        return sb.toString();
    }

    private void appendStandingsTable(StringBuilder sb, Map<Difficulty, int[]> res) {
        sb.append(String.format("%-4s  %-18s  %-6s  %-6s  %-5s%n", "#", "Strategy", "+WINS", "-LOSS", "Win%"));
        sb.append("─".repeat(46)).append("\n");
        for (int i = 0; i < tableItems.size(); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0];
            int l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(String.format("%-4d  %-18s  %-6d  %-6d  %-5s%n",
                i + 1, d.sample().displayName(), w, l, pct));
        }
    }

    private void appendH2hSection(StringBuilder sb, Map<Difficulty, int[]> res,
                                   Map<Difficulty, Map<Difficulty, Integer>> mw) {
        sb.append("\n\nHEAD-TO-HEAD BREAKDOWN\n").append("─".repeat(40)).append("\n");
        for (Difficulty d : tableItems) {
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0];
            int l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(d.sample().displayName()).append("  (")
              .append(w).append("W–").append(l).append("L  ").append(pct).append(")\n");
            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                wins.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<Difficulty, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().sample().displayName()))
                    .forEach(e -> {
                        int ww = e.getValue();
                        int ll = 2 - ww;
                        String m = switch (ww) {
                            case 2  -> "✓✓";
                            case 1  -> "✓✗";
                            default -> "✗✗";
                        };
                        sb.append(String.format("  %s  %-16s  %d–%d%n",
                            m, e.getKey().sample().displayName(), ww, ll));
                    });
            }
            sb.append("\n");
        }
    }
}

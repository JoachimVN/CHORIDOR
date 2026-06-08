package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.tournament.GameRecord;
import io.github.joachimvn.tournament.TournamentRunner;

import javafx.animation.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Full-screen tournament view with live boards, standings, and a rich post-tournament summary.
 */
public final class TournamentView {

    // ── Board rendering ──────────────────────────────────────────────────────
    private static final double DESIGN_CELL = 54;
    private static final double DESIGN_GAP  = 10;
    private static final double DESIGN_STEP = DESIGN_CELL + DESIGN_GAP;
    private static final double DESIGN_SIZE = GameState.BOARD_SIZE * DESIGN_CELL
                                            + (GameState.BOARD_SIZE - 1) * DESIGN_GAP;
    private static final double BOARD_PX         = 370;
    private static final double SUMMARY_BOARD_PX = 260;
    private static final double GOAL_STRIP_RATIO = 3.0 / 54;
    private static final double PAWN_PAD_RATIO   = 0.16;
    private static final double STRIP_OPACITY    = 0.70;

    private static final Color P1_COLOR = Color.web("#9E4A40");
    private static final Color P2_COLOR = Color.web("#3E68A8");
    private static final Color BG_COLOR = Color.web("#0F1117");
    private static final Color CELL_CLR = Color.web("#191C2A");
    private static final Color P1_STRIP = P1_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    private static final Color P2_STRIP = P2_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);

    private static final String BORDER_P1   = "-fx-border-color: #9E4A40; -fx-border-width: 2;";
    private static final String BORDER_P2   = "-fx-border-color: #3E68A8; -fx-border-width: 2;";
    private static final String BORDER_BOTH = "-fx-border-color: #D4AC0D; -fx-border-width: 2;";

    private static final String ICON_COLOR_BTN  = "#8890A8";
    private static final String ICON_COLOR_STOP = "#C8706A";

    private static final long FINISH_OVERLAY_NS = 1_500_000_000L;
    private static final int  MAX_RESULTS        = 20;

    // ── State ────────────────────────────────────────────────────────────────
    private final StackPane root;
    private final TournamentRunner runner    = new TournamentRunner();
    private final List<Difficulty> strategies = Arrays.asList(Difficulty.values());
    private final ObservableList<Difficulty> tableItems;
    private final ObservableList<String> recentResults = FXCollections.observableArrayList();

    private final Label       titleLabel    = new Label("TOURNAMENT");
    private final ProgressBar progressBar   = new ProgressBar(0);
    private final Label       progressLabel = new Label();
    private final Label       etaLabel      = new Label();
    private final FontIcon    pauseIcon     = new FontIcon(FontAwesomeSolid.PAUSE);
    private final FontIcon    stopIcon      = new FontIcon(FontAwesomeSolid.ARROW_LEFT);
    private final FontIcon    restartIcon   = new FontIcon(FontAwesomeSolid.REDO);
    private final Button      pauseBtn      = new Button("Pause");
    private final Button      restartBtn    = new Button("Restart");
    private final Button      actionBtn     = new Button("Back");

    private final TilePane boardGrid   = new TilePane(10, 10);
    private final VBox     summaryBox  = new VBox(14);
    private       Label    boardsTitle; // updated "LIVE GAMES" → "RESULTS SUMMARY"

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
    private boolean running  = false;
    private boolean paused   = false;
    private long    tournamentStartMs;
    private int     doneCount, totalCount;
    private long    etaDisplayMs = 0;
    private final ArrayDeque<Long> recentGameTimes = new ArrayDeque<>();
    private static final int    ETA_WINDOW = 20;
    private static final double ETA_ALPHA  = 0.25;

    public TournamentView(Runnable onClose) {
        root = new StackPane();
        root.getStyleClass().add("tournament-view");
        root.setVisible(false);

        tableItems    = FXCollections.observableArrayList(strategies);
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
        copyBtn.setOnAction(e -> copyToClipboard());

        restartIcon.setIconSize(12);
        restartIcon.setIconColor(Color.web(ICON_COLOR_BTN));
        restartBtn.setGraphic(restartIcon);
        restartBtn.getStyleClass().add("new-game-button");
        restartBtn.setVisible(false);
        restartBtn.setManaged(false);
        restartBtn.setOnAction(e -> start());

        stopIcon.setIconSize(12);
        stopIcon.setIconColor(Color.web(ICON_COLOR_STOP));
        actionBtn.setGraphic(stopIcon);
        actionBtn.getStyleClass().add("tournament-stop-btn");
        actionBtn.setOnAction(e -> {
            if (running) { runner.cancel(); running = false; }
            stopAnimTimer();
            root.setVisible(false);
            onClose.run();
        });

        HBox topBar = new HBox(12, titleLabel, progressBar, progressLabel, etaLabel,
                               pauseBtn, copyBtn, restartBtn, actionBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("tournament-top-bar");
        topBar.setPadding(new Insets(14, 20, 14, 20));
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // ── Live boards ───────────────────────────────────────────────────────
        boardGrid.getStyleClass().add("tournament-board-grid");
        boardGrid.setPrefTileWidth((int) BOARD_PX + 24);
        boardGrid.setPrefTileHeight((int) BOARD_PX + 52);
        boardGrid.setPadding(new Insets(10));

        Label noGamesLabel = new Label("Waiting for games to start…");
        noGamesLabel.getStyleClass().add("tournament-empty-label");

        StackPane boardsContent = new StackPane(boardGrid, noGamesLabel);
        boardGrid.getChildren().addListener(
            (javafx.collections.ListChangeListener<javafx.scene.Node>) c ->
                noGamesLabel.setVisible(boardGrid.getChildren().isEmpty()));

        boardsScroll = new ScrollPane(boardsContent);
        boardsScroll.setFitToWidth(true);
        boardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        boardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        boardsScroll.getStyleClass().add("tournament-board-scroll");

        summaryBox.setPadding(new Insets(4));
        summaryScroll = new ScrollPane(summaryBox);
        summaryScroll.setFitToWidth(true);
        summaryScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // safety: scroll rather than push right panel
        summaryScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        summaryScroll.getStyleClass().add("tournament-board-scroll");
        summaryScroll.setVisible(false);

        StackPane mainContent = new StackPane(boardsScroll, summaryScroll);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        boardsTitle = new Label("LIVE GAMES");
        boardsTitle.getStyleClass().add("tournament-section-title");

        VBox boardsSection = new VBox(8, boardsTitle, mainContent);
        boardsSection.getStyleClass().add("tournament-panel");
        boardsSection.setPadding(new Insets(14));
        HBox.setHgrow(boardsSection, Priority.ALWAYS);

        // ── Right panel ───────────────────────────────────────────────────────
        Label standingsTitle = new Label("STANDINGS");
        standingsTitle.getStyleClass().add("tournament-section-title");

        Label resultsTitle = new Label("RECENT RESULTS");
        resultsTitle.getStyleClass().add("tournament-section-title");

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
    }

    public StackPane getRoot() { return root; }

    public void start() {
        running = true;
        paused  = false;
        tournamentStartMs = System.currentTimeMillis();
        doneCount = 0;
        totalCount = runner.totalGames(strategies);
        etaDisplayMs = 0;
        recentGameTimes.clear();
        if (etaTimeline != null) etaTimeline.stop();
        etaTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickEta()));
        etaTimeline.setCycleCount(Timeline.INDEFINITE);
        etaTimeline.play();
        selectedStrategies.clear();
        pinnedGameIds.clear();
        finishingGames.clear();
        pinnedOverlays.clear();
        frozenBoards.clear();
        summaryBox.getChildren().clear();
        boardsScroll.setVisible(true);
        summaryScroll.setVisible(false);
        boardsTitle.setText("LIVE GAMES");
        titleLabel.setText("TOURNAMENT");
        stopIcon.setIconCode(FontAwesomeSolid.ARROW_LEFT);
        actionBtn.setText("Back");
        pauseIcon.setIconCode(FontAwesomeSolid.PAUSE);
        pauseBtn.setText("Pause");
        pauseBtn.setDisable(false);
        restartBtn.setVisible(false);
        restartBtn.setManaged(false);
        progressBar.setProgress(0);
        etaLabel.setText("");
        progressLabel.setText("0 / " + totalCount);
        tableItems.setAll(strategies);
        recentResults.clear();
        activeBoards.clear();
        boardGrid.getChildren().clear();
        standingsTable.refresh();
        root.setVisible(true);
        startAnimTimer();

        runner.start(strategies,
            (done, t) -> {
                doneCount = done;
                progressBar.setProgress((double) done / t);
                progressLabel.setText(done + " / " + t);
                resort();
                // Record completion time; compute rate over recent window and EWMA-blend into display
                long now = System.currentTimeMillis();
                recentGameTimes.addLast(now);
                if (recentGameTimes.size() > ETA_WINDOW) recentGameTimes.pollFirst();
                if (recentGameTimes.size() >= 2) {
                    long windowMs = recentGameTimes.peekLast() - recentGameTimes.peekFirst();
                    if (windowMs > 0) {
                        double rate   = (recentGameTimes.size() - 1.0) / windowMs; // games/ms
                        long   newEta = (long)((t - done) / rate);
                        etaDisplayMs  = etaDisplayMs == 0 ? newEta
                                      : (long)(ETA_ALPHA * newEta + (1.0 - ETA_ALPHA) * etaDisplayMs);
                    }
                }
            },
            (winner, loser) -> {
                int[] wr = runner.getResults().get(winner);
                String pct = (wr == null || wr[0] + wr[1] == 0) ? ""
                    : String.format(" (%d%%)", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1])));
                recentResults.add(0, winner.sample().displayName()
                    + " beat " + loser.sample().displayName() + pct);
                if (recentResults.size() > MAX_RESULTS)
                    recentResults.remove(recentResults.size() - 1);
            },
            () -> {
                running = false;
                if (etaTimeline != null) { etaTimeline.stop(); etaTimeline = null; }
                long durationMs = System.currentTimeMillis() - tournamentStartMs;
                titleLabel.setText("TOURNAMENT RESULTS");
                boardsTitle.setText("RESULTS SUMMARY");
                stopIcon.setIconCode(FontAwesomeSolid.TIMES);
                actionBtn.setText("Close");
                pauseBtn.setDisable(true);
                progressBar.setProgress(1.0);
                progressLabel.setText(runner.totalGames(strategies) + " games");
                etaLabel.setText("  " + formatDuration(durationMs));
                restartBtn.setVisible(true);
                restartBtn.setManaged(true);
                resort();
                boardsScroll.setVisible(false);
                summaryScroll.setVisible(true);
                populateSummary(durationMs);
            });
    }

    // ── ETA ──────────────────────────────────────────────────────────────────

    private void tickEta() {
        if (etaDisplayMs <= 0) return;
        etaDisplayMs = Math.max(0, etaDisplayMs - 1000);
        etaLabel.setText("  ~" + formatDuration(etaDisplayMs));
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    private void populateSummary(long durationMs) {
        summaryBox.getChildren().clear();

        Map<Difficulty, int[]> res = runner.getResults();

        // ── Notable Games row ─────────────────────────────────────────────────
        GameRecord best     = runner.getBestGame();
        GameRecord shortest = runner.getShortestGame();
        GameRecord longest  = runner.getLongestGame();

        if (best != null || shortest != null || longest != null) {
            HBox notableRow = new HBox(12);
            notableRow.setAlignment(Pos.TOP_LEFT);
            if (best     != null) addNotableCard(notableRow, best,     "BEST GAME",     best.moveCount() + " moves · " + best.wallCount() + " walls · " + best.loserFinalDist() + "-step finish", "#B8960C");
            if (shortest != null) addNotableCard(notableRow, shortest, "SHORTEST GAME", shortest.moveCount() + " moves",  "#3E68A8");
            if (longest  != null) addNotableCard(notableRow, longest,  "LONGEST GAME",  longest.moveCount()  + " moves",  "#9E4A40");

            // HBox.prefHeight(-1) hits the SUMMARY_BOARD_PX fallback, so the row height is too small
            // for the actual board size. Correct it once the row width is known.
            notableRow.widthProperty().addListener((obs, oldW, newW) -> {
                int n = notableRow.getChildren().size();
                if (n == 0 || newW.doubleValue() < 1) return;
                double boardSize = (newW.doubleValue() - 12.0 * (n - 1)) / n - 24;
                if (boardSize < 1) return;
                // ~90px: title(15) + matchup(20) + winner(18) + 3×spacing(15) + padding(24)
                double totalH = boardSize + 92;
                notableRow.setMinHeight(totalH);
                notableRow.setPrefHeight(totalH);
            });

            summaryBox.getChildren().add(notableRow);
        }

        summaryBox.getChildren().add(separator());

        // ── Key stats ─────────────────────────────────────────────────────────
        VBox statsBox = new VBox(6);

        String[] rankColors = {"#B8960C", "#8896A0", "#8B6040"};
        String[] rankNames  = {"1st", "2nd", "3rd"};
        HBox podiumRow = new HBox(20);
        for (int i = 0; i < Math.min(3, tableItems.size()); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.getOrDefault(d, new int[]{0,0});
            int w = wr[0], l = wr[1];
            String pct = (w + l == 0) ? "—" : (int)Math.round(100.0*w/(w+l)) + "%";
            VBox card = new VBox(3);
            card.setPadding(new Insets(8, 14, 8, 14));
            card.setStyle("-fx-background-color: #13151F; -fx-border-color: #1E2130; "
                        + "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
            Label rank = new Label(rankNames[i]);
            rank.setStyle("-fx-text-fill: " + rankColors[i] + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label name = new Label(d.sample().displayName());
            name.setStyle("-fx-text-fill: " + rankColors[i] + "; -fx-font-size: 15px; -fx-font-weight: bold;");
            Label wLbl = new Label(w + "W");
            wLbl.setStyle("-fx-text-fill: #5ABF78; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label lLbl = new Label(l + "L");
            lLbl.setStyle("-fx-text-fill: #C8706A; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label pctLbl = new Label(pct);
            pctLbl.setStyle("-fx-text-fill: #606880; -fx-font-size: 12px;");
            HBox recordRow = new HBox(6, wLbl, lLbl, pctLbl);
            recordRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(rank, name, recordRow);
            podiumRow.getChildren().add(card);
        }
        statsBox.getChildren().add(podiumRow);

        // Additional stats
        List<String> perfect = tableItems.stream()
            .filter(d -> { int[] w = res.get(d); return w != null && w[1] == 0 && w[0] > 0; })
            .map(d -> d.sample().displayName()).toList();
        if (!perfect.isEmpty())
            statsBox.getChildren().add(statChip("Unbeaten", String.join(", ", perfect), "#1A3A1A", "#4CAF50"));

        List<String> zero = tableItems.stream()
            .filter(d -> { int[] w = res.get(d); return w != null && w[0] == 0 && w[1] > 0; })
            .map(d -> d.sample().displayName()).toList();
        if (!zero.isEmpty())
            statsBox.getChildren().add(statChip("Winless", String.join(", ", zero), "#3A1A1A", "#C8706A"));

        // Closest race
        double minGap = Double.MAX_VALUE; String closestPair = null;
        for (int i = 0; i + 1 < tableItems.size(); i++) {
            int[] wi = res.get(tableItems.get(i));
            int[] wj = res.get(tableItems.get(i + 1));
            if (wi == null || wj == null || wi[0]+wi[1] == 0 || wj[0]+wj[1] == 0) continue;
            double gap = (double)wi[0]/(wi[0]+wi[1]) - (double)wj[0]/(wj[0]+wj[1]);
            if (gap < minGap) {
                minGap = gap;
                closestPair = tableItems.get(i).sample().displayName() + " vs "
                    + tableItems.get(i+1).sample().displayName();
            }
        }
        if (closestPair != null && minGap < 0.06)
            statsBox.getChildren().add(statChip("Closest race", closestPair
                    + "  (" + (int)Math.round((1-minGap)*100) + "% each)", "#1A2A3A", "#3E68A8"));

        statsBox.getChildren().add(statChip("Duration",
                formatDuration(durationMs) + "  ·  " + strategies.size()
                + " strategies  ·  " + runner.totalGames(strategies) + " games",
                "#1A1A2A", "#8890A8"));

        summaryBox.getChildren().add(statsBox);
        summaryBox.getChildren().add(separator());

        // ── Head-to-Head breakdown accordion ─────────────────────────────────
        Label h2hTitle = new Label("HEAD-TO-HEAD BREAKDOWN");
        h2hTitle.getStyleClass().add("tournament-section-title");
        summaryBox.getChildren().add(h2hTitle);

        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        for (Difficulty d : tableItems) {
            int[] wr = res.getOrDefault(d, new int[]{0,0});
            int w = wr[0], l = wr[1];
            String pct = (w + l == 0) ? "—" : (int)Math.round(100.0*w/(w+l)) + "%";

            VBox content = new VBox(4);
            content.setPadding(new Insets(6, 10, 6, 10));
            content.setStyle("-fx-background-color: #0C0E14;");

            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                List<Difficulty> opponents = new ArrayList<>(tableItems);
                opponents.remove(d);
                for (Difficulty opp : opponents) {
                    int oppWins = wins.getOrDefault(opp, 0);
                    int oppLosses = 2 - oppWins; // each pair plays twice
                    content.getChildren().add(h2hRow(opp, oppWins, oppLosses, res));
                }
            }

            TitledPane pane = new TitledPane();
            pane.setExpanded(false);
            pane.setAnimated(true);
            pane.setContent(content);
            pane.setStyle("-fx-background-color: #13151F; -fx-border-color: #1E2130;");
            // Custom header label with strategy name + record
            Label headerLabel = new Label(d.sample().displayName()
                    + "     " + w + "W  " + l + "L  ·  " + pct);
            headerLabel.setStyle("-fx-text-fill: #8890A8; -fx-font-size: 13px; -fx-font-weight: bold;");
            pane.setGraphic(headerLabel);
            pane.setText("");
            summaryBox.getChildren().add(pane);
        }
    }

    /** Builds a notable-game card with a self-sizing BoardPane and adds it to the row. */
    private void addNotableCard(HBox row, GameRecord rec, String title, String stat, String accentColor) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label d1Lbl = new Label(rec.d1().sample().displayName());
        d1Lbl.setStyle("-fx-text-fill: #9E4A40; -fx-font-size: 13px; -fx-font-weight: bold;");
        Label vsLbl = new Label("vs");
        vsLbl.setStyle("-fx-text-fill: #3A3F58; -fx-font-size: 11px;");
        Label d2Lbl = new Label(rec.d2().sample().displayName());
        d2Lbl.setStyle("-fx-text-fill: #3E68A8; -fx-font-size: 13px; -fx-font-weight: bold;");
        HBox matchupRow = new HBox(6, d1Lbl, vsLbl, d2Lbl);
        matchupRow.setAlignment(Pos.CENTER_LEFT);

        String winnerColor = rec.winner() == rec.d1() ? "#9E4A40" : "#3E68A8";
        Label winnerLbl = new Label(rec.winner() != null ? rec.winner().sample().displayName() : "—");
        winnerLbl.setStyle("-fx-text-fill: " + winnerColor + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label statLbl = new Label(stat);
        statLbl.setStyle("-fx-text-fill: #3A3F58; -fx-font-size: 11px;");
        HBox winnerRow = new HBox(6, winnerLbl, statLbl);
        winnerRow.setAlignment(Pos.CENTER_LEFT);

        BoardPane boardPane = new BoardPane(rec.finalState(), rec.wallOwners());
        VBox card = new VBox(5, titleLbl, matchupRow, winnerRow, boardPane);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #13151F; -fx-border-color: " + accentColor
                + "; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(card);
    }

    /**
     * A Region that wraps a Canvas for square board rendering.
     * minWidth=0 ensures the canvas never pushes the layout wider.
     * computePrefHeight(w)=w so VBox always allocates a square slot.
     */
    private static final class BoardPane extends Region {
        private final Canvas canvas = new Canvas(1, 1);
        private final GameState state;
        private final Map<Wall, Player> wallOwners;

        BoardPane(GameState state, Map<Wall, Player> wallOwners) {
            this.state = state;
            this.wallOwners = wallOwners;
            getChildren().add(canvas);
            setMinSize(0, 0);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        @Override protected void layoutChildren() {
            double w = getWidth();
            if (w < 1) return;
            canvas.setWidth(w);
            canvas.setHeight(w);
            canvas.setLayoutX(0);
            canvas.setLayoutY(0);
            drawBoard(canvas, state, wallOwners);
            setClip(new javafx.scene.shape.Rectangle(w, w));
        }

        @Override protected double computeMinWidth(double h)   { return 0; }
        @Override protected double computeMinHeight(double w)  { return 0; }
        @Override protected double computePrefWidth(double h)  { return SUMMARY_BOARD_PX; }
        @Override protected double computePrefHeight(double w) { return w > 0 ? w : SUMMARY_BOARD_PX; }
        @Override protected double computeMaxWidth(double h)   { return Double.MAX_VALUE; }
        @Override protected double computeMaxHeight(double w)  { return Double.MAX_VALUE; }
    }

    /** A row in the head-to-head breakdown for one opponent. */
    private HBox h2hRow(Difficulty opp, int wins, int losses, Map<Difficulty, int[]> res) {
        Label oppLabel = new Label(opp.sample().displayName());
        oppLabel.setStyle("-fx-text-fill: #606880; -fx-font-size: 12px;");
        oppLabel.setMinWidth(120);

        // Win/loss chip
        String chipBg, chipFg, chipText;
        if (wins == 2)       { chipBg = "#1A3A1A"; chipFg = "#4CAF50"; chipText = "✓✓  2–0"; }
        else if (wins == 1)  { chipBg = "#2A2A1A"; chipFg = "#B8960C"; chipText = "✓✗  1–1"; }
        else                 { chipBg = "#3A1A1A"; chipFg = "#C8706A"; chipText = "✗✗  0–2"; }

        Label chip = new Label(chipText);
        chip.setStyle("-fx-background-color: " + chipBg + "; -fx-text-fill: " + chipFg
                + "; -fx-font-size: 11px; -fx-font-weight: bold;"
                + " -fx-padding: 2 8 2 8; -fx-background-radius: 3;");

        HBox row = new HBox(10, oppLabel, chip);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));
        row.setStyle("-fx-border-color: transparent transparent #191C2A transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    /** An info row with a colored left accent line for the key-stats section. */
    private HBox statChip(String label, String value, String bg, String fg) {
        Label key = new Label(label.toUpperCase());
        key.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-min-width: 86px;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #7880A0; -fx-font-size: 12px;");
        HBox row = new HBox(14, key, val);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 12, 5, 14));
        row.setStyle("-fx-background-color: " + bg + "44;"
                + " -fx-border-color: " + fg + " transparent transparent transparent;"
                + " -fx-border-width: 0 0 0 3;");
        return row;
    }

    private Separator separator() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #1E2130;");
        return s;
    }

    private static String abbrev(String name) {
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }

    // ── Board canvas drawing ──────────────────────────────────────────────────

    private static void drawBoard(Canvas canvas, GameState state, Map<Wall, Player> wallOwners) {
        double boardPx = canvas.getWidth();
        GraphicsContext g = canvas.getGraphicsContext2D();
        double scale  = boardPx / DESIGN_SIZE;
        double cell   = DESIGN_CELL * scale;
        double gap    = DESIGN_GAP  * scale;
        double step   = DESIGN_STEP * scale;
        double stripH = GOAL_STRIP_RATIO * cell;
        int    n      = GameState.BOARD_SIZE;

        g.setFill(BG_COLOR); g.fillRect(0, 0, boardPx, boardPx);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                double x = c * step, y = r * step;
                g.setFill(CELL_CLR); g.fillRect(x, y, cell, cell);
                if (r == Player.ONE.goalRow()) {
                    g.setFill(P1_STRIP); g.fillRect(x, y, cell, stripH);
                } else if (r == Player.TWO.goalRow()) {
                    g.setFill(P2_STRIP); g.fillRect(x, y + cell - stripH, cell, stripH);
                }
            }
        }
        if (state != null) {
            for (Wall w : state.getWalls()) {
                Player owner = wallOwners != null ? wallOwners.get(w) : null;
                g.setFill(owner == Player.TWO ? P2_COLOR : P1_COLOR);
                double wx = w.col() * step, wy = w.row() * step, len = 2 * cell + gap;
                if (w.orientation() == Wall.Orientation.HORIZONTAL) g.fillRect(wx, wy + cell, len, gap);
                else                                                 g.fillRect(wx + cell, wy, gap, len);
            }
            double pad = cell * PAWN_PAD_RATIO;
            Position pp1 = state.getPawnPosition(Player.ONE);
            Position pp2 = state.getPawnPosition(Player.TWO);
            g.setFill(P1_COLOR);
            g.fillOval(pp1.col()*step+pad, pp1.row()*step+pad, cell-2*pad, cell-2*pad);
            g.setFill(P2_COLOR);
            g.fillOval(pp2.col()*step+pad, pp2.row()*step+pad, cell-2*pad, cell-2*pad);
        }
    }

    // ── Pause / resume ────────────────────────────────────────────────────────

    private void togglePause() {
        paused = !paused;
        if (paused) {
            runner.pause();
            pauseIcon.setIconCode(FontAwesomeSolid.PLAY);
            pauseBtn.setText("Resume");
            titleLabel.setText("TOURNAMENT — PAUSED");
        } else {
            runner.resume();
            pauseIcon.setIconCode(FontAwesomeSolid.PAUSE);
            pauseBtn.setText("Pause");
            titleLabel.setText("TOURNAMENT");
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
        var liveStates   = runner.getLiveStates();
        var liveMatchups = runner.getLiveMatchups();
        var liveWO       = runner.getLiveWallOwners();
        Set<Integer> current = new HashSet<>(liveStates.keySet());

        for (int id : current) {
            GameState state    = liveStates.get(id);
            Difficulty[] match = liveMatchups.get(id);
            if (state == null || match == null) continue;
            var wallOwners = liveWO.getOrDefault(id, new java.util.concurrent.ConcurrentHashMap<>());

            MiniBoard mb = activeBoards.computeIfAbsent(id, k -> {
                MiniBoard b = new MiniBoard(match[0], match[1]);
                b.card.setCursor(Cursor.HAND);
                b.card.setOnMouseClicked(e -> togglePin(k, b));
                boardGrid.getChildren().add(b.card);
                return b;
            });
            mb.draw(state, wallOwners);
            mb.updateSelection(selectedStrategies.contains(mb.d1), selectedStrategies.contains(mb.d2));
        }

        activeBoards.entrySet().removeIf(e -> {
            int id = e.getKey();
            if (current.contains(id)) return false;
            MiniBoard mb = e.getValue();

            GameState finalState = runner.getFinalGameStates().get(id);
            var finalWO = runner.getFinalWallOwners().getOrDefault(id,
                                  new java.util.concurrent.ConcurrentHashMap<>());
            if (finalState != null) mb.draw(finalState, finalWO);

            Difficulty winner = runner.getLiveWinners().get(id);
            String wName  = winner != null ? winner.sample().displayName() : null;
            Color  wColor = (winner == mb.d1) ? P1_COLOR : P2_COLOR;
            mb.drawWinnerOverlay(wName, wColor);

            if (pinnedGameIds.remove(id)) {
                mb.setPinned(true);
                mb.card.setOnMouseClicked(ev -> dismissFrozen(id, mb));
                var fo = runner.getFinalWallOwners().getOrDefault(id,
                                  new java.util.concurrent.ConcurrentHashMap<>());
                pinnedOverlays.put(id, new PinnedOverlay(mb, now,
                    finalState != null ? finalState : new GameState(), fo));
            } else {
                finishingGames.put(id, new FinishingGame(mb, now));
            }
            return true;
        });

        finishingGames.entrySet().removeIf(e -> {
            if (now - e.getValue().startNs() < FINISH_OVERLAY_NS) return false;
            MiniBoard mb = e.getValue().board();
            FadeTransition ft = new FadeTransition(Duration.millis(300), mb.card);
            ft.setToValue(0);
            ft.setOnFinished(ev -> { mb.card.setOpacity(1); removeWithShift(mb.card); });
            ft.play();
            return true;
        });

        pinnedOverlays.entrySet().removeIf(e -> {
            PinnedOverlay po = e.getValue();
            if (now - po.startNs() < FINISH_OVERLAY_NS) return false;
            po.board().draw(po.finalState(), po.finalWO());
            frozenBoards.put(e.getKey(), po.board());
            return true;
        });

        for (MiniBoard mb : frozenBoards.values())
            mb.updateSelection(selectedStrategies.contains(mb.d1), selectedStrategies.contains(mb.d2));
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
        boardGrid.applyCss();
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
                return (wr == null || wr[0]+wr[1] == 0) ? 0.0 : -(double)wr[0]/(wr[0]+wr[1]);
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

        tv.setRowFactory(t -> new TableRow<Difficulty>() {
            {
                setCursor(Cursor.HAND);
                setOnMouseClicked(e -> {
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
                int idx = tableItems.indexOf(item);
                if      (idx == 0) getStyleClass().add("rank-top");
                else if (idx == 1) getStyleClass().add("rank-second");
                else if (idx == 2) getStyleClass().add("rank-third");
                if (selectedStrategies.contains(item))
                    setStyle("-fx-border-color: transparent transparent #191C2A #D4AC0D;"
                           + "-fx-border-width: 0 0 1 3;");
            }
        });

        TableColumn<Difficulty, Number> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(cd -> new SimpleIntegerProperty(tableItems.indexOf(cd.getValue()) + 1));
        rankCol.setPrefWidth(36); rankCol.setMinWidth(32); rankCol.setMaxWidth(44); rankCol.setSortable(false);

        TableColumn<Difficulty, String> nameCol = new TableColumn<>("Strategy");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().sample().displayName()));
        nameCol.setMinWidth(100); nameCol.setSortable(false);

        TableColumn<Difficulty, Number> wCol = new TableColumn<>("W");
        wCol.setCellValueFactory(cd -> { int[] wr = runner.getResults().get(cd.getValue()); return new SimpleIntegerProperty(wr == null ? 0 : wr[0]); });
        wCol.setPrefWidth(44); wCol.setMinWidth(38); wCol.setMaxWidth(56); wCol.setSortable(false);

        TableColumn<Difficulty, Number> lCol = new TableColumn<>("L");
        lCol.setCellValueFactory(cd -> { int[] wr = runner.getResults().get(cd.getValue()); return new SimpleIntegerProperty(wr == null ? 0 : wr[1]); });
        lCol.setPrefWidth(44); lCol.setMinWidth(38); lCol.setMaxWidth(56); lCol.setSortable(false);

        TableColumn<Difficulty, String> pctCol = new TableColumn<>("Win%");
        pctCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            if (wr == null || wr[0]+wr[1] == 0) return new SimpleStringProperty("—");
            return new SimpleStringProperty(String.format("%d%%", (int)Math.round(100.0*wr[0]/(wr[0]+wr[1]))));
        });
        pctCol.setPrefWidth(52); pctCol.setMinWidth(46); pctCol.setMaxWidth(66); pctCol.setSortable(false);

        tv.getColumns().addAll(rankCol, nameCol, wCol, lCol, pctCol);
        return tv;
    }

    // ── Clipboard export ──────────────────────────────────────────────────────

    private void copyToClipboard() {
        ClipboardContent c = new ClipboardContent();
        c.putString(formatResults());
        Clipboard.getSystemClipboard().setContent(c);
    }

    private String formatResults() {
        int total = runner.totalGames(strategies);
        Map<Difficulty, int[]> res = runner.getResults();
        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        StringBuilder sb = new StringBuilder();
        sb.append("CHORIDOR TOURNAMENT RESULTS\n")
          .append(strategies.size()).append(" strategies · ").append(total).append(" games\n\n");
        sb.append(String.format("%-4s  %-18s  %-6s  %-6s  %-5s%n", "#", "Strategy", "+WINS", "-LOSS", "Win%"));
        sb.append("─".repeat(46)).append("\n");
        for (int i = 0; i < tableItems.size(); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0], l = wr == null ? 0 : wr[1];
            String pct = (w+l == 0) ? "—" : String.format("%.0f%%", 100.0*w/(w+l));
            sb.append(String.format("%-4d  %-18s  %-6d  %-6d  %-5s%n", i+1, d.sample().displayName(), w, l, pct));
        }
        sb.append("\n\nHEAD-TO-HEAD BREAKDOWN\n").append("─".repeat(40)).append("\n");
        for (Difficulty d : tableItems) {
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0], l = wr == null ? 0 : wr[1];
            String pct = (w+l == 0) ? "—" : String.format("%.0f%%", 100.0*w/(w+l));
            sb.append(d.sample().displayName()).append("  (").append(w).append("W–").append(l).append("L  ").append(pct).append(")\n");
            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                wins.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<Difficulty,Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().sample().displayName()))
                    .forEach(e -> {
                        int ww = e.getValue(), ll = 2-ww;
                        String m = ww==2?"✓✓":ww==1?"✓✗":"✗✗";
                        sb.append(String.format("  %s  %-16s  %d–%d%n", m, e.getKey().sample().displayName(), ww, ll));
                    });
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── MiniBoard ─────────────────────────────────────────────────────────────

    private static final class MiniBoard {
        final Difficulty d1, d2;
        final VBox       card;
        final Canvas     canvas  = new Canvas(BOARD_PX, BOARD_PX);
        final FontIcon   pinIcon;

        MiniBoard(Difficulty d1, Difficulty d2) {
            this.d1 = d1; this.d2 = d2;

            Label p1lbl = new Label(abbrev12(d1.sample().displayName()));
            p1lbl.getStyleClass().add("mini-p1");
            Label vs = new Label("vs");
            vs.getStyleClass().add("mini-vs");
            Label p2lbl = new Label(abbrev12(d2.sample().displayName()));
            p2lbl.getStyleClass().add("mini-p2");

            pinIcon = new FontIcon(FontAwesomeSolid.THUMBTACK);
            pinIcon.setIconSize(10);
            pinIcon.setIconColor(Color.web("#D4AC0D"));
            pinIcon.setVisible(false);

            HBox names = new HBox(6, p1lbl, vs, p2lbl, pinIcon);
            names.setAlignment(Pos.CENTER);

            card = new VBox(6, names, canvas);
            card.getStyleClass().add("mini-board-card");
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(10));
        }

        void setPinned(boolean pinned) { pinIcon.setVisible(pinned); }

        void updateSelection(boolean d1Sel, boolean d2Sel) {
            if (d1Sel && d2Sel) card.setStyle(BORDER_BOTH);
            else if (d1Sel)     card.setStyle(BORDER_P1);
            else if (d2Sel)     card.setStyle(BORDER_P2);
            else                card.setStyle("");
        }

        void draw(GameState state, Map<Wall, Player> wallOwners) {
            drawBoard(canvas, state, wallOwners);
        }

        void drawWinnerOverlay(String winnerName, Color winnerColor) {
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setFill(Color.color(0, 0, 0, 0.72));
            g.fillRect(0, 0, BOARD_PX, BOARD_PX);
            if (winnerName == null) return;
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.setFill(winnerColor.brighter());
            g.setFont(Font.font("System", FontWeight.BOLD, BOARD_PX / 15));
            g.fillText(winnerName, BOARD_PX / 2, BOARD_PX / 2 - BOARD_PX / 12);
            g.setFill(Color.web("#8890A8"));
            g.setFont(Font.font("System", BOARD_PX / 22));
            g.fillText("wins!", BOARD_PX / 2, BOARD_PX / 2 + BOARD_PX / 14);
        }

        private static String abbrev12(String name) {
            return name.length() > 12 ? name.substring(0, 11) + "…" : name;
        }
    }
}

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

/**
 * Full-screen tournament view: live boards during play, rich summary after.
 * Board rendering → {@link BoardRenderer}
 * Live board card  → {@link MiniBoard}
 * Post-tournament  → {@link TournamentSummary}
 */
public final class TournamentView {

    private static final String ICON_COLOR_BTN  = "#8890A8";
    private static final String ICON_COLOR_STOP = "#C8706A";
    private static final long   FINISH_OVERLAY_NS = 1_500_000_000L;
    private static final int    MAX_RESULTS       = 20;
    private static final int    ETA_WINDOW = 20;
    private static final double ETA_ALPHA  = 0.25;

    private final AudioClip selectSound = new AudioClip(
        getClass().getResource("/audio/sfx/Select.wav").toExternalForm());
        private final AudioClip pinSound = new AudioClip(
        getClass().getResource("/audio/sfx/Pin.wav").toExternalForm());

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
    private boolean running = false;
    private boolean paused  = false;
    private long    tournamentStartMs;
    private int     totalCount;
    private long    etaDisplayMs = 0;
    private final ArrayDeque<Long> recentGameTimes = new ArrayDeque<>();
    private final GameController ctrl;

    public TournamentView(Runnable onClose, GameController ctrl) {
        this.ctrl = ctrl;
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
        restartBtn.setOnAction(e -> start());

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
        boardGrid.setPrefTileWidth((int) MiniBoard.BOARD_PX + 24);
        boardGrid.setPrefTileHeight((int) MiniBoard.BOARD_PX + 52);
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

    // ── Start ─────────────────────────────────────────────────────────────────

    public void start() {
        running = true;
        paused  = false;
        tournamentStartMs = System.currentTimeMillis();
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
                progressBar.setProgress((double) done / t);
                progressLabel.setText(done + " / " + t);
                resort();
                long now = System.currentTimeMillis();
                recentGameTimes.addLast(now);
                if (recentGameTimes.size() > ETA_WINDOW) recentGameTimes.pollFirst();
                if (recentGameTimes.size() >= 2) {
                    long windowMs = recentGameTimes.peekLast() - recentGameTimes.peekFirst();
                    if (windowMs > 0) {
                        double rate  = (recentGameTimes.size() - 1.0) / windowMs;
                        long newEta  = (long) ((t - done) / rate);
                        etaDisplayMs = etaDisplayMs == 0 ? newEta
                                     : (long) (ETA_ALPHA * newEta + (1.0 - ETA_ALPHA) * etaDisplayMs);
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
                TournamentSummary.populate(summaryBox, runner, tableItems, strategies, durationMs);
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

    // ── Pause / resume ────────────────────────────────────────────────────────

    private void togglePause() {
        paused = !paused;
        if (!ctrl.isMuted()) selectSound.play();
        if (paused) {
            runner.pause();
            if (etaTimeline != null) etaTimeline.pause();
            pauseIcon.setIconCode(FontAwesomeSolid.PLAY);
            pauseBtn.setText("Resume");
            titleLabel.setText("TOURNAMENT — PAUSED");
        } else {
            runner.resume();
            if (etaTimeline != null) etaTimeline.play();
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
                b.card.setOnMouseClicked(e -> { togglePin(k, b); if (!ctrl.isMuted()) pinSound.play(); });
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
            Color  wColor = (winner == mb.d1) ? BoardRenderer.P1_COLOR : BoardRenderer.P2_COLOR;
            mb.drawWinnerOverlay(wName, wColor);

            if (pinnedGameIds.remove(id)) {
                mb.setPinned(true);
                mb.card.setOnMouseClicked(ev -> { dismissFrozen(id, mb); if (!ctrl.isMuted()) pinSound.play(); });
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
                return (wr == null || wr[0]+wr[1] == 0) ? 0.0 : -(double) wr[0] / (wr[0]+wr[1]);
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
            return new SimpleStringProperty(String.format("%d%%", (int) Math.round(100.0*wr[0]/(wr[0]+wr[1]))));
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
                    .sorted(Comparator.<Map.Entry<Difficulty, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().sample().displayName()))
                    .forEach(e -> {
                        int ww = e.getValue(), ll = 2 - ww;
                        String m = ww==2 ? "✓✓" : ww==1 ? "✓✗" : "✗✗";
                        sb.append(String.format("  %s  %-16s  %d–%d%n", m, e.getKey().sample().displayName(), ww, ll));
                    });
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * Full-screen tournament view with live boards, standings, and recent results.
 *
 * <p><b>Selection</b>: clicking a standings row persistently toggles that strategy.
 * Active board cards whose matchup involves a selected strategy show a coloured border
 * (P1 red / P2 blue / both = gold). Multiple strategies can be selected simultaneously.
 *
 * <p><b>Pinning</b>: clicking a live board card pins it. Pinned boards survive the game
 * finishing — they freeze on the final position with the winner overlay and stay until
 * clicked again to dismiss.
 *
 * <p><b>Winner overlay</b>: when a game finishes, the board shows a dark overlay naming
 * the winner in the player's colour. Un-pinned boards fade out after 1.5 s; pinned
 * boards stay indefinitely.
 *
 * <p><b>Animated shift</b>: when a board card is removed, remaining cards slide into
 * their new positions with a short ease-out transition.
 */
public final class TournamentView {

    // ── Board rendering — mirrors BoardView's design constants exactly ──────
    private static final double DESIGN_CELL = 54;
    private static final double DESIGN_GAP  = 10;
    private static final double DESIGN_STEP = DESIGN_CELL + DESIGN_GAP;
    private static final double DESIGN_SIZE = GameState.BOARD_SIZE * DESIGN_CELL
                                            + (GameState.BOARD_SIZE - 1) * DESIGN_GAP;
    private static final double BOARD_PX    = 370;
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

    // ── State ─────────────────────────────────────────────────────────────
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

    private final TilePane boardGrid    = new TilePane(10, 10);
    private final VBox     summaryBox   = new VBox(5);

    // Active games: gameId → MiniBoard (game still running)
    private final Map<Integer, MiniBoard> activeBoards  = new LinkedHashMap<>();
    // Finishing games: board stays 1.5 s showing winner overlay, then fades + shifts out
    private record FinishingGame(MiniBoard board, long startNs) {}
    private final Map<Integer, FinishingGame> finishingGames = new LinkedHashMap<>();
    // Pinned overlays: pinned boards showing 1.5 s winner overlay; after timeout the overlay
    // is cleared and the final board position is shown until the user dismisses it
    private record PinnedOverlay(MiniBoard board, long startNs,
                                 GameState finalState,
                                 java.util.concurrent.ConcurrentHashMap<Wall, Player> finalWO) {}
    private final Map<Integer, PinnedOverlay> pinnedOverlays = new LinkedHashMap<>();
    // Frozen boards: pinned boards with overlay cleared — show final position until dismissed
    private final Map<Integer, MiniBoard>     frozenBoards   = new LinkedHashMap<>();

    private final Set<Difficulty> selectedStrategies = new HashSet<>();
    private final Set<Integer>    pinnedGameIds      = new HashSet<>();

    private TableView<Difficulty> standingsTable;
    private AnimationTimer animTimer;
    private boolean running = false;
    private boolean paused  = false;
    private long    tournamentStartMs;

    public TournamentView(Runnable onClose) {
        root = new StackPane();
        root.getStyleClass().add("tournament-view");
        root.setVisible(false);

        tableItems    = FXCollections.observableArrayList(strategies);
        standingsTable = buildTable();

        // ── Top bar ───────────────────────────────────────────────────────
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

        // ── Live boards ───────────────────────────────────────────────────
        boardGrid.getStyleClass().add("tournament-board-grid");
        boardGrid.setPrefTileWidth((int) BOARD_PX + 24);
        boardGrid.setPrefTileHeight((int) BOARD_PX + 52);
        boardGrid.setPadding(new Insets(10));

        Label boardsTitle = new Label("LIVE GAMES");
        boardsTitle.getStyleClass().add("tournament-section-title");

        Label noGamesLabel = new Label("Waiting for games to start…");
        noGamesLabel.getStyleClass().add("tournament-empty-label");

        StackPane boardsContent = new StackPane(boardGrid, noGamesLabel);
        boardGrid.getChildren().addListener(
            (javafx.collections.ListChangeListener<javafx.scene.Node>) c ->
                noGamesLabel.setVisible(boardGrid.getChildren().isEmpty()));

        ScrollPane boardsScroll = new ScrollPane(boardsContent);
        boardsScroll.setFitToWidth(true);
        boardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        boardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        boardsScroll.getStyleClass().add("tournament-board-scroll");
        VBox.setVgrow(boardsScroll, Priority.ALWAYS);

        VBox boardsSection = new VBox(8, boardsTitle, boardsScroll);
        boardsSection.getStyleClass().add("tournament-panel");
        boardsSection.setPadding(new Insets(14));
        HBox.setHgrow(boardsSection, Priority.ALWAYS);

        // ── Right panel ───────────────────────────────────────────────────
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

        // summaryBox is empty until tournament completes (takes no space)
        summaryBox.setPadding(new Insets(4, 0, 4, 0));

        VBox rightPanel = new VBox(10,
                standingsTitle, standingsTable,
                summaryBox,
                resultsTitle, resultsList);
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
        selectedStrategies.clear();
        pinnedGameIds.clear();
        finishingGames.clear();
        pinnedOverlays.clear();
        frozenBoards.clear();
        summaryBox.getChildren().clear();
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
        int total = runner.totalGames(strategies);
        progressLabel.setText("0 / " + total);
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
                updateEta(done, t);
                resort();
            },
            (winner, loser) -> {
                int[] wr = runner.getResults().get(winner);
                String pct = (wr == null || wr[0] + wr[1] == 0) ? ""
                    : String.format(" (%d%%)", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1])));
                recentResults.add(0, "  " + winner.sample().displayName()
                    + " beat " + loser.sample().displayName() + pct);
                if (recentResults.size() > MAX_RESULTS)
                    recentResults.remove(recentResults.size() - 1);
            },
            () -> {
                running = false;
                long durationMs = System.currentTimeMillis() - tournamentStartMs;
                titleLabel.setText("TOURNAMENT RESULTS");
                stopIcon.setIconCode(FontAwesomeSolid.TIMES);
                actionBtn.setText("Close");
                pauseBtn.setDisable(true);
                progressBar.setProgress(1.0);
                progressLabel.setText(runner.totalGames(strategies) + " games");
                etaLabel.setText("  " + formatDuration(durationMs));
                restartBtn.setVisible(true);
                restartBtn.setManaged(true);
                resort();
                populateSummary(durationMs);
            });
    }

    // ── ETA ───────────────────────────────────────────────────────────────

    private void updateEta(int done, int total) {
        if (done == 0) return;
        long elapsed = System.currentTimeMillis() - tournamentStartMs;
        long remaining = elapsed * (total - done) / done;
        etaLabel.setText("  ~" + formatDuration(remaining));
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    // ── Summary ───────────────────────────────────────────────────────────

    private void populateSummary(long durationMs) {
        summaryBox.getChildren().clear();

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #1E2130;");
        summaryBox.getChildren().add(sep);

        Label title = new Label("SUMMARY");
        title.getStyleClass().add("tournament-section-title");
        summaryBox.getChildren().add(title);

        Label dur = new Label("Completed in " + formatDuration(durationMs)
                + "  ·  " + strategies.size() + " strategies");
        dur.setStyle("-fx-text-fill: #606880; -fx-font-size: 12px;");
        summaryBox.getChildren().add(dur);

        String[] rankColors = {"#B8960C", "#8896A0", "#8B6040"};
        String[] rankNames  = {"1st", "2nd", "3rd"};
        Map<Difficulty, int[]> res = runner.getResults();
        for (int i = 0; i < Math.min(3, tableItems.size()); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0];
            int l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : (int) Math.round(100.0 * w / (w + l)) + "%";
            Label lbl = new Label(rankNames[i] + "  " + d.sample().displayName()
                    + "  —  " + w + "W " + l + "L  " + pct);
            lbl.setStyle("-fx-text-fill: " + rankColors[i]
                    + "; -fx-font-size: 12px; -fx-font-weight: bold;");
            summaryBox.getChildren().add(lbl);
        }
    }

    // ── Pause / resume ────────────────────────────────────────────────────

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

    // ── Animation timer ───────────────────────────────────────────────────

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

        // ── 1. Add / update active boards ────────────────────────────────
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

        // ── 2. Detect newly finished active boards ────────────────────────
        activeBoards.entrySet().removeIf(e -> {
            int id = e.getKey();
            if (current.contains(id)) return false;
            MiniBoard mb = e.getValue();

            // Draw true final position (captured before liveStates was cleared)
            GameState finalState = runner.getFinalGameStates().get(id);
            var finalWO = runner.getFinalWallOwners().getOrDefault(id,
                                  new java.util.concurrent.ConcurrentHashMap<>());
            if (finalState != null) mb.draw(finalState, finalWO);

            // Overlay winner once — canvas retains it
            Difficulty winner = runner.getLiveWinners().get(id);
            String wName  = winner != null ? winner.sample().displayName() : null;
            Color  wColor = (winner == mb.d1) ? P1_COLOR : P2_COLOR;
            mb.drawWinnerOverlay(wName, wColor);

            if (pinnedGameIds.remove(id)) {
                // Pinned: show overlay for 1.5 s, then clear to reveal final position
                mb.setPinned(true);
                mb.card.setOnMouseClicked(ev -> dismissFrozen(id, mb));
                var fo = (java.util.concurrent.ConcurrentHashMap<Wall, Player>) runner
                        .getFinalWallOwners().getOrDefault(id,
                        new java.util.concurrent.ConcurrentHashMap<>());
                pinnedOverlays.put(id, new PinnedOverlay(mb, now, finalState != null ? finalState : new GameState(), fo));
            } else {
                finishingGames.put(id, new FinishingGame(mb, now));
            }
            return true;
        });

        // ── 3. Age finishing boards → fade + animated shift out ───────────
        finishingGames.entrySet().removeIf(e -> {
            FinishingGame fg = e.getValue();
            if (now - fg.startNs() < FINISH_OVERLAY_NS) return false;
            MiniBoard mb = fg.board();
            FadeTransition ft = new FadeTransition(Duration.millis(300), mb.card);
            ft.setToValue(0);
            ft.setOnFinished(ev -> {
                mb.card.setOpacity(1);
                removeWithShift(mb.card);
            });
            ft.play();
            return true;
        });

        // ── 4. Age pinned overlays — clear overlay after 1.5 s, reveal final state ──
        pinnedOverlays.entrySet().removeIf(e -> {
            PinnedOverlay po = e.getValue();
            if (now - po.startNs() < FINISH_OVERLAY_NS) return false;
            po.board().draw(po.finalState(), po.finalWO()); // clear the overlay
            frozenBoards.put(e.getKey(), po.board());
            return true;
        });

        // ── 5. Keep frozen board selection borders current ────────────────
        for (MiniBoard mb : frozenBoards.values()) {
            mb.updateSelection(selectedStrategies.contains(mb.d1), selectedStrategies.contains(mb.d2));
        }
    }

    // ── Board grid helpers ────────────────────────────────────────────────

    private void togglePin(int gameId, MiniBoard board) {
        if (!pinnedGameIds.remove(gameId)) {
            pinnedGameIds.add(gameId);
            board.setPinned(true);
        } else {
            board.setPinned(false);
        }
    }

    private void dismissFrozen(int gameId, MiniBoard board) {
        frozenBoards.remove(gameId);
        pinnedOverlays.remove(gameId); // also handles dismiss during overlay phase
        removeWithShift(board.card);
    }

    /**
     * Removes {@code card} from the board grid and slides the remaining cards into
     * their new positions with a short ease-out transition.
     */
    private void removeWithShift(Node card) {
        List<Node> siblings = new ArrayList<>(boardGrid.getChildren());
        siblings.remove(card);

        // Snapshot positions before removal
        Map<Node, Point2D> before = new LinkedHashMap<>();
        for (Node n : siblings) before.put(n, new Point2D(n.getLayoutX(), n.getLayoutY()));

        boardGrid.getChildren().remove(card);
        boardGrid.applyCss();
        boardGrid.layout();

        // Animate each card from its old position to its new one
        for (Map.Entry<Node, Point2D> entry : before.entrySet()) {
            Node n = entry.getKey();
            double dx = entry.getValue().getX() - n.getLayoutX();
            double dy = entry.getValue().getY() - n.getLayoutY();
            if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) continue;
            n.setTranslateX(dx);
            n.setTranslateY(dy);
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), n);
            tt.setToX(0);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
        }
    }

    // ── Standings ─────────────────────────────────────────────────────────

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

                if (selectedStrategies.contains(item)) {
                    setStyle("-fx-border-color: transparent transparent #191C2A #D4AC0D;"
                           + "-fx-border-width: 0 0 1 3;");
                }
            }
        });

        TableColumn<Difficulty, Number> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(cd -> new SimpleIntegerProperty(tableItems.indexOf(cd.getValue()) + 1));
        rankCol.getStyleClass().add("tournament-col-center");
        rankCol.setPrefWidth(36); rankCol.setMinWidth(32); rankCol.setMaxWidth(44);
        rankCol.setSortable(false);

        TableColumn<Difficulty, String> nameCol = new TableColumn<>("Strategy");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().sample().displayName()));
        nameCol.setMinWidth(100);
        nameCol.setSortable(false);

        TableColumn<Difficulty, Number> wCol = new TableColumn<>("W");
        wCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            return new SimpleIntegerProperty(wr == null ? 0 : wr[0]);
        });
        wCol.getStyleClass().add("tournament-col-center");
        wCol.setPrefWidth(44); wCol.setMinWidth(38); wCol.setMaxWidth(56); wCol.setSortable(false);

        TableColumn<Difficulty, Number> lCol = new TableColumn<>("L");
        lCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            return new SimpleIntegerProperty(wr == null ? 0 : wr[1]);
        });
        lCol.getStyleClass().add("tournament-col-center");
        lCol.setPrefWidth(44); lCol.setMinWidth(38); lCol.setMaxWidth(56); lCol.setSortable(false);

        TableColumn<Difficulty, String> pctCol = new TableColumn<>("Win%");
        pctCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            if (wr == null || wr[0] + wr[1] == 0) return new SimpleStringProperty("—");
            return new SimpleStringProperty(
                    String.format("%d%%", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1]))));
        });
        pctCol.getStyleClass().add("tournament-col-center");
        pctCol.setPrefWidth(52); pctCol.setMinWidth(46); pctCol.setMaxWidth(66); pctCol.setSortable(false);

        tv.getColumns().addAll(rankCol, nameCol, wCol, lCol, pctCol);
        return tv;
    }

    // ── Clipboard export ──────────────────────────────────────────────────

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
        sb.append(String.format("%-4s  %-16s  %3s  %3s  %5s%n", "#", "Strategy", "W", "L", "Win%"));
        sb.append("─".repeat(40)).append("\n");
        for (int i = 0; i < tableItems.size(); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0], l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(String.format("%-4d  %-16s  %3d  %3d  %5s%n",
                    i + 1, d.sample().displayName(), w, l, pct));
        }
        sb.append("\n\nHEAD-TO-HEAD BREAKDOWN\n").append("─".repeat(40)).append("\n");
        for (Difficulty d : tableItems) {
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0], l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(d.sample().displayName()).append("  (")
              .append(w).append("W–").append(l).append("L  ").append(pct).append(")\n");
            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                wins.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<Difficulty, Integer>>comparingInt(
                            Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().sample().displayName()))
                    .forEach(e -> {
                        int ww = e.getValue(), ll = 2 - ww;
                        String m = ww == 2 ? "✓✓" : ww == 1 ? "✓✗" : "✗✗";
                        sb.append(String.format("  %s  %-16s  %d–%d%n",
                                m, e.getKey().sample().displayName(), ww, ll));
                    });
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── MiniBoard ─────────────────────────────────────────────────────────

    private static final class MiniBoard {
        final Difficulty d1, d2;
        final VBox       card;
        final Canvas     canvas  = new Canvas(BOARD_PX, BOARD_PX);
        final FontIcon   pinIcon;

        MiniBoard(Difficulty d1, Difficulty d2) {
            this.d1 = d1;
            this.d2 = d2;

            Label p1lbl = new Label(abbrev(d1.sample().displayName()));
            p1lbl.getStyleClass().add("mini-p1");
            Label vs = new Label("vs");
            vs.getStyleClass().add("mini-vs");
            Label p2lbl = new Label(abbrev(d2.sample().displayName()));
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

        void updateSelection(boolean d1Selected, boolean d2Selected) {
            if (d1Selected && d2Selected) card.setStyle(BORDER_BOTH);
            else if (d1Selected)          card.setStyle(BORDER_P1);
            else if (d2Selected)          card.setStyle(BORDER_P2);
            else                          card.setStyle("");
        }

        void draw(GameState state, Map<Wall, Player> wallOwners) {
            GraphicsContext g = canvas.getGraphicsContext2D();
            double scale  = BOARD_PX / DESIGN_SIZE;
            double cell   = DESIGN_CELL * scale;
            double gap    = DESIGN_GAP  * scale;
            double step   = DESIGN_STEP * scale;
            double stripH = GOAL_STRIP_RATIO * cell;
            int    n      = GameState.BOARD_SIZE;

            g.setFill(BG_COLOR); g.fillRect(0, 0, BOARD_PX, BOARD_PX);
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
            for (Wall w : state.getWalls()) {
                Player owner = wallOwners.get(w);
                g.setFill(owner == Player.TWO ? P2_COLOR : P1_COLOR);
                double wx = w.col() * step, wy = w.row() * step, len = 2 * cell + gap;
                if (w.orientation() == Wall.Orientation.HORIZONTAL) g.fillRect(wx, wy + cell, len, gap);
                else                                                 g.fillRect(wx + cell, wy, gap, len);
            }
            double pad = cell * PAWN_PAD_RATIO;
            Position pp1 = state.getPawnPosition(Player.ONE);
            Position pp2 = state.getPawnPosition(Player.TWO);
            paintPawn(g, pp1.col(), pp1.row(), P1_COLOR, step, cell, pad);
            paintPawn(g, pp2.col(), pp2.row(), P2_COLOR, step, cell, pad);
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

        private static void paintPawn(GraphicsContext g, int col, int row,
                                      Color color, double step, double cell, double pad) {
            g.setFill(color);
            g.fillOval(col * step + pad, row * step + pad, cell - 2 * pad, cell - 2 * pad);
        }

        private static String abbrev(String name) {
            return name.length() > 12 ? name.substring(0, 11) + "…" : name;
        }
    }
}

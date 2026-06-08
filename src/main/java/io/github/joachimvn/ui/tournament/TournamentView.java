package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.tournament.TournamentRunner;

import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Full-screen tournament view: live mini boards on the left, standings and recent
 * results on the right. Replaces the old modal overlay.
 *
 * <p>Live boards are refreshed by an {@link AnimationTimer} (up to 60 fps) that reads
 * the runner's {@code ConcurrentHashMap<gameId, GameState>} — no per-move
 * {@code Platform.runLater} spam. Games appear and disappear automatically as they
 * start and finish.
 */
public final class TournamentView {

    // ── Board rendering — mirrors BoardView's design constants exactly ──────
    private static final double DESIGN_CELL = 54;
    private static final double DESIGN_GAP  = 10;
    private static final double DESIGN_STEP = DESIGN_CELL + DESIGN_GAP;
    private static final double DESIGN_SIZE = GameState.BOARD_SIZE * DESIGN_CELL
                                            + (GameState.BOARD_SIZE - 1) * DESIGN_GAP; // 566
    private static final double BOARD_PX    = 283; // exactly 0.5 × 566, clean half-scale
    private static final double GOAL_STRIP_RATIO = 3.0 / 54;  // same as BoardView
    private static final double PAWN_PAD_RATIO   = 0.16;       // same as BoardView
    private static final double STRIP_OPACITY    = 0.70;

    private static final Color P1_COLOR = Color.web("#9E4A40");
    private static final Color P2_COLOR = Color.web("#3E68A8");
    private static final Color BG_COLOR = Color.web("#0F1117");
    private static final Color CELL_CLR = Color.web("#191C2A");
    private static final Color P1_STRIP = P1_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    private static final Color P2_STRIP = P2_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);

    private static final int MAX_RESULTS = 20;

    // ── State ─────────────────────────────────────────────────────────────
    private final StackPane root;
    private final TournamentRunner runner = new TournamentRunner();
    private final List<Difficulty> strategies = Arrays.asList(Difficulty.values());
    private final ObservableList<Difficulty> tableItems;
    private final ObservableList<String> recentResults = FXCollections.observableArrayList();

    private final Label       titleLabel    = new Label("TOURNAMENT");
    private final ProgressBar progressBar   = new ProgressBar(0);
    private final Label       progressLabel = new Label();
    private final Button      pauseBtn      = new Button("Pause");
    private final Button      actionBtn     = new Button("Stop");

    private final TilePane  boardGrid = new TilePane(10, 10);
    private final Map<Integer, MiniBoard> activeBoards = new LinkedHashMap<>();

    private TableView<Difficulty> standingsTable;
    private AnimationTimer animTimer;
    private boolean running = false;

    public TournamentView(Runnable onClose) {
        root = new StackPane();
        root.getStyleClass().add("tournament-view");
        root.setVisible(false);

        tableItems = FXCollections.observableArrayList(strategies);
        standingsTable = buildTable();

        // ── Top bar ───────────────────────────────────────────────────────
        titleLabel.getStyleClass().add("tournament-title");
        progressLabel.getStyleClass().add("tournament-progress-label");
        progressBar.getStyleClass().add("tournament-progress-bar");
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        pauseBtn.getStyleClass().add("new-game-button");
        pauseBtn.setOnAction(e -> togglePause());

        Button copyBtn = new Button("Copy Results");
        copyBtn.getStyleClass().add("new-game-button");
        copyBtn.setOnAction(e -> copyToClipboard());

        actionBtn.getStyleClass().add("start-button");
        actionBtn.setOnAction(e -> {
            if (running) { runner.cancel(); running = false; }
            stopAnimTimer();
            root.setVisible(false);
            onClose.run();
        });

        HBox topBar = new HBox(12, titleLabel, progressBar, progressLabel,
                               pauseBtn, copyBtn, actionBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("tournament-top-bar");
        topBar.setPadding(new Insets(14, 20, 14, 20));
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // ── Live boards section ───────────────────────────────────────────
        boardGrid.getStyleClass().add("tournament-board-grid");
        boardGrid.setPrefTileWidth((int) BOARD_PX + 24);   // canvas + card padding
        boardGrid.setPrefTileHeight((int) BOARD_PX + 46);  // canvas + name row + card padding
        boardGrid.setPadding(new Insets(10));

        Label boardsTitle = new Label("LIVE GAMES");
        boardsTitle.getStyleClass().add("tournament-section-title");

        Label noGamesLabel = new Label("Waiting for games to start…");
        noGamesLabel.getStyleClass().add("tournament-empty-label");

        StackPane boardsContent = new StackPane(boardGrid, noGamesLabel);
        boardGrid.getChildren().addListener(
            (javafx.collections.ListChangeListener<javafx.scene.Node>) c ->
                noGamesLabel.setVisible(boardGrid.getChildren().isEmpty()));

        VBox boardsSection = new VBox(8, boardsTitle, boardsContent);
        boardsSection.getStyleClass().add("tournament-panel");
        boardsSection.setPadding(new Insets(14));
        VBox.setVgrow(boardsContent, Priority.ALWAYS);
        HBox.setHgrow(boardsSection, Priority.ALWAYS);

        // ── Right panel: standings + recent results ───────────────────────
        Label standingsTitle = new Label("STANDINGS");
        standingsTitle.getStyleClass().add("tournament-section-title");

        Label resultsTitle = new Label("RECENT RESULTS");
        resultsTitle.getStyleClass().add("tournament-section-title");

        ListView<String> resultsList = new ListView<>(recentResults);
        resultsList.getStyleClass().add("tournament-results-list");
        resultsList.setPrefHeight(200);
        resultsList.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(resultsList, Priority.ALWAYS);

        VBox rightPanel = new VBox(10, standingsTitle, standingsTable, resultsTitle, resultsList);
        rightPanel.getStyleClass().add("tournament-panel");
        rightPanel.setPadding(new Insets(14));
        rightPanel.setPrefWidth(360);
        rightPanel.setMaxWidth(360);
        VBox.setVgrow(standingsTable, Priority.SOMETIMES);

        // ── Main content ──────────────────────────────────────────────────
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
        paused = false;
        titleLabel.setText("TOURNAMENT");
        actionBtn.setText("Stop");
        pauseBtn.setText("Pause");
        pauseBtn.setDisable(false);
        progressBar.setProgress(0);
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
                resort();
            },
            (winner, loser) -> {
                int[] wr = runner.getResults().get(winner);
                String pct = (wr == null || wr[0] + wr[1] == 0) ? ""
                    : String.format(" (%d%%)", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1])));
                recentResults.add(0, "  " + winner.sample().displayName()
                    + "  beat  " + loser.sample().displayName() + pct);
                if (recentResults.size() > MAX_RESULTS)
                    recentResults.remove(recentResults.size() - 1);
            },
            () -> {
                running = false;
                titleLabel.setText("TOURNAMENT RESULTS");
                actionBtn.setText("Close");
                pauseBtn.setDisable(true);
                progressBar.setProgress(1.0);
                progressLabel.setText(runner.totalGames(strategies) + " games complete");
                resort();
            });
    }

    // ── Pause / resume ────────────────────────────────────────────────────

    private boolean paused = false;

    private void togglePause() {
        paused = !paused;
        if (paused) {
            runner.pause();
            pauseBtn.setText("Resume");
            titleLabel.setText("TOURNAMENT — PAUSED");
        } else {
            runner.resume();
            pauseBtn.setText("Pause");
            titleLabel.setText("TOURNAMENT");
        }
    }

    // ── Animation timer ───────────────────────────────────────────────────

    private void startAnimTimer() {
        animTimer = new AnimationTimer() {
            @Override public void handle(long now) { refreshLiveBoards(); }
        };
        animTimer.start();
    }

    private void stopAnimTimer() {
        if (animTimer != null) { animTimer.stop(); animTimer = null; }
    }

    private void refreshLiveBoards() {
        var liveStates     = runner.getLiveStates();
        var liveMatchups   = runner.getLiveMatchups();
        var liveWallOwners = runner.getLiveWallOwners();
        Set<Integer> current = new HashSet<>(liveStates.keySet());

        // Remove boards for finished games
        activeBoards.entrySet().removeIf(e -> {
            if (!current.contains(e.getKey())) {
                boardGrid.getChildren().remove(e.getValue().card);
                return true;
            }
            return false;
        });

        // Add boards for new games; update existing
        for (int id : current) {
            GameState state    = liveStates.get(id);
            Difficulty[] match = liveMatchups.get(id);
            if (state == null || match == null) continue;

            var wallOwners = liveWallOwners.getOrDefault(id, new java.util.concurrent.ConcurrentHashMap<>());

            MiniBoard mb = activeBoards.computeIfAbsent(id, k -> {
                MiniBoard b = new MiniBoard(match[0], match[1]);
                boardGrid.getChildren().add(b.card);
                return b;
            });
            mb.draw(state, wallOwners);
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
        tv.setPrefHeight(9999);
        tv.getStyleClass().add("tournament-table");
        tv.setFocusTraversable(false);
        tv.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(Difficulty item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("rank-top", "rank-second", "rank-third");
                if (!empty) {
                    int idx = tableItems.indexOf(item);
                    if      (idx == 0) getStyleClass().add("rank-top");
                    else if (idx == 1) getStyleClass().add("rank-second");
                    else if (idx == 2) getStyleClass().add("rank-third");
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
        wCol.setCellValueFactory(cd -> { int[] wr = runner.getResults().get(cd.getValue()); return new SimpleIntegerProperty(wr == null ? 0 : wr[0]); });
        wCol.getStyleClass().add("tournament-col-center");
        wCol.setPrefWidth(44); wCol.setMinWidth(38); wCol.setMaxWidth(56); wCol.setSortable(false);

        TableColumn<Difficulty, Number> lCol = new TableColumn<>("L");
        lCol.setCellValueFactory(cd -> { int[] wr = runner.getResults().get(cd.getValue()); return new SimpleIntegerProperty(wr == null ? 0 : wr[1]); });
        lCol.getStyleClass().add("tournament-col-center");
        lCol.setPrefWidth(44); lCol.setMinWidth(38); lCol.setMaxWidth(56); lCol.setSortable(false);

        TableColumn<Difficulty, String> pctCol = new TableColumn<>("Win%");
        pctCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            if (wr == null || wr[0] + wr[1] == 0) return new SimpleStringProperty("—");
            return new SimpleStringProperty(String.format("%d%%", (int) Math.round(100.0 * wr[0] / (wr[0] + wr[1]))));
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
            sb.append(String.format("%-4d  %-16s  %3d  %3d  %5s%n", i + 1, d.sample().displayName(), w, l, pct));
        }
        sb.append("\n\nHEAD-TO-HEAD BREAKDOWN\n").append("─".repeat(40)).append("\n");
        for (Difficulty d : tableItems) {
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0], l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(d.sample().displayName()).append("  (").append(w).append("W–").append(l).append("L  ").append(pct).append(")\n");
            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                wins.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<Difficulty, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().sample().displayName()))
                    .forEach(e -> {
                        int ww = e.getValue(), ll = 2 - ww;
                        String m = ww == 2 ? "✓✓" : ww == 1 ? "✓✗" : "✗✗";
                        sb.append(String.format("  %s  %-16s  %d–%d%n", m, e.getKey().sample().displayName(), ww, ll));
                    });
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── MiniBoard ─────────────────────────────────────────────────────────

    private static final class MiniBoard {
        final VBox   card;
        final Canvas canvas = new Canvas(BOARD_PX, BOARD_PX);

        MiniBoard(Difficulty d1, Difficulty d2) {
            Label p1 = new Label(abbrev(d1.sample().displayName()));
            p1.getStyleClass().add("mini-p1");
            Label vs = new Label("vs");
            vs.getStyleClass().add("mini-vs");
            Label p2 = new Label(abbrev(d2.sample().displayName()));
            p2.getStyleClass().add("mini-p2");

            HBox names = new HBox(6, p1, vs, p2);
            names.setAlignment(Pos.CENTER);

            card = new VBox(6, names, canvas);
            card.getStyleClass().add("mini-board-card");
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(10, 10, 10, 10));
        }

        /**
         * Renders using the exact same formula as {@link io.github.joachimvn.ui.board.BoardView}
         * scaled to {@value #BOARD_PX}px. Wall colours match the owning player.
         */
        void draw(GameState state, Map<Wall, Player> wallOwners) {
            GraphicsContext g  = canvas.getGraphicsContext2D();
            double scale       = BOARD_PX / DESIGN_SIZE;
            double cell        = DESIGN_CELL * scale;
            double gap         = DESIGN_GAP  * scale;
            double step        = DESIGN_STEP * scale;
            double stripH      = GOAL_STRIP_RATIO * cell;
            int    n           = GameState.BOARD_SIZE;

            // Background
            g.setFill(BG_COLOR);
            g.fillRect(0, 0, BOARD_PX, BOARD_PX);

            // Cells + goal strips (mirrors BoardView.drawCell exactly)
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    double x = c * step;
                    double y = r * step;
                    g.setFill(CELL_CLR);
                    g.fillRect(x, y, cell, cell);
                    if (r == Player.ONE.goalRow()) {
                        g.setFill(P1_STRIP);
                        g.fillRect(x, y, cell, stripH);
                    } else if (r == Player.TWO.goalRow()) {
                        g.setFill(P2_STRIP);
                        g.fillRect(x, y + cell - stripH, cell, stripH);
                    }
                }
            }

            // Walls — same formula as BoardView.paintWall, coloured by owner
            for (Wall w : state.getWalls()) {
                Player owner = wallOwners.get(w);
                Color  color = owner == Player.TWO ? P2_COLOR : P1_COLOR;
                g.setFill(color);
                double wx = w.col() * step;
                double wy = w.row() * step;
                double len = 2 * cell + gap;
                if (w.orientation() == Wall.Orientation.HORIZONTAL)
                    g.fillRect(wx, wy + cell, len, gap);
                else
                    g.fillRect(wx + cell, wy, gap, len);
            }

            // Pawns — same formula as BoardView.paintPawn
            double pad = cell * PAWN_PAD_RATIO;
            Position pp1 = state.getPawnPosition(Player.ONE);
            Position pp2 = state.getPawnPosition(Player.TWO);
            paintPawn(g, pp1.col(), pp1.row(), P1_COLOR, step, cell, pad);
            paintPawn(g, pp2.col(), pp2.row(), P2_COLOR, step, cell, pad);
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

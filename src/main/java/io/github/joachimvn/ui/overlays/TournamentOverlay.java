package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.tournament.TournamentRunner;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Full-screen overlay that runs and displays a round-robin AI tournament.
 *
 * <p>All strategies from {@link Difficulty} compete: every pair plays twice (swapping
 * P1/P2), and the live standings table updates as results arrive. When the tournament
 * finishes the Stop button becomes Close.
 */
public final class TournamentOverlay {

    private final StackPane root;
    private final TournamentRunner runner = new TournamentRunner();
    private final List<Difficulty> strategies = Arrays.asList(Difficulty.values());
    private final ObservableList<Difficulty> tableItems;

    private final Label       titleLabel    = new Label("TOURNAMENT");
    private final ProgressBar progressBar   = new ProgressBar(0);
    private final Label       progressLabel = new Label();
    private final TableView<Difficulty> table;
    private final Button      actionBtn     = new Button("Stop");
    private boolean running = false;

    public TournamentOverlay(Runnable onClose) {
        root = new StackPane();
        root.getStyleClass().add("setup-overlay");
        root.setVisible(false);

        titleLabel.getStyleClass().add("tournament-title");
        progressLabel.getStyleClass().add("tournament-progress-label");

        tableItems = FXCollections.observableArrayList(strategies);
        table = buildTable();

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("tournament-progress-bar");
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        HBox progressRow = new HBox(14, progressBar, progressLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        actionBtn.getStyleClass().add("start-button");
        actionBtn.setOnAction(e -> {
            if (running) {
                runner.cancel();
                running = false;
            }
            root.setVisible(false);
            onClose.run();
        });

        Button copyBtn = new Button("Copy Results");
        copyBtn.getStyleClass().add("new-game-button");
        copyBtn.setOnAction(e -> copyResultsToClipboard());

        HBox bottomRow = new HBox(10, copyBtn, actionBtn);
        bottomRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(actionBtn, Priority.ALWAYS);
        actionBtn.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(22, titleLabel, progressRow, table, bottomRow);
        card.getStyleClass().addAll("setup-card", "tournament-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(760);
        card.setFillWidth(true);

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

    public void start() {
        running = true;
        titleLabel.setText("TOURNAMENT");
        actionBtn.setText("Stop");
        progressBar.setProgress(0);
        int total = runner.totalGames(strategies);
        progressLabel.setText("0 / " + total);
        tableItems.setAll(strategies);
        table.refresh();
        root.setVisible(true);

        runner.start(strategies, (done, t) -> {
            progressBar.setProgress((double) done / t);
            progressLabel.setText(done + " / " + t);
            resort();
        }, () -> {
            running = false;
            titleLabel.setText("TOURNAMENT RESULTS");
            actionBtn.setText("Close");
            progressBar.setProgress(1.0);
            progressLabel.setText(total + " games complete");
            resort();
        });
    }

    private void resort() {
        Map<Difficulty, int[]> res = runner.getResults();
        tableItems.sort(Comparator
            .comparingDouble((Difficulty d) -> {
                int[] wr = res.get(d);
                if (wr == null || wr[0] + wr[1] == 0) return 0.0;
                return -(double) wr[0] / (wr[0] + wr[1]);
            })
            .thenComparingInt(d -> {
                int[] wr = res.get(d);
                return wr == null ? 0 : -wr[0];
            }));
        table.refresh();
    }

    @SuppressWarnings("unchecked")
    private TableView<Difficulty> buildTable() {
        TableView<Difficulty> tv = new TableView<>(tableItems);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPrefHeight(500);
        tv.setMaxHeight(500);
        tv.getStyleClass().add("tournament-table");
        tv.setFocusTraversable(false);

        tv.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Difficulty item, boolean empty) {
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

        // Rank
        TableColumn<Difficulty, Number> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(cd ->
            new SimpleIntegerProperty(tableItems.indexOf(cd.getValue()) + 1));
        rankCol.getStyleClass().add("tournament-col-center");
        rankCol.setPrefWidth(42);
        rankCol.setMinWidth(36);
        rankCol.setMaxWidth(52);
        rankCol.setSortable(false);

        // Name
        TableColumn<Difficulty, String> nameCol = new TableColumn<>("Strategy");
        nameCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().sample().displayName()));
        nameCol.setSortable(false);

        // Wins
        TableColumn<Difficulty, Number> wCol = new TableColumn<>("W");
        wCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            return new SimpleIntegerProperty(wr == null ? 0 : wr[0]);
        });
        wCol.getStyleClass().add("tournament-col-center");
        wCol.setPrefWidth(56);
        wCol.setMinWidth(44);
        wCol.setMaxWidth(72);
        wCol.setSortable(false);

        // Losses
        TableColumn<Difficulty, Number> lCol = new TableColumn<>("L");
        lCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            return new SimpleIntegerProperty(wr == null ? 0 : wr[1]);
        });
        lCol.getStyleClass().add("tournament-col-center");
        lCol.setPrefWidth(56);
        lCol.setMinWidth(44);
        lCol.setMaxWidth(72);
        lCol.setSortable(false);

        // Win %
        TableColumn<Difficulty, String> pctCol = new TableColumn<>("Win %");
        pctCol.setCellValueFactory(cd -> {
            int[] wr = runner.getResults().get(cd.getValue());
            if (wr == null || wr[0] + wr[1] == 0) return new SimpleStringProperty("—");
            return new SimpleStringProperty(
                String.format("%.0f%%", 100.0 * wr[0] / (wr[0] + wr[1])));
        });
        pctCol.getStyleClass().add("tournament-col-center");
        pctCol.setPrefWidth(70);
        pctCol.setMinWidth(56);
        pctCol.setMaxWidth(90);
        pctCol.setSortable(false);

        tv.getColumns().addAll(rankCol, nameCol, wCol, lCol, pctCol);
        return tv;
    }

    private void copyResultsToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(formatResults());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String formatResults() {
        int total = runner.totalGames(strategies);
        Map<Difficulty, int[]> res = runner.getResults();
        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        StringBuilder sb = new StringBuilder();

        sb.append("CHORIDOR TOURNAMENT RESULTS\n");
        sb.append(strategies.size()).append(" strategies · ").append(total).append(" games\n\n");

        // Standings
        sb.append(String.format("%-4s  %-16s  %3s  %3s  %5s%n", "#", "Strategy", "W", "L", "Win%"));
        sb.append("─".repeat(40)).append("\n");
        for (int i = 0; i < tableItems.size(); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0];
            int l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(String.format("%-4d  %-16s  %3d  %3d  %5s%n",
                i + 1, d.sample().displayName(), w, l, pct));
        }

        // Head-to-head breakdown per strategy
        sb.append("\n\nHEAD-TO-HEAD BREAKDOWN\n");
        sb.append("─".repeat(40)).append("\n");
        for (Difficulty d : tableItems) {
            int[] wr = res.get(d);
            int w = wr == null ? 0 : wr[0];
            int l = wr == null ? 0 : wr[1];
            String pct = (w + l == 0) ? "—" : String.format("%.0f%%", 100.0 * w / (w + l));
            sb.append(d.sample().displayName())
              .append("  (").append(w).append("W–").append(l).append("L  ").append(pct).append(")\n");

            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                // Sort opponents: most wins first, then alphabetically
                List<Map.Entry<Difficulty, Integer>> entries = new ArrayList<>(wins.entrySet());
                entries.sort(Comparator
                    .<Map.Entry<Difficulty, Integer>>comparingInt(Map.Entry::getValue).reversed()
                    .thenComparing(e -> e.getKey().sample().displayName()));
                for (Map.Entry<Difficulty, Integer> e : entries) {
                    int ww = e.getValue();
                    int ll = 2 - ww;
                    String marker = ww == 2 ? "✓✓" : ww == 1 ? "✓✗" : "✗✗";
                    sb.append(String.format("  %s  %-16s  %d–%d%n",
                        marker, e.getKey().sample().displayName(), ww, ll));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

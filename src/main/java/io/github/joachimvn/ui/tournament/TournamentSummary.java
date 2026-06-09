package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.tournament.GameRecord;
import io.github.joachimvn.tournament.TournamentRunner;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Builds and populates the post-tournament summary panel. */
final class TournamentSummary {

    private static final double SUMMARY_BOARD_PX = 260;

    // Shared accent colours used in both notable-game cards and stat chips
    private static final String COLOR_GOLD = "#B8960C";
    private static final String COLOR_BLUE = "#3E68A8";
    private static final String COLOR_RED  = "#9E4A40";
    private static final String FX_FILL    = "-fx-text-fill: ";

    private TournamentSummary() {}

    /**
     * Clears and repopulates {@code summaryBox} with notable games, podium,
     * key stats, and the head-to-head accordion.
     *
     * @param tableItems sorted strategy list (highest rank first)
     * @param strategies full strategy list (for game count)
     */
    static void populate(VBox summaryBox, TournamentRunner runner,
                         List<Difficulty> tableItems, List<Difficulty> strategies,
                         long durationMs) {
        summaryBox.getChildren().clear();

        HBox notableRow = buildNotableRow(runner);
        if (notableRow != null) {
            summaryBox.getChildren().add(notableRow);
        }

        summaryBox.getChildren().add(separator());
        summaryBox.getChildren().add(buildStatsBox(runner, tableItems, strategies, durationMs));
        summaryBox.getChildren().add(separator());

        Label h2hTitle = new Label("HEAD-TO-HEAD BREAKDOWN");
        h2hTitle.getStyleClass().add("tournament-section-title");
        summaryBox.getChildren().add(h2hTitle);

        Map<Difficulty, int[]> res = runner.getResults();
        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        for (Difficulty d : tableItems) {
            summaryBox.getChildren().add(buildH2hPane(d, mw, tableItems, res));
        }
    }

    // ── Section builders ──────────────────────────────────────────────────────

    /** Returns the notable-games row, or {@code null} if no games were recorded. */
    private static HBox buildNotableRow(TournamentRunner runner) {
        GameRecord best     = runner.getBestGame();
        GameRecord shortest = runner.getShortestGame();
        GameRecord longest  = runner.getLongestGame();
        if (best == null && shortest == null && longest == null) return null;

        HBox row = new HBox(12);
        row.setAlignment(Pos.TOP_LEFT);
        if (best     != null) addNotableCard(row, best,     "BEST GAME",
                best.moveCount() + " moves · " + best.wallCount() + " walls · " + best.loserFinalDist() + "-step finish", COLOR_GOLD);
        if (shortest != null) addNotableCard(row, shortest, "SHORTEST GAME",
                shortest.moveCount() + " moves", COLOR_BLUE);
        if (longest  != null) addNotableCard(row, longest,  "LONGEST GAME",
                longest.moveCount()  + " moves", COLOR_RED);

        row.widthProperty().addListener((obs, oldW, newW) -> {
            int n = row.getChildren().size();
            if (n == 0 || newW.doubleValue() < 1) return;
            double boardSize = (newW.doubleValue() - 12.0 * (n - 1)) / n - 24;
            if (boardSize < 1) return;
            row.setMinHeight(boardSize + 92);
            row.setPrefHeight(boardSize + 92);
        });
        return row;
    }

    private static VBox buildStatsBox(TournamentRunner runner, List<Difficulty> tableItems,
                                       List<Difficulty> strategies, long durationMs) {
        Map<Difficulty, int[]> res = runner.getResults();
        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        VBox box = new VBox(6);
        box.getChildren().add(buildPodiumRow(tableItems, res));

        HBox finisher = buildFinisherChip(runner, tableItems);
        if (finisher != null) box.getChildren().add(finisher);

        HBox cycleStat = buildCycleStatChip(mw, tableItems);
        if (cycleStat != null) box.getChildren().add(cycleStat);

        box.getChildren().add(statChip("Duration",
                formatDuration(durationMs) + "  ·  " + strategies.size()
                + " strategies  ·  " + runner.totalGames(strategies) + " games",
                "#1A1A2A", "#8890A8"));
        return box;
    }

    private static HBox buildPodiumRow(List<Difficulty> tableItems, Map<Difficulty, int[]> res) {
        String[] rankColors = {COLOR_GOLD, "#8896A0", "#8B6040"};
        String[] rankNames  = {"1st", "2nd", "3rd"};
        HBox podiumRow = new HBox(20);
        for (int i = 0; i < Math.min(3, tableItems.size()); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.getOrDefault(d, new int[]{0, 0});
            int w = wr[0];
            int l = wr[1];
            String pct = (w + l == 0) ? "—" : (int) Math.round(100.0 * w / (w + l)) + "%";
            VBox card = new VBox(3);
            card.setPadding(new Insets(8, 14, 8, 14));
            card.setStyle("-fx-background-color: #13151F; -fx-border-color: #1E2130; "
                    + "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
            Label rank = new Label(rankNames[i]);
            rank.setStyle(FX_FILL + rankColors[i] + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label name = new Label(d.sample().displayName());
            name.setStyle(FX_FILL + rankColors[i] + "; -fx-font-size: 15px; -fx-font-weight: bold;");
            Label wLbl = new Label(w + "W");
            wLbl.setStyle(FX_FILL + "#2e8f4b; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label lLbl = new Label(l + "L");
            lLbl.setStyle(FX_FILL + "#8f2e2e; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label pctLbl = new Label(pct);
            pctLbl.setStyle(FX_FILL + "#606880; -fx-font-size: 12px;");
            HBox recordRow = new HBox(6, wLbl, lLbl, pctLbl);
            recordRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(rank, name, recordRow);
            podiumRow.getChildren().add(card);
        }
        return podiumRow;
    }

    /** Fastest or slowest average finisher; picks whichever deviates more from the mean. */
    private static HBox buildFinisherChip(TournamentRunner runner, List<Difficulty> tableItems) {
        var moveTotals = runner.getStrategyMoveTotals();
        Difficulty fastest = null;
        Difficulty slowest = null;
        double fastestAverage = Double.MAX_VALUE;
        double slowestAverage = 0;
        double totalMoveSum = 0;
        long totalMoveCount = 0;
        for (Difficulty d : tableItems) {
            long[] mt = moveTotals.get(d);
            if (mt == null || mt[1] == 0) continue;
            double average = (double) mt[0] / mt[1];
            if (average < fastestAverage) { fastestAverage = average; fastest = d; }
            if (average > slowestAverage) { slowestAverage = average; slowest = d; }
            totalMoveSum += mt[0];
            totalMoveCount += mt[1];
        }
        if (fastest == null || slowest == null || totalMoveCount == 0) return null;

        double mean = totalMoveSum / totalMoveCount;
        if ((mean - fastestAverage) >= (slowestAverage - mean)) {
            return statChip("Fastest finisher",
                    fastest.sample().displayName() + " average " + (int) Math.round(fastestAverage) + " moves per game",
                    "#1A2A1A", "#5ABF78");
        }
        return statChip("Slowest finisher",
                slowest.sample().displayName() + " average " + (int) Math.round(slowestAverage) + " moves per game",
                "#2A1A1A", "#C8706A");
    }

    /** Biggest upset → nemesis of #1 → most dominant — picks whichever is most interesting. */
    private static HBox buildCycleStatChip(Map<Difficulty, Map<Difficulty, Integer>> mw,
                                            List<Difficulty> tableItems) {
        HBox chip = buildUpsetChip(mw, tableItems);
        if (chip != null) return chip;
        chip = buildNemesisChip(mw, tableItems);
        if (chip != null) return chip;
        return buildDominantChip(mw, tableItems);
    }

    private static HBox buildUpsetChip(Map<Difficulty, Map<Difficulty, Integer>> mw,
                                        List<Difficulty> tableItems) {
        int n = tableItems.size();
        int biggestGap = 0;
        Difficulty upsetter = null;
        Difficulty victim = null;
        for (int lo = 1; lo < n; lo++) {
            Map<Difficulty, Integer> loWins = mw.get(tableItems.get(lo));
            if (loWins == null) continue;
            for (int hi = 0; hi < lo; hi++) {
                if (loWins.getOrDefault(tableItems.get(hi), 0) >= 2 && lo - hi > biggestGap) {
                    biggestGap = lo - hi;
                    upsetter = tableItems.get(lo);
                    victim = tableItems.get(hi);
                }
            }
        }
        if (upsetter == null || biggestGap < Math.max(2, n / 3)) return null;
        return statChip("Biggest upset",
                upsetter.sample().displayName() + " (#" + (tableItems.indexOf(upsetter) + 1) + ")"
                + " swept " + victim.sample().displayName() + " (#" + (tableItems.indexOf(victim) + 1) + ")",
                "#2A1A2A", "#9B59B6");
    }

    private static HBox buildNemesisChip(Map<Difficulty, Map<Difficulty, Integer>> mw,
                                          List<Difficulty> tableItems) {
        Difficulty top = tableItems.get(0);
        Difficulty nemesis = null;
        int nemesisWins = 0;
        for (Difficulty d : tableItems) {
            if (d == top) continue;
            int w = mw.getOrDefault(d, Map.of()).getOrDefault(top, 0);
            if (w > nemesisWins) { nemesisWins = w; nemesis = d; }
        }
        if (nemesis == null || nemesisWins < 2) return null;
        return statChip("Nemesis of #1",
                nemesis.sample().displayName() + " went " + nemesisWins + "-" + (2 - nemesisWins)
                + " vs " + top.sample().displayName(),
                "#2A1A1A", "#E67E22");
    }

    private static HBox buildDominantChip(Map<Difficulty, Map<Difficulty, Integer>> mw,
                                           List<Difficulty> tableItems) {
        Difficulty sweeper = null;
        int maxSweeps = 0;
        for (Difficulty d : tableItems) {
            Map<Difficulty, Integer> dWins = mw.get(d);
            if (dWins == null) continue;
            int sweeps = (int) dWins.values().stream().filter(v -> v >= 2).count();
            if (sweeps > maxSweeps) { maxSweeps = sweeps; sweeper = d; }
        }
        if (sweeper == null || maxSweeps < 2) return null;
        return statChip("Most dominant",
                sweeper.sample().displayName() + " swept " + maxSweeps + " of " + (tableItems.size() - 1) + " opponents",
                "#1A1A2A", COLOR_BLUE);
    }

    private static TitledPane buildH2hPane(Difficulty d, Map<Difficulty, Map<Difficulty, Integer>> mw,
                                            List<Difficulty> tableItems, Map<Difficulty, int[]> res) {
        int[] wr = res.getOrDefault(d, new int[]{0, 0});
        int w = wr[0];
        int l = wr[1];
        String pct = (w + l == 0) ? "—" : (int) Math.round(100.0 * w / (w + l)) + "%";

        VBox content = new VBox(4);
        content.setPadding(new Insets(6, 10, 6, 10));
        content.setStyle("-fx-background-color: #0C0E14;");
        Map<Difficulty, Integer> wins = mw.get(d);
        if (wins != null) {
            List<Difficulty> opponents = new ArrayList<>(tableItems);
            opponents.remove(d);
            for (Difficulty opp : opponents) {
                content.getChildren().add(h2hRow(opp, wins.getOrDefault(opp, 0)));
            }
        }

        TitledPane pane = new TitledPane();
        pane.setExpanded(false);
        pane.setAnimated(true);
        pane.setContent(content);
        pane.setStyle("-fx-background-color: #13151F; -fx-border-color: #1E2130;");
        Label headerLabel = new Label(d.sample().displayName() + "     " + w + "W  " + l + "L  ·  " + pct);
        headerLabel.setStyle(FX_FILL + "#8890A8; -fx-font-size: 13px; -fx-font-weight: bold;");
        pane.setGraphic(headerLabel);
        pane.setText("");
        return pane;
    }

    // ── Row / chip helpers ────────────────────────────────────────────────────

    private static void addNotableCard(HBox row, GameRecord rec, String title,
                                        String stat, String accentColor) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle(FX_FILL + accentColor + "; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label d1Lbl = new Label(rec.d1().sample().displayName());
        d1Lbl.setStyle(FX_FILL + COLOR_RED + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        Label vsLbl = new Label("vs");
        vsLbl.setStyle(FX_FILL + "#3A3F58; -fx-font-size: 11px;");
        Label d2Lbl = new Label(rec.d2().sample().displayName());
        d2Lbl.setStyle(FX_FILL + COLOR_BLUE + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        HBox matchupRow = new HBox(6, d1Lbl, vsLbl, d2Lbl);
        matchupRow.setAlignment(Pos.CENTER_LEFT);

        String winnerColor = rec.winner() == rec.d1() ? COLOR_RED : COLOR_BLUE;
        Label winnerLbl = new Label(rec.winner() != null ? rec.winner().sample().displayName() : "—");
        winnerLbl.setStyle(FX_FILL + winnerColor + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label statLbl = new Label(stat);
        statLbl.setStyle(FX_FILL + "#3A3F58; -fx-font-size: 11px;");
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

    private static HBox h2hRow(Difficulty opp, int wins) {
        Label oppLabel = new Label(opp.sample().displayName());
        oppLabel.setStyle(FX_FILL + "#606880; -fx-font-size: 12px;");
        oppLabel.setMinWidth(120);

        String chipBg;
        String chipFg;
        String chipText;
        switch (wins) {
            case 2  -> { chipBg = "#1A3A1A"; chipFg = "#4CAF50"; chipText = "✓✓  2–0"; }
            case 1  -> { chipBg = "#2A2A1A"; chipFg = COLOR_GOLD; chipText = "✓✗  1–1"; }
            default -> { chipBg = "#3A1A1A"; chipFg = "#C8706A"; chipText = "✗✗  0–2"; }
        }

        Label chip = new Label(chipText);
        chip.setStyle("-fx-background-color: " + chipBg + "; " + FX_FILL + chipFg
                + "; -fx-font-size: 11px; -fx-font-weight: bold;"
                + " -fx-padding: 2 8 2 8; -fx-background-radius: 3;");

        HBox row = new HBox(10, oppLabel, chip);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));
        row.setStyle("-fx-border-color: transparent transparent #191C2A transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    private static HBox statChip(String label, String value, String bg, String fg) {
        Label key = new Label(label.toUpperCase());
        key.setStyle(FX_FILL + fg + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-min-width: 86px;");
        Label val = new Label(value);
        val.setStyle(FX_FILL + "#7880A0; -fx-font-size: 12px;");
        HBox row = new HBox(14, key, val);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 12, 5, 14));
        row.setStyle("-fx-background-color: " + bg + "44;"
                + " -fx-border-color: " + fg + " transparent transparent transparent;"
                + " -fx-border-width: 0 0 0 3;");
        return row;
    }

    private static Separator separator() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #1E2130;");
        return s;
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    // ── BoardPane ─────────────────────────────────────────────────────────────

    /**
     * A Region wrapping a Canvas for square board rendering inside summary cards.
     * minWidth=0 ensures the canvas never pushes the layout wider.
     * computePrefHeight(w)=w so VBox always allocates a square slot.
     */
    static final class BoardPane extends Region {
        private final Canvas canvas = new Canvas(1, 1);
        private final GameState state;
        private final java.util.Map<Wall, Player> wallOwners;

        BoardPane(GameState state, java.util.Map<Wall, Player> wallOwners) {
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
            BoardRenderer.draw(canvas, state, wallOwners);
            setClip(new javafx.scene.shape.Rectangle(w, w));
        }

        @Override protected double computeMinWidth(double h)   { return 0; }
        @Override protected double computeMinHeight(double w)  { return 0; }
        @Override protected double computePrefWidth(double h)  { return SUMMARY_BOARD_PX; }
        @Override protected double computePrefHeight(double w) { return w > 0 ? w : SUMMARY_BOARD_PX; }
        @Override protected double computeMaxWidth(double h)   { return Double.MAX_VALUE; }
        @Override protected double computeMaxHeight(double w)  { return Double.MAX_VALUE; }
    }
}

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
import javafx.scene.paint.Color;

import java.util.*;

/** Builds and populates the post-tournament summary panel. */
final class TournamentSummary {

    private static final double SUMMARY_BOARD_PX = 260;

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
        Map<Difficulty, int[]> res = runner.getResults();

        // ── Notable Games row ─────────────────────────────────────────────────
        GameRecord best     = runner.getBestGame();
        GameRecord shortest = runner.getShortestGame();
        GameRecord longest  = runner.getLongestGame();

        if (best != null || shortest != null || longest != null) {
            HBox notableRow = new HBox(12);
            notableRow.setAlignment(Pos.TOP_LEFT);
            if (best     != null) addNotableCard(notableRow, best,     "BEST GAME",
                    best.moveCount() + " moves · " + best.wallCount() + " walls · " + best.loserFinalDist() + "-step finish", "#B8960C");
            if (shortest != null) addNotableCard(notableRow, shortest, "SHORTEST GAME",
                    shortest.moveCount() + " moves",  "#3E68A8");
            if (longest  != null) addNotableCard(notableRow, longest,  "LONGEST GAME",
                    longest.moveCount()  + " moves",  "#9E4A40");

            notableRow.widthProperty().addListener((obs, oldW, newW) -> {
                int n = notableRow.getChildren().size();
                if (n == 0 || newW.doubleValue() < 1) return;
                double boardSize = (newW.doubleValue() - 12.0 * (n - 1)) / n - 24;
                if (boardSize < 1) return;
                double totalH = boardSize + 92;
                notableRow.setMinHeight(totalH);
                notableRow.setPrefHeight(totalH);
            });

            summaryBox.getChildren().add(notableRow);
        }

        summaryBox.getChildren().add(separator());

        // ── Podium + key stats ────────────────────────────────────────────────
        VBox statsBox = new VBox(6);

        String[] rankColors = {"#B8960C", "#8896A0", "#8B6040"};
        String[] rankNames  = {"1st", "2nd", "3rd"};
        HBox podiumRow = new HBox(20);
        for (int i = 0; i < Math.min(3, tableItems.size()); i++) {
            Difficulty d = tableItems.get(i);
            int[] wr = res.getOrDefault(d, new int[]{0, 0});
            int w = wr[0], l = wr[1];
            String pct = (w + l == 0) ? "—" : (int) Math.round(100.0 * w / (w + l)) + "%";
            VBox card = new VBox(3);
            card.setPadding(new Insets(8, 14, 8, 14));
            card.setStyle("-fx-background-color: #13151F; -fx-border-color: #1E2130; "
                    + "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
            Label rank = new Label(rankNames[i]);
            rank.setStyle("-fx-text-fill: " + rankColors[i] + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label name = new Label(d.sample().displayName());
            name.setStyle("-fx-text-fill: " + rankColors[i] + "; -fx-font-size: 15px; -fx-font-weight: bold;");
            Label wLbl = new Label(w + "W");
            wLbl.setStyle("-fx-text-fill: #2e8f4b; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label lLbl = new Label(l + "L");
            lLbl.setStyle("-fx-text-fill: #8f2e2e; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label pctLbl = new Label(pct);
            pctLbl.setStyle("-fx-text-fill: #606880; -fx-font-size: 12px;");
            HBox recordRow = new HBox(6, wLbl, lLbl, pctLbl);
            recordRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(rank, name, recordRow);
            podiumRow.getChildren().add(card);
        }
        statsBox.getChildren().add(podiumRow);

        // ── Fastest / Slowest finisher ────────────────────────────────────────
        var moveTotals = runner.getStrategyMoveTotals();
        Difficulty fastest = null, slowest = null;
        double fastestAvg = Double.MAX_VALUE, slowestAvg = 0;
        double totalMoveSum = 0; int totalMoveCount = 0;
        for (Difficulty d : tableItems) {
            long[] mt = moveTotals.get(d);
            if (mt == null || mt[1] == 0) continue;
            double avg = (double) mt[0] / mt[1];
            if (avg < fastestAvg) { fastestAvg = avg; fastest = d; }
            if (avg > slowestAvg) { slowestAvg = avg; slowest = d; }
            totalMoveSum += mt[0]; totalMoveCount += mt[1];
        }
        if (fastest != null && slowest != null && totalMoveCount > 0) {
            double mean = totalMoveSum / totalMoveCount;
            boolean showFastest = (mean - fastestAvg) >= (slowestAvg - mean);
            if (showFastest)
                statsBox.getChildren().add(statChip("Fastest finisher",
                        fastest.sample().displayName() + "  avg " + (int) Math.round(fastestAvg) + " moves per game",
                        "#1A2A1A", "#5ABF78"));
            else
                statsBox.getChildren().add(statChip("Slowest finisher",
                        slowest.sample().displayName() + "  avg " + (int) Math.round(slowestAvg) + " moves per game",
                        "#2A1A1A", "#C8706A"));
        }

        // ── Cycle: biggest upset → nemesis of #1 → most dominant ─────────────
        Map<Difficulty, Map<Difficulty, Integer>> mw = runner.getMatchupWins();
        int n = tableItems.size();

        // Biggest upset: lower-ranked strategy 2-0 swept a higher-ranked one
        int biggestGap = 0; Difficulty upsetter = null, victim = null;
        for (int lo = 1; lo < n; lo++) {
            Difficulty loD = tableItems.get(lo);
            Map<Difficulty, Integer> loWins = mw.get(loD);
            if (loWins == null) continue;
            for (int hi = 0; hi < lo; hi++) {
                Difficulty hiD = tableItems.get(hi);
                if (loWins.getOrDefault(hiD, 0) >= 2 && lo - hi > biggestGap) {
                    biggestGap = lo - hi; upsetter = loD; victim = hiD;
                }
            }
        }

        // Nemesis: who took the most wins off the #1 strategy
        Difficulty top = tableItems.get(0);
        Difficulty nemesis = null; int nemesisWins = 0;
        for (Difficulty d : tableItems) {
            if (d == top) continue;
            int w = mw.getOrDefault(d, Map.of()).getOrDefault(top, 0);
            if (w > nemesisWins) { nemesisWins = w; nemesis = d; }
        }

        // Sweep count: who 2-0'd the most opponents
        Difficulty sweeper = null; int maxSweeps = 0;
        for (Difficulty d : tableItems) {
            Map<Difficulty, Integer> dWins = mw.get(d);
            if (dWins == null) continue;
            int sweeps = (int) dWins.values().stream().filter(v -> v >= 2).count();
            if (sweeps > maxSweeps) { maxSweeps = sweeps; sweeper = d; }
        }

        int upsettRankThreshold = Math.max(2, n / 3);
        if (upsetter != null && biggestGap >= upsettRankThreshold) {
            statsBox.getChildren().add(statChip("Biggest upset",
                    upsetter.sample().displayName() + " (#" + (tableItems.indexOf(upsetter)+1) + ")"
                    + " swept " + victim.sample().displayName() + " (#" + (tableItems.indexOf(victim)+1) + ")",
                    "#2A1A2A", "#9B59B6"));
        } else if (nemesis != null && nemesisWins >= 2) {
            statsBox.getChildren().add(statChip("Nemesis of #1",
                    nemesis.sample().displayName() + " went " + nemesisWins + "-" + (2 - nemesisWins)
                    + " vs " + top.sample().displayName(),
                    "#2A1A1A", "#E67E22"));
        } else if (sweeper != null && maxSweeps >= 2) {
            statsBox.getChildren().add(statChip("Most dominant",
                    sweeper.sample().displayName() + " swept " + maxSweeps + " of " + (n - 1) + " opponents",
                    "#1A1A2A", "#3E68A8"));
        }

        statsBox.getChildren().add(statChip("Duration",
                formatDuration(durationMs) + "  ·  " + strategies.size()
                + " strategies  ·  " + runner.totalGames(strategies) + " games",
                "#1A1A2A", "#8890A8"));

        summaryBox.getChildren().add(statsBox);
        summaryBox.getChildren().add(separator());

        // ── Head-to-head accordion ────────────────────────────────────────────
        Label h2hTitle = new Label("HEAD-TO-HEAD BREAKDOWN");
        h2hTitle.getStyleClass().add("tournament-section-title");
        summaryBox.getChildren().add(h2hTitle);

        for (Difficulty d : tableItems) {
            int[] wr = res.getOrDefault(d, new int[]{0, 0});
            int w = wr[0], l = wr[1];
            String pct = (w + l == 0) ? "—" : (int) Math.round(100.0 * w / (w + l)) + "%";

            VBox content = new VBox(4);
            content.setPadding(new Insets(6, 10, 6, 10));
            content.setStyle("-fx-background-color: #0C0E14;");

            Map<Difficulty, Integer> wins = mw.get(d);
            if (wins != null) {
                List<Difficulty> opponents = new ArrayList<>(tableItems);
                opponents.remove(d);
                for (Difficulty opp : opponents) {
                    int oppWins = wins.getOrDefault(opp, 0);
                    content.getChildren().add(h2hRow(opp, oppWins));
                }
            }

            TitledPane pane = new TitledPane();
            pane.setExpanded(false);
            pane.setAnimated(true);
            pane.setContent(content);
            pane.setStyle("-fx-background-color: #13151F; -fx-border-color: #1E2130;");
            Label headerLabel = new Label(d.sample().displayName()
                    + "     " + w + "W  " + l + "L  ·  " + pct);
            headerLabel.setStyle("-fx-text-fill: #8890A8; -fx-font-size: 13px; -fx-font-weight: bold;");
            pane.setGraphic(headerLabel);
            pane.setText("");
            summaryBox.getChildren().add(pane);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void addNotableCard(HBox row, GameRecord rec, String title,
                                        String stat, String accentColor) {
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

    private static HBox h2hRow(Difficulty opp, int wins) {
        Label oppLabel = new Label(opp.sample().displayName());
        oppLabel.setStyle("-fx-text-fill: #606880; -fx-font-size: 12px;");
        oppLabel.setMinWidth(120);

        String chipBg, chipFg, chipText;
        if (wins == 2)      { chipBg = "#1A3A1A"; chipFg = "#4CAF50"; chipText = "✓✓  2–0"; }
        else if (wins == 1) { chipBg = "#2A2A1A"; chipFg = "#B8960C"; chipText = "✓✗  1–1"; }
        else                { chipBg = "#3A1A1A"; chipFg = "#C8706A"; chipText = "✗✗  0–2"; }

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

    private static HBox statChip(String label, String value, String bg, String fg) {
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

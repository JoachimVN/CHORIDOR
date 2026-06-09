package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Depth-8 alpha-beta with impact-sorted wall ordering and phase-aware path-width evaluation.
 *
 * <p><b>Move ordering:</b> wall candidates are scored by {@code oppDistIncrease + myDistDecrease}
 * before the search begins at each node. Presenting the best-impact walls first dramatically
 * improves alpha-beta cutoffs compared to an unordered wall list, effectively searching deeper
 * within the same time budget.
 *
 * <p><b>Evaluation:</b> combines BFS distance differential with path-DAG width and a
 * phase-aware taper. In the endgame (≤ 4 total walls remaining) path breadth matters little
 * — only the race counts — so the width term fades out to avoid misleading the search.
 *
 * <p><b>Note:</b> this is a completely independent implementation; it does not modify
 * or extend {@link MinimaxStrategy}.
 */
public class StrategistStrategy extends AbstractPrunedSearchStrategy {

    public StrategistStrategy(Player aiPlayer) { super(aiPlayer); }

    @Override public String displayName() { return "Strategist"; }
    @Override public String description() {
        return "Impact-sorted alpha-beta with phase-aware path-width evaluation";
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    @Override
    protected int score(int myDist, int oppDist) {
        return (oppDist - myDist) * 10;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        int myWalls    = state.getWallCount(aiPlayer());
        int oppWalls   = state.getWallCount(aiPlayer().opponent());
        int totalWalls = myWalls + oppWalls;

        // Endgame: path breadth is irrelevant when walls are almost gone
        if (totalWalls <= 4) {
            return (oppDist - myDist) * 12 + wallAdvantage(state);
        }

        int myWidth  = PathWidth.dagWidth(state, aiPlayer());
        int oppWidth = PathWidth.dagWidth(state, aiPlayer().opponent());

        // Taper path-width weight as walls disappear (max weight 3, zero at ≤ 4 walls)
        int pathWt = Math.max(0, 3 * (totalWalls - 4) / 16);

        return (oppDist - myDist) * 10
             + (myWidth - oppWidth) * pathWt
             + wallAdvantage(state) * 2;
    }

    // ── Impact-sorted move ordering ───────────────────────────────────────────

    @Override
    protected List<Move> candidates(GameState state) {
        List<Move> base = super.candidates(state);

        List<Move>     pawns = new ArrayList<>();
        List<WallMove> walls = new ArrayList<>();
        for (Move m : base) {
            if (m instanceof PawnMove)  pawns.add(m);
            else if (m instanceof WallMove wm) walls.add(wm);
        }

        if (!walls.isEmpty()) {
            Player current = state.getCurrentPlayer();
            Player opp     = current.opponent();
            int myDist0  = pathChecker.shortestPathWithJumps(state, current);
            int oppDist0 = pathChecker.shortestPathWithJumps(state, opp);
            walls.sort(Comparator.comparingInt(
                    (WallMove wm) -> wallImpact(state, wm, current, myDist0, opp, oppDist0))
                    .reversed());
        }

        List<Move> result = new ArrayList<>(pawns);
        result.addAll(walls);
        return result;
    }

    private int wallImpact(GameState state, WallMove wm,
                            Player current, int myDist0,
                            Player opp,     int oppDist0) {
        GameState after = state.withWallMove(wm.wall());
        int newOppDist = pathChecker.shortestPathWithJumps(after, opp);
        if (newOppDist == Integer.MAX_VALUE) return -1; // disconnects — avoid
        int newMyDist = pathChecker.shortestPathWithJumps(after, current);
        if (newMyDist == Integer.MAX_VALUE) return -1;
        return (newOppDist - oppDist0) + (myDist0 - newMyDist);
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep-search strategy with impact-based move pruning.
 *
 * <p>Conventional pruned search (wall candidates within Chebyshev-4 of either pawn) still
 * yields ~30 candidates per node, limiting iterative deepening to around depth 8 in 1 second.
 * Sharp reduces this in two further passes:
 *
 * <ol>
 *   <li><b>Pawn moves</b>: only moves that reduce the player's BFS distance toward goal.
 *       Falls back to all legal pawn moves when every option is blocked (e.g. navigating
 *       around walls).</li>
 *   <li><b>Wall moves</b>: geometric pre-filter (Chebyshev ≤ 4) then an impact check —
 *       only walls that increase the opponent's BFS distance by at least {@value #MIN_WALL_IMPACT}
 *       cells. Walls that don't meaningfully block the opponent are discarded.</li>
 * </ol>
 *
 * <p>The resulting branching factor of ~8–12 allows depth 10–12, making Sharp more likely
 * to find winning lines and react correctly to late-game race situations.
 */
public class SharpStrategy extends AbstractSearchStrategy {

    private static final int   SHARP_DEPTH      = 12;
    private static final long  TIME_LIMIT_MS    = 1000;
    private static final int   WALL_PRUNE_DIST  = 4;
    private static final int   MIN_WALL_IMPACT  = 2;

    public SharpStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, SHARP_DEPTH);
    }

    @Override public String displayName() { return "Sharp"; }
    @Override public String description() {
        return "Deeper search that ignores weak moves — only advancing pawn moves and high-impact walls";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return 3 * (oppDist - myDist);
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist) + wallAdvantage(state);
    }

    @Override
    protected List<Move> candidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        int myDist     = pathChecker.shortestPath(state, current);
        int oppDist    = pathChecker.shortestPath(state, opp);

        List<Move> result = new ArrayList<>();

        // ── Pawn moves: advancing only ────────────────────────────────────────
        List<PawnMove> all = validator.getLegalPawnMoves(state);
        boolean hasAdvancing = false;
        for (PawnMove pm : all) {
            if (pathChecker.shortestPath(state.withPawnMove(pm.target()), current) < myDist) {
                result.add(pm);
                hasAdvancing = true;
            }
        }
        if (!hasAdvancing) result.addAll(all); // blocked: fall back to all legal pawn moves

        // ── Wall moves: geometric pre-filter then impact check ────────────────
        if (state.getWallCount(current) > 0) {
            Position p1 = state.getPawnPosition(Player.ONE);
            Position p2 = state.getPawnPosition(Player.TWO);

            for (WallMove wm : validator.getLegalWallMoves(state)) {
                Wall w = wm.wall();
                if (!nearEither(w, p1, p2)) continue;                   // geometric pre-filter
                GameState after = state.withWallMove(w);
                if (pathChecker.shortestPath(after, opp) - oppDist < MIN_WALL_IMPACT) continue;
                result.add(wm);
            }
        }

        return result;
    }

    private boolean nearEither(Wall wall, Position p1, Position p2) {
        return chebyshev(wall, p1) <= WALL_PRUNE_DIST
            || chebyshev(wall, p2) <= WALL_PRUNE_DIST;
    }

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Precision-search strategy that extends Sharp's approach to depth {@value #DEPTH}.
 *
 * <p>Sharp reaches depth 12 with up to 8 wall candidates (Chebyshev ≤ 4, impact ≥ 1). Sniper
 * tightens both filters — Chebyshev ≤ {@value #WALL_PRUNE_DIST}, impact ≥
 * {@value #MIN_WALL_IMPACT}, top {@value #MAX_WALL_CANDIDATES} — dropping the branching factor
 * low enough for the extra two plies. The trade-off: Sniper may overlook a decent wall that
 * Sharp would consider, but it is more likely to find a forced winning sequence that Sharp
 * cannot see within its depth budget.
 */
public class SniperStrategy extends AbstractSearchStrategy {

    private static final int   DEPTH               = 14;
    private static final long  TIME_LIMIT_MS        = 1000;
    private static final int   WALL_PRUNE_DIST      = 3;
    private static final int   MIN_WALL_IMPACT      = 2;
    private static final int   MAX_WALL_CANDIDATES  = 3;

    public SniperStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
    }

    @Override public String displayName() { return "Sniper"; }
    @Override public String description() {
        return "Searches deeper than Sharp using only the highest-impact walls and strictly advancing pawn moves";
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
        int myDist     = pathChecker.shortestPathWithJumps(state, current);
        int oppDist    = pathChecker.shortestPathWithJumps(state, opp);

        List<Move> result = new ArrayList<>();

        // Pawn moves: advancing only; fall back to all when fully blocked
        List<PawnMove> allPawns = validator.getLegalPawnMoves(state);
        boolean hasAdvancing = false;
        for (PawnMove pm : allPawns) {
            if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist) {
                result.add(pm);
                hasAdvancing = true;
            }
        }
        if (!hasAdvancing) result.addAll(allPawns);

        // Wall moves: tighter geo-filter, higher impact floor, fewer candidates
        if (state.getWallCount(current) > 0) {
            Position p1 = state.getPawnPosition(Player.ONE);
            Position p2 = state.getPawnPosition(Player.TWO);
            List<WallMove> legalWalls = validator.getLegalWallMoves(state);
            List<int[]> impacts = new ArrayList<>();
            for (int i = 0; i < legalWalls.size(); i++) {
                Wall w = legalWalls.get(i).wall();
                if (!nearEither(w, p1, p2)) continue;
                int impact = pathChecker.shortestPathWithJumps(state.withWallMove(w), opp) - oppDist;
                if (impact >= MIN_WALL_IMPACT) impacts.add(new int[]{impact, i});
            }
            impacts.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
            int limit = Math.min(impacts.size(), MAX_WALL_CANDIDATES);
            for (int k = 0; k < limit; k++) result.add(legalWalls.get(impacts.get(k)[1]));
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

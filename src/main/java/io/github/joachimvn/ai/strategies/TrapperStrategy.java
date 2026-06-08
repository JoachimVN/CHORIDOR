package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Deep search that targets walls near the opponent's pawn only.
 *
 * <p>Unlike Sharp and Balanced, which consider walls near either pawn, Trapper only considers
 * walls in a Chebyshev-{@value #WALL_PRUNE_DIST} neighbourhood of the opponent. It never
 * places defensive walls near its own position — every wall it considers is an offensive move
 * aimed at maximally extending the opponent's path. Walls are sorted by their BFS impact and
 * only the top {@value #MAX_WALL_CANDIDATES} are kept, keeping the branching factor low enough
 * for depth-{@value #DEPTH} search within the time budget.
 */
public class TrapperStrategy extends AbstractSearchStrategy {

    private static final int   DEPTH               = 10;
    private static final long  TIME_LIMIT_MS        = 1000;
    private static final int   WALL_PRUNE_DIST      = 4;
    private static final int   MIN_WALL_IMPACT      = 1;
    private static final int   MAX_WALL_CANDIDATES  = 8;

    public TrapperStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
    }

    @Override public String displayName() { return "Trapper"; }
    @Override public String description() {
        return "Only considers walls near the opponent, hunting for placements that maximally extend their path";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return 4 * oppDist - myDist;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist) + wallAdvantage(state);
    }

    @Override
    protected List<Move> candidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        Position oppPos = state.getPawnPosition(opp);
        int myDist      = pathChecker.shortestPathWithJumps(state, current);
        int oppDist     = pathChecker.shortestPathWithJumps(state, opp);

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

        // Wall moves: near the opponent's pawn only, sorted by impact, capped
        if (state.getWallCount(current) > 0) {
            List<WallMove> legalWalls = validator.getLegalWallMoves(state);
            List<int[]> impacts = new ArrayList<>();
            for (int i = 0; i < legalWalls.size(); i++) {
                Wall w = legalWalls.get(i).wall();
                if (chebyshev(w, oppPos) > WALL_PRUNE_DIST) continue;
                int impact = pathChecker.shortestPathWithJumps(state.withWallMove(w), opp) - oppDist;
                if (impact >= MIN_WALL_IMPACT) impacts.add(new int[]{impact, i});
            }
            impacts.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
            int limit = Math.min(impacts.size(), MAX_WALL_CANDIDATES);
            for (int k = 0; k < limit; k++) result.add(legalWalls.get(impacts.get(k)[1]));
        }

        return result;
    }

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }
}

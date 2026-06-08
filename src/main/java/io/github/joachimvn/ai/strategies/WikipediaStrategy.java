package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Orthodox doctrine from the Quoridor Wikipedia article.
 *
 * <p>Three distinct phases:
 * <ol>
 *   <li><b>Opening</b> (before reaching the centerline): advance the pawn every turn, no walls.
 *       Conserving all 10 walls through the opening gives maximum flexibility when it matters.</li>
 *   <li><b>Midgame</b> (past center, ≥ 4 walls remaining): balanced minimax — distance plus a
 *       moderate wall-advantage term. Walls are spent on genuinely high-impact placements only.</li>
 *   <li><b>Endgame</b> (≤ 3 walls left for either player): pure race. Wall reserves are nearly
 *       exhausted so distance dominates; the wall bonus disappears entirely.</li>
 * </ol>
 */
public class WikipediaStrategy extends AbstractSearchStrategy {

    private static final int   DEPTH               = 10;
    private static final long  TIME_LIMIT_MS        = 1000;
    private static final int   WALL_PRUNE_DIST      = 4;
    private static final int   MIN_WALL_IMPACT      = 1;
    private static final int   MAX_WALL_CANDIDATES  = 8;

    /** Opening phase ends when AI has fewer than this many steps to go. */
    private static final int CENTER_DIST  = 4;
    /** Wall count threshold below which we switch to pure-race endgame. */
    private static final int ENDGAME_WALLS = 3;

    public WikipediaStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
    }

    @Override public String displayName() { return "Wikipedia"; }
    @Override public String description() {
        return "Orthodox doctrine: advance freely in opening, wall conservatively in midgame, race in endgame";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return 3 * (oppDist - myDist);
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        boolean endgame = state.getWallCount(aiPlayer()) <= ENDGAME_WALLS
                       || state.getWallCount(aiPlayer().opponent()) <= ENDGAME_WALLS;
        if (endgame) return 5 * (oppDist - myDist);
        return 3 * (oppDist - myDist) + wallAdvantage(state);
    }

    @Override
    protected List<Move> candidates(GameState state) {
        Player current = state.getCurrentPlayer();
        int myDist     = pathChecker.shortestPathWithJumps(state, current);

        // Opening phase: pawn-only moves toward goal (for both AI and opponent in search)
        boolean inOpening = myDist > CENTER_DIST;
        if (inOpening) {
            List<PawnMove> all = validator.getLegalPawnMoves(state);
            List<Move> advances = new ArrayList<>();
            for (PawnMove pm : all) {
                if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist) {
                    advances.add(pm);
                }
            }
            return advances.isEmpty() ? new ArrayList<>(all) : advances;
        }

        // Midgame / endgame: Sharp-style impact-filtered candidates
        Player opp     = current.opponent();
        int oppDist    = pathChecker.shortestPathWithJumps(state, opp);
        Position p1    = state.getPawnPosition(Player.ONE);
        Position p2    = state.getPawnPosition(Player.TWO);

        List<Move> result = new ArrayList<>();
        List<PawnMove> allPawns = validator.getLegalPawnMoves(state);
        boolean hasAdvancing = false;
        for (PawnMove pm : allPawns) {
            if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist) {
                result.add(pm);
                hasAdvancing = true;
            }
        }
        if (!hasAdvancing) result.addAll(allPawns);

        if (state.getWallCount(current) > 0) {
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

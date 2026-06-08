package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Baiter: lets the opponent advance deep into the board, then cuts off their path forward.
 *
 * <p>The strategy advances normally toward its own goal while placing walls that are "ahead"
 * of the opponent — between the opponent's current position and their goal row. Once an opponent
 * has committed to advancing, these walls force a U-turn: the opponent cannot continue forward
 * and must backtrack all the way around, adding a large path cost from an already distant position.
 *
 * <p>Walls ahead of the opponent get a priority bonus over equally-impacting neutral walls.
 * When the opponent is still near their own side the bonus is rarely triggered; once they
 * cross midfield, the blocking walls become devastating. The evaluation weights opponent
 * distance (4× vs 1×) so the AI is rewarded for maximising how far the opponent must travel.
 */
public class BaiterStrategy extends AbstractSearchStrategy {

    private static final int   DEPTH               = 10;
    private static final long  TIME_LIMIT_MS        = 1000;
    private static final int   MIN_WALL_IMPACT      = 1;
    private static final int   MAX_WALL_CANDIDATES  = 8;
    private static final int   AHEAD_BONUS          = 4;

    public BaiterStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
    }

    @Override public String displayName() { return "Baiter"; }
    @Override public String description() {
        return "Lures the opponent forward then cuts off their path, forcing a costly U-turn";
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
        Player current  = state.getCurrentPlayer();
        Player opp      = current.opponent();
        int myDist      = pathChecker.shortestPathWithJumps(state, current);
        int oppDist     = pathChecker.shortestPathWithJumps(state, opp);
        Position oppPos = state.getPawnPosition(opp);

        List<Move> result = new ArrayList<>();

        // Advancing pawn moves only; fall back to all when fully blocked
        List<PawnMove> allPawns = validator.getLegalPawnMoves(state);
        boolean hasAdvancing = false;
        for (PawnMove pm : allPawns) {
            if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist) {
                result.add(pm);
                hasAdvancing = true;
            }
        }
        if (!hasAdvancing) result.addAll(allPawns);

        // Wall candidates scored by impact + bonus for walls ahead of the opponent
        if (state.getWallCount(current) > 0) {
            List<WallMove> legalWalls = validator.getLegalWallMoves(state);
            List<int[]> wallScores = new ArrayList<>();
            for (int i = 0; i < legalWalls.size(); i++) {
                Wall w = legalWalls.get(i).wall();
                int impact = pathChecker.shortestPathWithJumps(state.withWallMove(w), opp) - oppDist;
                if (impact < MIN_WALL_IMPACT) continue;
                int bonus = isAheadOfOpponent(w, opp, oppPos) ? AHEAD_BONUS : 0;
                wallScores.add(new int[]{impact * 2 + bonus, i});
            }
            wallScores.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
            int limit = Math.min(wallScores.size(), MAX_WALL_CANDIDATES);
            for (int k = 0; k < limit; k++) result.add(legalWalls.get(wallScores.get(k)[1]));
        }

        return result;
    }

    /**
     * Returns true if the wall lies between the opponent's current position and their goal row.
     * Walls here cut off the opponent's forward path, forcing a U-turn.
     *
     * <p>Player.TWO (goalRow=8) travels up — "ahead" means wall.row ≥ oppPos.row.
     * Player.ONE (goalRow=0) travels down — "ahead" means wall.row ≤ oppPos.row.
     */
    private static boolean isAheadOfOpponent(Wall wall, Player opp, Position oppPos) {
        if (opp.goalRow() == GameState.BOARD_SIZE - 1) {
            return wall.row() >= oppPos.row();
        } else {
            return wall.row() <= oppPos.row();
        }
    }
}

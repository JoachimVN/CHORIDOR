package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Search strategy that prunes the wall search space to walls within {@code WALL_PRUNE_DIST}
 * of either pawn, cutting the branching factor from ~130 to ~20-30 and allowing much
 * deeper search. Subclasses still supply only the heuristic via {@link #score(int, int)}.
 */
abstract class AbstractPrunedSearchStrategy extends AbstractSearchStrategy {

    private static final int  WALL_PRUNE_DIST = 4;
    private static final int  MAX_DEPTH       = 8;
    private static final long TIME_LIMIT_MS   = 1000;

    protected AbstractPrunedSearchStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, MAX_DEPTH);
    }

    @Override
    protected List<Move> candidates(GameState state) {
        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));
        if (state.getWallCount(state.getCurrentPlayer()) > 0) {
            Position p1 = state.getPawnPosition(Player.ONE);
            Position p2 = state.getPawnPosition(Player.TWO);
            for (WallMove wm : validator.getLegalWallMoves(state)) {
                if (nearEither(wm.wall(), p1, p2)) moves.add(wm);
            }
        }
        return moves;
    }

    private boolean nearEither(Wall wall, Position p1, Position p2) {
        return chebyshev(wall, p1) <= WALL_PRUNE_DIST
            || chebyshev(wall, p2) <= WALL_PRUNE_DIST;
    }

    private int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }
}

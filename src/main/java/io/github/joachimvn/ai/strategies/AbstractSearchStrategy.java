package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterative-deepening minimax with alpha-beta pruning, shared by every search-based
 * strategy. Subclasses supply the non-terminal heuristic via {@link #score(int, int)}
 * and may narrow the move set via {@link #candidates(GameState)}.
 *
 * <p>Re-entrant: the search deadline is threaded through method parameters rather than
 * held as mutable state, so overlapping {@code decide} calls on a single instance
 * (e.g. an in-flight search when the game is reset) cannot corrupt each other.
 */
abstract class AbstractSearchStrategy implements Strategy {

    // Terminal score — must exceed the largest magnitude any score() can return.
    // The heaviest weighting in use is Rusher's 3*dist; a BFS goal distance is bounded
    // by BOARD_SIZE^2, so 4*BOARD_SIZE^2 sits safely above every possible heuristic value.
    protected static final int WIN = 4 * GameState.BOARD_SIZE * GameState.BOARD_SIZE + 1;

    private final Player aiPlayer;
    private final long   timeLimitMs;
    private final int    maxDepth;

    protected final MoveValidator validator   = new MoveValidator();
    protected final PathChecker   pathChecker = new PathChecker();

    protected AbstractSearchStrategy(Player aiPlayer, long timeLimitMs, int maxDepth) {
        this.aiPlayer    = aiPlayer;
        this.timeLimitMs = timeLimitMs;
        this.maxDepth    = maxDepth;
    }

    /** Combine the two BFS goal distances into a non-terminal score; higher favours aiPlayer. */
    protected abstract int score(int myDist, int oppDist);

    /** Moves to consider at a node. Defaults to all legal moves; override to prune. */
    protected List<Move> candidates(GameState state) {
        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));
        moves.addAll(validator.getLegalWallMoves(state));
        return moves;
    }

    @Override
    public Move decide(GameState state) {
        long deadline = System.currentTimeMillis() + timeLimitMs;
        List<Move> moves = candidates(state);
        if (moves.isEmpty()) throw new NoSuchElementException("No legal moves available");
        Move best = moves.get(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() >= deadline) break;
            Move candidate = searchDepth(state, moves, depth, deadline);
            if (candidate != null) best = candidate;
        }
        return best;
    }

    private Move searchDepth(GameState state, List<Move> moves, int depth, long deadline) {
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : moves) {
            if (System.currentTimeMillis() >= deadline) break;
            int s = minimax(apply(state, move), depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false, deadline);
            if (s > bestScore) {
                bestScore = s;
                best = move;
            }
        }
        return best;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing, long deadline) {
        int s = evaluate(state);
        if (Math.abs(s) >= WIN || depth == 0 || System.currentTimeMillis() >= deadline) return s;

        List<Move> moves = candidates(state);
        if (maximizing) {
            int max = Integer.MIN_VALUE;
            for (Move move : moves) {
                max = Math.max(max, minimax(apply(state, move), depth - 1, alpha, beta, false, deadline));
                alpha = Math.max(alpha, max);
                if (beta <= alpha) break;
            }
            return max;
        } else {
            int min = Integer.MAX_VALUE;
            for (Move move : moves) {
                min = Math.min(min, minimax(apply(state, move), depth - 1, alpha, beta, true, deadline));
                beta = Math.min(beta, min);
                if (beta <= alpha) break;
            }
            return min;
        }
    }

    private int evaluate(GameState state) {
        int myDist  = pathChecker.shortestPath(state, aiPlayer);
        int oppDist = pathChecker.shortestPath(state, aiPlayer.opponent());
        if (myDist  == 0)                 return  WIN;
        if (oppDist == 0)                 return -WIN;
        if (myDist  == Integer.MAX_VALUE) return -WIN;
        if (oppDist == Integer.MAX_VALUE) return  WIN;
        return score(myDist, oppDist);
    }

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

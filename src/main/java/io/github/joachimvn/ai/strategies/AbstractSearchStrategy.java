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
    // The heaviest distance weighting in use is 3*dist (Rusher, Balanced, Adaptive),
    // optionally combined with a wall-reserve term bounded by WALLS_PER_PLAYER. A BFS
    // goal distance is bounded by BOARD_SIZE^2, so 3*BOARD_SIZE^2 + WALLS_PER_PLAYER stays
    // below 4*BOARD_SIZE^2, which therefore sits safely above every possible heuristic value.
    protected static final int WIN = 4 * GameState.BOARD_SIZE * GameState.BOARD_SIZE + 1;

    // Default per-wall reserve cost for strategies that don't define their own wall economy.
    // Weight 1 means a wall must buy at least 1 net distance cell to be worth spending.
    // Weight 2 caused walls to be hoarded too aggressively: with a large wall advantage the
    // reserve bonus outweighed the distance gain of even a good blocking move, so the AI
    // refused to block even when one step away from losing.
    private static final int WALL_RESERVE_WEIGHT = 1;

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

    /**
     * Wall-aware non-terminal score; higher favours aiPlayer. Adds a wall-reserve term to the
     * distance-only {@link #score(int, int)} so a wall is only worth playing when the distance it
     * buys outweighs losing it from reserve. Without this cost the wall-blind strategies treat
     * blocking as free: they dump all ten walls in the opening and, assuming the opponent can
     * block for free too, over-fear advancing and fall into a pawn standoff. Subclasses that weigh
     * wall reserves or game phase differently override this.
     */
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist) + WALL_RESERVE_WEIGHT * wallAdvantage(state);
    }

    /** Wall-reserve edge for aiPlayer: positive when aiPlayer holds more walls than the opponent. */
    protected final int wallAdvantage(GameState state) {
        return state.getWallCount(aiPlayer) - state.getWallCount(aiPlayer.opponent());
    }

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
        Move best       = null;
        int bestScore   = Integer.MIN_VALUE;
        int bestMyDist  = Integer.MAX_VALUE;
        int bestOppDist = Integer.MIN_VALUE;
        for (Move move : moves) {
            if (System.currentTimeMillis() >= deadline) break;
            GameState next = apply(state, move);
            int s = minimax(next, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false, deadline);
            int myDist  = pathChecker.shortestPath(next, aiPlayer);
            int oppDist = pathChecker.shortestPath(next, aiPlayer.opponent());
            // Two-level tie-break when the search scores are equal:
            //   1. Prefer smaller myDist (keep advancing toward own goal).
            //   2. If myDist is also equal, prefer larger oppDist (block the opponent more).
            // Separating these avoids the single-number gap (myDist - oppDist) masking cases
            // where different (myDist, oppDist) pairs cancel to the same value, which caused
            // deterministic oscillation — e.g. in a forced loss all pawn moves score -WIN,
            // so the first move alphabetically in the list was always chosen regardless of
            // whether it made progress toward goal.
            boolean better = s > bestScore
                || (s == bestScore && myDist < bestMyDist)
                || (s == bestScore && myDist == bestMyDist && oppDist > bestOppDist);
            if (better) {
                bestScore   = s;
                bestMyDist  = myDist;
                bestOppDist = oppDist;
                best = move;
            }
        }
        return best;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing, long deadline) {
        int s = evaluate(state);
        // Bias terminal scores by the remaining depth so the search strictly prefers winning
        // sooner and losing later. Without this every line that wins anywhere inside the horizon
        // scores exactly WIN — a flat plateau with no gradient toward the goal — so a winning AI
        // dawdles, shuffling back and forth instead of finishing (and AI-vs-AI can deadlock).
        // More depth remaining == fewer plies to the terminal == a larger bias.
        if (Math.abs(s) >= WIN) return s > 0 ? s + depth : s - depth;
        if (depth == 0 || System.currentTimeMillis() >= deadline) return s;

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
        return score(state, myDist, oppDist);
    }

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

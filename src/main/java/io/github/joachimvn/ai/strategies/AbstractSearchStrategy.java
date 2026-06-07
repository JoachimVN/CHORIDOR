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

        // Fast-path: when already winning the race (my BFS distance strictly less than the
        // opponent's), advancing the pawn is almost always optimal. Any wall placed instead
        // costs a move we can't afford and lets the opponent close the gap. The minimax
        // can't reliably find the winning pawn line at depth 7+ within the time budget when
        // many walls remain (branching factor explodes), so we shortcut directly to the
        // best advancing pawn move and skip the search entirely in this case.
        PawnMove racing = racingMove(state, moves);
        if (racing != null) return racing;

        // Full iterative-deepening minimax for all other positions.
        // Sort root candidates by BFS distance so the most promising moves come first —
        // alpha-beta prunes large subtrees once a good move is found early.
        moves.sort(orderByProgress(state));
        Move best = moves.get(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() >= deadline) break;
            Move candidate = searchDepth(state, moves, depth, deadline);
            if (candidate != null) best = candidate;
        }
        return best;
    }

    /** If winning the race (myDist < oppDist), return the pawn move that best advances toward goal. */
    private PawnMove racingMove(GameState state, List<Move> moves) {
        int myDist  = pathChecker.shortestPath(state, aiPlayer);
        int oppDist = pathChecker.shortestPath(state, aiPlayer.opponent());
        if (myDist <= 0 || myDist >= oppDist) return null;
        PawnMove best = null;
        int bestDist  = myDist;
        for (Move m : moves) {
            if (m instanceof PawnMove pm) {
                int d = pathChecker.shortestPath(apply(state, pm), aiPlayer);
                if (d < bestDist) { bestDist = d; best = pm; }
            }
        }
        return best;
    }

    /** Comparator using only row-to-goal distance — no BFS call, safe at every recursive minimax node. */
    private static java.util.Comparator<Move> orderByRowProgress(GameState state) {
        int goalRow = state.getCurrentPlayer().goalRow();
        return (a, b) -> {
            boolean aIsPawn = a instanceof PawnMove;
            boolean bIsPawn = b instanceof PawnMove;
            if (aIsPawn != bIsPawn) return aIsPawn ? -1 : 1;
            if (!aIsPawn) return 0;
            int dA = Math.abs(((PawnMove) a).target().row() - goalRow);
            int dB = Math.abs(((PawnMove) b).target().row() - goalRow);
            return Integer.compare(dA, dB);
        };
    }

    private java.util.Comparator<Move> orderByProgress(GameState state) {
        Player mover   = state.getCurrentPlayer();
        boolean aiMoves = mover == aiPlayer;
        Player scored  = aiMoves ? aiPlayer : aiPlayer.opponent();
        return (a, b) -> {
            // Pawn moves before walls
            boolean aIsPawn = a instanceof PawnMove;
            boolean bIsPawn = b instanceof PawnMove;
            if (aIsPawn != bIsPawn) return aIsPawn ? -1 : 1;
            if (!aIsPawn) return 0; // relative wall order doesn't matter here
            // Within pawn moves: prefer smaller BFS distance to the moving player's goal
            int dA = pathChecker.shortestPath(apply(state, a), scored);
            int dB = pathChecker.shortestPath(apply(state, b), scored);
            return Integer.compare(dA, dB);
        };
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
        // Bias terminal scores by remaining depth: prefer winning sooner, losing later.
        if (Math.abs(s) >= WIN) return s > 0 ? s + depth : s - depth;
        if (depth == 0 || System.currentTimeMillis() >= deadline) return s;

        List<Move> moves = candidates(state);
        moves.sort(orderByRowProgress(state));
        return maximizing
            ? maximize(moves, state, depth, alpha, beta, deadline)
            : minimize(moves, state, depth, alpha, beta, deadline);
    }

    private int maximize(List<Move> moves, GameState state, int depth, int alpha, int beta, long deadline) {
        int max = Integer.MIN_VALUE;
        for (Move move : moves) {
            max = Math.max(max, minimax(apply(state, move), depth - 1, alpha, beta, false, deadline));
            alpha = Math.max(alpha, max);
            if (beta <= alpha) break;
        }
        return max;
    }

    private int minimize(List<Move> moves, GameState state, int depth, int alpha, int beta, long deadline) {
        int min = Integer.MAX_VALUE;
        for (Move move : moves) {
            min = Math.min(min, minimax(apply(state, move), depth - 1, alpha, beta, true, deadline));
            beta = Math.min(beta, min);
            if (beta <= alpha) break;
        }
        return min;
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

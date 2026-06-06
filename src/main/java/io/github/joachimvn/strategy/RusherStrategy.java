package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.ArrayList;
import java.util.List;

// Wall-pruned iterative deepening. Weights own advancement 3x over blocking.
// Plays like a racer: places walls only when they buy significant path improvement.
public class RusherStrategy implements Strategy {

    private static final int  WALL_PRUNE_DIST = 4;
    private static final int  MAX_DEPTH       = 8;
    private static final long TIME_LIMIT_MS   = 1000;
    private static final int  WIN             = GameState.BOARD_SIZE * GameState.BOARD_SIZE + 1;

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private long deadline;

    public RusherStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Rusher"; }
    @Override public String description()   { return "Same deep search as Tactical, but races toward the goal rather than blocking"; }

    @Override
    public Move decide(GameState state) {
        deadline = System.currentTimeMillis() + TIME_LIMIT_MS;
        List<Move> moves = candidates(state);
        Move best = moves.get(0);
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (System.currentTimeMillis() >= deadline) break;
            Move candidate = searchDepth(state, moves, depth);
            if (candidate != null) best = candidate;
        }
        return best;
    }

    private Move searchDepth(GameState state, List<Move> moves, int depth) {
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : moves) {
            if (System.currentTimeMillis() >= deadline) break;
            int score = minimax(apply(state, move), depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            if (score > bestScore) { bestScore = score; best = move; }
        }
        return best;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing) {
        int score = evaluate(state);
        if (Math.abs(score) >= WIN || depth == 0 || System.currentTimeMillis() >= deadline) return score;
        List<Move> moves = candidates(state);
        if (maximizing) {
            int max = Integer.MIN_VALUE;
            for (Move m : moves) {
                max = Math.max(max, minimax(apply(state, m), depth - 1, alpha, beta, false));
                alpha = Math.max(alpha, max);
                if (beta <= alpha) break;
            }
            return max;
        } else {
            int min = Integer.MAX_VALUE;
            for (Move m : moves) {
                min = Math.min(min, minimax(apply(state, m), depth - 1, alpha, beta, true));
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
        return oppDist - 3 * myDist;
    }

    private List<Move> candidates(GameState state) {
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

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

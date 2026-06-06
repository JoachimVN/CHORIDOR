package io.github.joachimvn.ai;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.ArrayList;
import java.util.List;

public class MinimaxStrategy implements Strategy {

    // How many plies (half-moves) ahead the AI searches. Depth 2 = AI move + human response.
    // Each extra level multiplies work by ~130 (branching factor), so increases slowly.
    private static final int DEPTH = 2;

    // Terminal score — must be strictly greater than any value the heuristic can return.
    // The heuristic is bounded by ±BOARD_SIZE² (max BFS distance on the grid), so this
    // scales correctly if BOARD_SIZE ever changes.
    private static final int WIN = GameState.BOARD_SIZE * GameState.BOARD_SIZE + 1;

    private final Player      aiPlayer;
    private final long        timeLimitMs;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private long deadline;

    public MinimaxStrategy(Player aiPlayer, long timeLimitMs) {
        this.aiPlayer    = aiPlayer;
        this.timeLimitMs = timeLimitMs;
    }

    @Override public String displayName() { return "Minimax"; }
    @Override public String description()   { return "Searches several moves ahead using minimax with alpha-beta pruning"; }

    @Override
    public Move decide(GameState state) {
        deadline = System.currentTimeMillis() + timeLimitMs;
        List<Move> moves = allMoves(state);
        Move best = moves.get(0);
        for (int depth = 1; depth <= DEPTH; depth++) {
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
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing) {
        int score = evaluate(state);
        if (Math.abs(score) >= WIN || depth == 0 || System.currentTimeMillis() >= deadline) return score;

        List<Move> moves = allMoves(state);
        if (maximizing) {
            int max = Integer.MIN_VALUE;
            for (Move move : moves) {
                max = Math.max(max, minimax(apply(state, move), depth - 1, alpha, beta, false));
                alpha = Math.max(alpha, max);
                if (beta <= alpha) break;
            }
            return max;
        } else {
            int min = Integer.MAX_VALUE;
            for (Move move : moves) {
                min = Math.min(min, minimax(apply(state, move), depth - 1, alpha, beta, true));
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
        return oppDist - myDist;
    }

    private List<Move> allMoves(GameState state) {
        List<Move> moves = new ArrayList<>();
        moves.addAll(validator.getLegalPawnMoves(state));
        moves.addAll(validator.getLegalWallMoves(state));
        return moves;
    }

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var target) -> state.withPawnMove(target);
            case WallMove(var wall)   -> state.withWallMove(wall);
        };
    }
}

package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.ArrayList;
import java.util.List;

public class MinimaxStrategy implements Strategy {

    private static final int DEPTH    = 2;
    private static final int WIN      = 10_000;

    private final Player aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public MinimaxStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override
    public Move decide(GameState state) {
        List<Move> moves = allMoves(state);
        Move best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Move move : moves) {
            int score = minimax(apply(state, move), DEPTH - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing) {
        int score = evaluate(state);
        if (Math.abs(score) >= WIN || depth == 0) return score;

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
        if (myDist  == 0)             return  WIN;
        if (oppDist == 0)             return -WIN;
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
            case PawnMove pm -> state.withPawnMove(pm.target());
            case WallMove wm -> state.withWallMove(wm.wall());
        };
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Exhaustive one-ply greedy search over the full legal move set.
 *
 * <p>Every legal move (all pawn moves and all wall placements) is evaluated by computing
 * the resulting BFS distance gap after that single move. The move that gives the best
 * immediate {@code oppDist − myDist} is played with no further lookahead. Unlike Greedy,
 * which only looks at pawn moves, OneStep considers walls too — but unlike Minimax it
 * never thinks about the opponent's reply.
 */
public class OneStepStrategy implements Strategy {

    private final Player      aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public OneStepStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Short-Sighted"; }
    @Override public String description() {
        return "Evaluates every legal move only one step ahead";
    }

    @Override
    public Move decide(GameState state) {
        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));
        moves.addAll(validator.getLegalWallMoves(state));
        if (moves.isEmpty()) throw new NoSuchElementException("No legal moves");

        Move best      = moves.get(0);
        int  bestScore = Integer.MIN_VALUE;

        for (Move move : moves) {
            GameState next = apply(state, move);
            int myDist  = pathChecker.shortestPathWithJumps(next, aiPlayer);
            int oppDist = pathChecker.shortestPathWithJumps(next, aiPlayer.opponent());
            if (myDist == 0) return move; // instant win
            int score = oppDist - myDist;
            if (score > bestScore) { bestScore = score; best = move; }
        }
        return best;
    }

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

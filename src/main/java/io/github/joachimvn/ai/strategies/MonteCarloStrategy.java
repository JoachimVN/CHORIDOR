package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Allocates the full 1-second budget to random rollouts rather than minimax search.
 *
 * <p>Each candidate move is evaluated by repeatedly playing both sides with random pawn
 * moves until someone wins or the rollout depth cap is reached. Candidates are cycled
 * round-robin so they each accumulate roughly equal trials. The candidate with the best
 * win rate across all its trials is chosen.
 *
 * <p>Rollouts use pawn moves only so each step is O(1) (row comparison) rather than a BFS
 * call, keeping thousands of rollouts feasible inside the budget. At the depth cap, BFS
 * is called once to break the tie by distance.
 */
public class MonteCarloStrategy implements Strategy {

    private static final long TIME_LIMIT_MS      = 1000;
    private static final int  MAX_ROLLOUT_DEPTH  = 60;
    private static final int  WALL_PRUNE_DIST    = 4;

    private final Player      aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private final Random        random      = new Random();

    public MonteCarloStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Monte Carlo"; }
    @Override public String description() {
        return "Runs thousands of random game simulations and picks the move that wins most often";
    }

    @Override
    public Move decide(GameState state) {
        List<Move> candidates = buildCandidates(state);
        if (candidates.isEmpty()) throw new NoSuchElementException("No legal moves");

        long deadline  = System.currentTimeMillis() + TIME_LIMIT_MS;
        int[] wins     = new int[candidates.size()];
        int[] trials   = new int[candidates.size()];
        int   idx      = 0;

        while (System.currentTimeMillis() < deadline) {
            GameState after = apply(state, candidates.get(idx));
            if (rollout(after, deadline)) wins[idx]++;
            trials[idx]++;
            idx = (idx + 1) % candidates.size();
        }

        int best = 0;
        for (int i = 1; i < candidates.size(); i++) {
            double ri = trials[i]    == 0 ? 0 : (double) wins[i]    / trials[i];
            double rb = trials[best] == 0 ? 0 : (double) wins[best] / trials[best];
            if (ri > rb) best = i;
        }
        return candidates.get(best);
    }

    private boolean rollout(GameState state, long deadline) {
        GameState cur = state;
        for (int d = 0; d < MAX_ROLLOUT_DEPTH; d++) {
            if (hasWon(cur, aiPlayer))            return true;
            if (hasWon(cur, aiPlayer.opponent())) return false;
            if (System.currentTimeMillis() >= deadline) break;
            List<PawnMove> pawns = validator.getLegalPawnMoves(cur);
            if (pawns.isEmpty()) break;
            cur = cur.withPawnMove(pawns.get(random.nextInt(pawns.size())).target());
        }
        int myDist  = pathChecker.shortestPathWithJumps(cur, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(cur, aiPlayer.opponent());
        return myDist <= oppDist;
    }

    private boolean hasWon(GameState state, Player player) {
        return state.getPawnPosition(player).row() == player.goalRow();
    }

    private List<Move> buildCandidates(GameState state) {
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

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

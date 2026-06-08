package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Monte Carlo tree search with UCB1 candidate selection and heuristic rollouts.
 *
 * <p>Candidates are the pruned move set (Chebyshev-4). The time budget is spent doing
 * rollouts in round-UCB1 order — candidates with high win rates are visited more, but
 * unvisited candidates are always tried first (UCB1 infinity rule).
 *
 * <p>Rollout policy (both players): row-progress pawn advance; with probability
 * {@value #WALL_PROB} place the highest-impact wall from a small random sample instead.
 * This makes rollouts simulate real Quoridor play rather than a wall-free pawn race,
 * which was the core flaw of the previous random-pawn-only implementation.
 */
public class MonteCarloStrategy implements Strategy {

    private static final long TIME_LIMIT_MS     = 1000;
    private static final int  MAX_ROLLOUT_DEPTH = 60;
    private static final int  WALL_PRUNE_DIST   = 4;
    private static final float WALL_PROB        = 0.30f;
    private static final int  WALL_SAMPLES      = 5;
    private static final double UCB_C           = Math.sqrt(2);

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private final Random        random      = new Random();

    public MonteCarloStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Monte Carlo"; }
    @Override public String description() {
        return "UCB1-guided simulations with heuristic rollouts that include wall placement";
    }

    @Override
    public Move decide(GameState state) {
        List<Move> candidates = buildCandidates(state);
        if (candidates.isEmpty()) throw new NoSuchElementException("No legal moves");

        int n = candidates.size();
        int[] wins   = new int[n];
        int[] visits = new int[n];
        int   total  = 0;

        long deadline = System.currentTimeMillis() + TIME_LIMIT_MS;
        while (System.currentTimeMillis() < deadline) {
            int idx = selectUCB1(wins, visits, total, n);
            GameState after = apply(state, candidates.get(idx));
            if (rollout(after, deadline)) wins[idx]++;
            visits[idx]++;
            total++;
        }

        // Pick candidate with best win rate (most visits breaks ties)
        int best = 0;
        for (int i = 1; i < n; i++) {
            double rateI = visits[i]    == 0 ? 0 : (double) wins[i]    / visits[i];
            double rateB = visits[best] == 0 ? 0 : (double) wins[best] / visits[best];
            if (rateI > rateB || (rateI == rateB && visits[i] > visits[best])) best = i;
        }
        return candidates.get(best);
    }

    private int selectUCB1(int[] wins, int[] visits, int total, int n) {
        // Always visit unvisited candidates first
        for (int i = 0; i < n; i++) { if (visits[i] == 0) return i; }
        double best = Double.NEGATIVE_INFINITY;
        int    bestIdx = 0;
        double lnTotal = Math.log(total);
        for (int i = 0; i < n; i++) {
            double score = (double) wins[i] / visits[i]
                         + UCB_C * Math.sqrt(lnTotal / visits[i]);
            if (score > best) { best = score; bestIdx = i; }
        }
        return bestIdx;
    }

    /** Heuristic rollout: row-progress advance, occasionally place the best sampled wall. */
    private boolean rollout(GameState state, long deadline) {
        GameState cur = state;
        for (int d = 0; d < MAX_ROLLOUT_DEPTH; d++) {
            if (hasWon(cur, aiPlayer))            return true;
            if (hasWon(cur, aiPlayer.opponent())) return false;
            if (System.currentTimeMillis() >= deadline) break;
            cur = heuristicStep(cur);
        }
        int myDist  = pathChecker.shortestPathWithJumps(cur, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(cur, aiPlayer.opponent());
        return myDist <= oppDist;
    }

    private GameState heuristicStep(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();

        // With WALL_PROB: try placing a sampled high-impact wall
        if (state.getWallCount(current) > 0 && random.nextFloat() < WALL_PROB) {
            List<WallMove> walls = validator.getLegalWallMoves(state);
            if (!walls.isEmpty()) {
                int oppDist = pathChecker.shortestPathWithJumps(state, opp);
                WallMove bestWall  = null;
                int      bestImpact = 0;
                int      samples   = Math.min(WALL_SAMPLES, walls.size());
                for (int i = 0; i < samples; i++) {
                    WallMove wm = walls.get(random.nextInt(walls.size()));
                    int impact = pathChecker.shortestPathWithJumps(state.withWallMove(wm.wall()), opp) - oppDist;
                    if (impact > bestImpact) { bestImpact = impact; bestWall = wm; }
                }
                if (bestWall != null && bestImpact > 0)
                    return state.withWallMove(bestWall.wall());
            }
        }

        // Row-progress pawn advance (no BFS — O(1), keeps rollouts fast)
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) return state;
        int goalRow  = current.goalRow();
        PawnMove best     = pawns.get(0);
        int      bestDist = Math.abs(best.target().row() - goalRow);
        for (PawnMove pm : pawns) {
            int d = Math.abs(pm.target().row() - goalRow);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return state.withPawnMove(best.target());
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

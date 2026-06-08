package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Monte Carlo tree search with UCB1 candidate selection and heuristic rollouts.
 *
 * <p>Candidates use impact-based wall filtering (Chebyshev ≤ 4, impact ≥ 1, top 8 by impact)
 * rather than naive Chebyshev proximity. This reduces the candidate set from ~30 to ~8–12,
 * allowing roughly 3× more rollouts per second and focusing the search on walls that actually
 * matter. The UCB1 selection ensures exploration/exploitation balance across all candidates.
 *
 * <p>Oscillation prevention: if the chosen pawn move would return to the previous pawn position,
 * the strategy overrides to the BFS-optimal advancing move instead.
 */
public class MonteCarloStrategy implements Strategy {

    private static final long   TIME_LIMIT_MS       = 1000;
    private static final int    MAX_ROLLOUT_DEPTH   = 60;
    private static final int    WALL_PRUNE_DIST     = 4;
    private static final int    MIN_WALL_IMPACT     = 1;
    private static final int    MAX_WALL_CANDIDATES = 8;
    private static final float  WALL_PROB           = 0.30f;
    private static final int    WALL_SAMPLES        = 5;
    private static final double UCB_C               = Math.sqrt(2);

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private final Random        random      = new Random();

    private Position prevPosition = null;

    public MonteCarloStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Monte Carlo"; }
    @Override public String description() {
        return "UCB1-guided simulations with impact-filtered wall candidates and BFS tiebreaking";
    }

    @Override
    public Move decide(GameState state) {
        List<Move> candidates = buildCandidates(state);
        if (candidates.isEmpty()) throw new NoSuchElementException("No legal moves");

        int n        = candidates.size();
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

        Position myPos = state.getPawnPosition(aiPlayer);

        // Select best: win rate primary, BFS distances as tiebreakers
        int    best        = 0;
        double bestRate    = visits[0] == 0 ? 0 : (double) wins[0] / visits[0];
        int    bestMyDist  = myDistAfter(state, candidates.get(0));
        int    bestOppDist = oppDistAfter(state, candidates.get(0));

        for (int i = 1; i < n; i++) {
            double rate    = visits[i] == 0 ? 0 : (double) wins[i] / visits[i];
            int myDist     = myDistAfter(state, candidates.get(i));
            int oppDist    = oppDistAfter(state, candidates.get(i));
            boolean better = rate > bestRate
                || (rate == bestRate && myDist < bestMyDist)
                || (rate == bestRate && myDist == bestMyDist && oppDist > bestOppDist);
            if (better) {
                best        = i;
                bestRate    = rate;
                bestMyDist  = myDist;
                bestOppDist = oppDist;
            }
        }

        Move chosen = candidates.get(best);

        // Anti-oscillation: override if chosen move returns to previous position
        if (chosen instanceof PawnMove pm && pm.target().equals(prevPosition)) {
            Move bfsAdvance = bfsBestAdvance(state);
            if (bfsAdvance != null) chosen = bfsAdvance;
        }

        prevPosition = myPos;
        return chosen;
    }

    private int selectUCB1(int[] wins, int[] visits, int total, int n) {
        for (int i = 0; i < n; i++) { if (visits[i] == 0) return i; }
        double best    = Double.NEGATIVE_INFINITY;
        int    bestIdx = 0;
        double lnTotal = Math.log(total);
        for (int i = 0; i < n; i++) {
            double score = (double) wins[i] / visits[i]
                         + UCB_C * Math.sqrt(lnTotal / visits[i]);
            if (score > best) { best = score; bestIdx = i; }
        }
        return bestIdx;
    }

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

        if (state.getWallCount(current) > 0 && random.nextFloat() < WALL_PROB) {
            List<WallMove> walls = validator.getLegalWallMoves(state);
            if (!walls.isEmpty()) {
                int oppDist    = pathChecker.shortestPathWithJumps(state, opp);
                WallMove best  = null;
                int bestImpact = 0;
                int samples    = Math.min(WALL_SAMPLES, walls.size());
                for (int i = 0; i < samples; i++) {
                    WallMove wm = walls.get(random.nextInt(walls.size()));
                    int impact = pathChecker.shortestPathWithJumps(state.withWallMove(wm.wall()), opp) - oppDist;
                    if (impact > bestImpact) { bestImpact = impact; best = wm; }
                }
                if (best != null && bestImpact > 0) return state.withWallMove(best.wall());
            }
        }

        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) return state;
        int goalRow    = current.goalRow();
        PawnMove best  = pawns.get(0);
        int bestDist   = Math.abs(best.target().row() - goalRow);
        for (PawnMove pm : pawns) {
            int d = Math.abs(pm.target().row() - goalRow);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return state.withPawnMove(best.target());
    }

    private List<Move> buildCandidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        int oppDist    = pathChecker.shortestPathWithJumps(state, opp);

        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));

        if (state.getWallCount(current) > 0) {
            Position p1 = state.getPawnPosition(Player.ONE);
            Position p2 = state.getPawnPosition(Player.TWO);
            List<WallMove> legalWalls = validator.getLegalWallMoves(state);
            List<int[]> impacts = new ArrayList<>();
            for (int i = 0; i < legalWalls.size(); i++) {
                Wall w = legalWalls.get(i).wall();
                if (!nearEither(w, p1, p2)) continue;
                int impact = pathChecker.shortestPathWithJumps(state.withWallMove(w), opp) - oppDist;
                if (impact >= MIN_WALL_IMPACT) impacts.add(new int[]{impact, i});
            }
            impacts.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
            int limit = Math.min(impacts.size(), MAX_WALL_CANDIDATES);
            for (int k = 0; k < limit; k++) moves.add(legalWalls.get(impacts.get(k)[1]));
        }
        return moves;
    }

    private int myDistAfter(GameState state, Move move) {
        return pathChecker.shortestPathWithJumps(apply(state, move), aiPlayer);
    }

    private int oppDistAfter(GameState state, Move move) {
        return pathChecker.shortestPathWithJumps(apply(state, move), aiPlayer.opponent());
    }

    private Move bfsBestAdvance(GameState state) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) return null;
        int best = Integer.MAX_VALUE;
        PawnMove bestMove = null;
        for (PawnMove pm : pawns) {
            if (!pm.target().equals(prevPosition)) {
                int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), aiPlayer);
                if (d < best) { best = d; bestMove = pm; }
            }
        }
        return bestMove;
    }

    private boolean hasWon(GameState state, Player player) {
        return state.getPawnPosition(player).row() == player.goalRow();
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

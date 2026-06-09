package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Strict tempo accounting — never spends a wall unless it buys at least 2 moves.
 *
 * <p>A wall placement costs exactly 1 tempo (your turn). For it to be worth playing,
 * the wall must add ≥ 2 moves to the opponent's path, yielding a net tempo gain of ≥ 1.
 * Walls with impact 0 or 1 are discarded entirely; they either stall the position or
 * break exactly even, giving nothing back.
 *
 * <p>When ahead: advance without walls (preserve wall stock for when it's actually needed).
 * When tied or behind: search for the most tempo-efficient wall (highest impact, ≥ 2).
 * If no qualifying wall exists, advance anyway — wasting a turn on a cheap block only
 * deepens the deficit.
 */
public class TempoStrategy implements Strategy {

    private static final int MIN_WALL_IMPACT = 2;

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public TempoStrategy(Player aiPlayer) { this.aiPlayer = aiPlayer; }

    @Override public String displayName() { return "Tempo"; }
    @Override public String description() {
        return "Only spends a wall when it nets at least 2 opponent tempos — never wastes a turn on a cheap block";
    }

    @Override
    public Move decide(GameState state) {
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());

        // Winning the race: advance, save walls for when the lead narrows
        if (myDist < oppDist) return bestAdvance(state, myDist);

        // Tied or losing: only commit a wall if the payoff is ≥ MIN_WALL_IMPACT
        if (state.getWallCount(aiPlayer) > 0) {
            WallMove wall = bestTempoWall(state, myDist, oppDist);
            if (wall != null) return wall;
        }

        return bestAdvance(state, myDist);
    }

    /**
     * Best wall that adds ≥ MIN_WALL_IMPACT to opponent's path while not increasing
     * own path. Returns null if no such wall exists.
     */
    private WallMove bestTempoWall(GameState state, int myDist, int oppDist) {
        List<WallMove> walls = validator.getLegalWallMoves(state);
        WallMove best      = null;
        int      bestImpact = MIN_WALL_IMPACT - 1;

        for (WallMove wm : walls) {
            GameState after = state.withWallMove(wm.wall());
            // Reject walls that hurt our own path
            if (pathChecker.shortestPathWithJumps(after, aiPlayer) > myDist) continue;
            int impact = pathChecker.shortestPathWithJumps(after, aiPlayer.opponent()) - oppDist;
            if (impact > bestImpact) { bestImpact = impact; best = wm; }
        }
        return best;
    }

    private Move bestAdvance(GameState state, int myDist) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");
        PawnMove best     = pawns.get(0);
        int      bestDist = myDist;
        for (PawnMove pm : pawns) {
            int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), aiPlayer);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return best;
    }
}

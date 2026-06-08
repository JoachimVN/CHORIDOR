package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Priority-ordered pattern rules — no search, no rollouts.
 *
 * <p>Each turn evaluates a fixed threat hierarchy and executes the first rule that fires:
 * <ol>
 *   <li><b>Win now</b> — if a pawn move reaches the goal row, take it.</li>
 *   <li><b>Emergency block</b> — if the opponent is 1 step from winning and walls remain,
 *       place the highest-impact wall.</li>
 *   <li><b>Clear lead</b> — if 2+ steps ahead in the race, advance (don't waste a wall).</li>
 *   <li><b>Opponent closing in</b> — if opponent is within 2 steps and walls remain, block.</li>
 *   <li><b>Losing the race</b> — if behind, place the best blocking wall.</li>
 *   <li><b>Default</b> — advance the pawn by BFS.</li>
 * </ol>
 *
 * <p>The strategy plays intuitively and is fully transparent, but has no forward planning:
 * it can't see traps or forced wins beyond the current position.
 */
public class ThreatResponderStrategy implements Strategy {

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public ThreatResponderStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Threat Responder"; }
    @Override public String description() {
        return "Priority rules: win now → emergency block → sprint lead → block threat → default advance";
    }

    @Override
    public Move decide(GameState state) {
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");

        // 1. Instant win
        for (PawnMove pm : pawns) {
            if (pm.target().row() == aiPlayer.goalRow()) return pm;
        }

        // 2. Emergency block (opponent 1 step from winning)
        if (oppDist == 1 && state.getWallCount(aiPlayer) > 0) {
            Move wall = bestBlockingWall(state, oppDist);
            if (wall != null) return wall;
        }

        // 3. Clear race lead — just run, don't waste walls
        if (myDist <= oppDist - 2) return bestPawnAdvance(state, myDist, pawns);

        // 4. Opponent closing in (within 2 steps) — block
        if (oppDist <= 2 && state.getWallCount(aiPlayer) > 0) {
            Move wall = bestBlockingWall(state, oppDist);
            if (wall != null) return wall;
        }

        // 5. Losing the race — place the best wall
        if (myDist > oppDist && state.getWallCount(aiPlayer) > 0) {
            Move wall = bestBlockingWall(state, oppDist);
            if (wall != null) return wall;
        }

        // Default: advance
        return bestPawnAdvance(state, myDist, pawns);
    }

    private Move bestPawnAdvance(GameState state, int currentDist, List<PawnMove> pawns) {
        PawnMove best     = pawns.get(0);
        int      bestDist = currentDist;
        for (PawnMove pm : pawns) {
            int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), aiPlayer);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return best;
    }

    private Move bestBlockingWall(GameState state, int currentOppDist) {
        List<WallMove> walls = validator.getLegalWallMoves(state);
        WallMove best      = null;
        int      bestImpact = 0;
        for (WallMove wm : walls) {
            int impact = pathChecker.shortestPathWithJumps(
                state.withWallMove(wm.wall()), aiPlayer.opponent()) - currentOppDist;
            if (impact > bestImpact) { bestImpact = impact; best = wm; }
        }
        return best;
    }
}

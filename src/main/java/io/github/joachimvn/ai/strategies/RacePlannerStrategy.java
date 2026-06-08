package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Pure rule-based strategy driven entirely by the race state — no tree search.
 *
 * <p>On each turn: compute both players' BFS goal distances and decide with a single rule:
 * <ul>
 *   <li>If I am winning (or tied in) the pure pawn race → advance along the BFS path.</li>
 *   <li>If I am losing the race → place the wall that maximally extends the opponent's
 *       BFS distance. If no wall helps, advance anyway.</li>
 * </ul>
 *
 * <p>The logic mirrors how a strong human player thinks about Quoridor at a high level:
 * "Am I winning the sprint? Then don't waste a wall. Am I losing? Block."
 * The absence of lookahead makes it fast and transparent but exploitable by opponents
 * who can bait it into premature walls.
 */
public class RacePlannerStrategy implements Strategy {

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public RacePlannerStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Race Planner"; }
    @Override public String description() {
        return "Advances when winning the sprint, places the best blocking wall when losing — no search";
    }

    @Override
    public Move decide(GameState state) {
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());

        // Winning or tied → just run
        if (myDist <= oppDist) return bestPawnAdvance(state, myDist);

        // Losing → try the best blocking wall
        if (state.getWallCount(aiPlayer) > 0) {
            Move wall = bestBlockingWall(state, oppDist);
            if (wall != null) return wall;
        }

        // No walls or no impactful wall available → run
        return bestPawnAdvance(state, myDist);
    }

    private Move bestPawnAdvance(GameState state, int currentDist) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");
        PawnMove best    = pawns.get(0);
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
        return best; // null if no wall adds any distance
    }
}

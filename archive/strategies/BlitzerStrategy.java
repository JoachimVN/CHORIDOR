package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Wall-heavy opener that switches to a sprint the moment its wall supply runs low.
 * Unlike Adaptive (which phases on distance), Blitzer phases on its own wall count:
 * while it holds ≥ {@value #WALL_THRESHOLD} walls it blocks hard; once it drops below
 * that threshold it abandons walls entirely and races for the goal.
 */
public class BlitzerStrategy extends AbstractPrunedSearchStrategy {

    private static final int WALL_THRESHOLD = 5;

    private final Player aiPlayer;

    public BlitzerStrategy(Player aiPlayer) {
        super(aiPlayer);
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Blitzer"; }
    @Override public String description() {
        return "Opens with an aggressive wall barrage, then abandons walls and sprints once running low";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return oppDist - myDist; // fallback; overridden below
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return state.getWallCount(aiPlayer) >= WALL_THRESHOLD
            ? 3 * oppDist - myDist               // loaded: block hard
            : oppDist - 4 * myDist + wallAdvantage(state); // empty: sprint
    }
}

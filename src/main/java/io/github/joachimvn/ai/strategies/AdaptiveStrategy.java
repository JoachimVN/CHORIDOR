package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Wall-pruned deep search that switches stance by game phase: while ahead in the race it
 * defends the lead by blocking (Tactical-like), and once behind it abandons walls and
 * sprints for the goal (Rusher-like). A wall-reserve tiebreak rounds out the evaluation.
 */
public class AdaptiveStrategy extends AbstractPrunedSearchStrategy {

    public AdaptiveStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Adaptive"; }
    @Override public String description()   { return "Defends its lead by blocking when ahead, then races flat-out when behind"; }

    @Override
    protected int score(int myDist, int oppDist) {
        return myDist <= oppDist
            ? 2 * oppDist - myDist   // ahead or level: protect the lead by hindering the opponent
            : oppDist - 3 * myDist;  // behind: drop the walls and run
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist) + wallAdvantage(state);
    }
}

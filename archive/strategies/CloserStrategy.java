package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Endgame specialist that shifts heuristic weight based on its own BFS distance to goal.
 *
 * <p>In the opening (myDist &gt; 6) it plays a patient balanced game. As it closes in
 * (myDist ≤ 6) it accelerates, weighting its own advancement more heavily. In the final
 * stretch (myDist ≤ 3) it goes all-out, maximising the evaluation gap to the exclusion
 * of almost everything else.
 */
public class CloserStrategy extends AbstractPrunedSearchStrategy {

    public CloserStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Closer"; }
    @Override public String description() {
        return "Plays patiently through the opening, then shifts to high-urgency mode when close to the goal";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return oppDist - myDist;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        if (myDist <= 3) return 6 * oppDist - 2 * myDist;           // final stretch: all-out
        if (myDist <= 6) return 3 * oppDist - 2 * myDist;           // approach: accelerate
        return oppDist - myDist + wallAdvantage(state);               // opening: patient
    }
}

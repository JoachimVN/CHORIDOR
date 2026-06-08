package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Wall-pruned deep search that treats walls as free to spend. Unlike Tactical and Balanced,
 * there is no wall-reserve penalty in the evaluation, so the search will happily place a wall
 * whenever it lengthens the opponent's path — even by a single step — without discounting the
 * cost of losing a wall from reserve. The result is the most wall-aggressive search-based AI.
 */
public class AggressorStrategy extends AbstractPrunedSearchStrategy {

    public AggressorStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Aggressor"; }
    @Override public String description() {
        return "Places walls freely without hoarding them — never passes up a chance to slow the opponent";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return 3 * oppDist - myDist;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist); // no wall-reserve penalty: walls spent at any opportunity
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Wall-pruned deep search with the proven balanced heuristic — race and block weighted
 * equally — plus a wall-reserve tiebreak. Strongest all-rounder: it has Minimax's even
 * temperament but searches as deep as Tactical and Rusher.
 */
public class BalancedStrategy extends AbstractPrunedSearchStrategy {

    public BalancedStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Balanced"; }
    @Override public String description()   { return "Deep search weighting advancing and blocking equally, keeping walls in reserve as a tiebreak"; }

    @Override
    protected int score(int myDist, int oppDist) {
        return 3 * (oppDist - myDist);
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        // Distance dominates; the wall edge only breaks ties between equal-distance lines.
        return score(myDist, oppDist) + wallAdvantage(state);
    }
}

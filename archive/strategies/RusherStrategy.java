package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.Player;

/** Same wall-pruned deep search as Tactical, but weights own advancement 3x over blocking. */
public class RusherStrategy extends AbstractPrunedSearchStrategy {

    public RusherStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Rusher"; }
    @Override public String description()   { return "Same deep search as Tactical, but races toward the goal rather than blocking"; }

    @Override
    protected int score(int myDist, int oppDist) {
        return oppDist - 3 * myDist;
    }
}

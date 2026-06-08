package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.Player;

/** Wall-pruned deep search that weights blocking the opponent twice as much as advancing. */
public class TacticalStrategy extends AbstractPrunedSearchStrategy {

    public TacticalStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Tactical"; }
    @Override public String description()   { return "Prunes the wall search space to go much deeper, prioritises blocking over advancing"; }

    @Override
    protected int score(int myDist, int oppDist) {
        return 2 * oppDist - myDist;
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Wall-pruned deep search that plays the wall economy: it values its wall reserve heavily,
 * so it only spends a wall when the deep search shows the resulting distance swing is worth
 * more than keeping the wall in hand. The result is a frugal hoarder that saves its walls
 * for high-impact blocks late in the game.
 */
public class EconomistStrategy extends AbstractPrunedSearchStrategy {

    private static final int WALL_VALUE = 3;

    public EconomistStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Economist"; }
    @Override public String description()   { return "Hoards its walls, spending them only when the search shows a big enough payoff"; }

    @Override
    protected int score(int myDist, int oppDist) {
        return 2 * (oppDist - myDist);
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        // A spent wall must buy at least WALL_VALUE/2 net distance to be worth it.
        return score(myDist, oppDist) + WALL_VALUE * wallAdvantage(state);
    }
}

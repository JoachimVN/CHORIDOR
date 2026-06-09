package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

/**
 * Asymmetric path-width search: targets the opponent's routing options over raw distance.
 *
 * <p>Where {@code PathCount} maximises {@code (myWidth - oppWidth)}, Tactician specifically
 * penalises the opponent's path breadth with a much heavier coefficient. Funnelling the
 * opponent into a single corridor is worth more than widening your own, because a player
 * with one route can be sealed with a single well-placed wall.
 *
 * <p>This produces a distinctly different wall-placement style: Tactician prefers walls
 * that choke the opponent's options even when those walls don't immediately lengthen their
 * BFS distance, while PathCount would ignore such walls as zero-impact.
 */
public class TacticianStrategy extends AbstractPrunedSearchStrategy {

    public TacticianStrategy(Player aiPlayer) { super(aiPlayer); }

    @Override public String displayName() { return "Tactician"; }
    @Override public String description() {
        return "Chokes opponent routing options: funnels them into a single corridor even at zero distance cost";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return (oppDist - myDist) * 10;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        int myWidth  = PathWidth.dagWidth(state, aiPlayer());
        int oppWidth = PathWidth.dagWidth(state, aiPlayer().opponent());
        // Heavily penalise opponent breadth; own breadth matters less than suppressing theirs
        return (oppDist - myDist) * 10
             - oppWidth * 6
             + myWidth  * 2
             + wallAdvantage(state);
    }
}

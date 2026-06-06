package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.Player;

/**
 * Iterative-deepening minimax over the full move set (all pawn moves and every legal
 * wall), scoring purely by the difference in BFS goal distances.
 */
public class MinimaxStrategy extends AbstractSearchStrategy {

    // Depth 2 = AI move + opponent response. Each extra ply multiplies work by the
    // (unpruned) branching factor of ~130, so the time budget caps effective depth.
    private static final int DEFAULT_DEPTH = 2;

    public MinimaxStrategy(Player aiPlayer, long timeLimitMs) {
        super(aiPlayer, timeLimitMs, DEFAULT_DEPTH);
    }

    @Override public String displayName() { return "Minimax"; }
    @Override public String description()   { return "Searches several moves ahead using minimax with alpha-beta pruning"; }

    @Override
    protected int score(int myDist, int oppDist) {
        return oppDist - myDist;
    }
}

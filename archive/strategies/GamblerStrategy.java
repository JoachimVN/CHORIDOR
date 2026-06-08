package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

/**
 * Non-linear evaluation that squares the distance gap, making the AI highly sensitive to
 * large leads or deficits while nearly indifferent to small imbalances.
 *
 * <p>The gap is clamped to ±18 before squaring so the non-terminal score never reaches
 * the terminal WIN sentinel (18² = 324 &lt; WIN). In practice the clamping rarely triggers —
 * realistic BFS gaps in Quoridor are well below 18 — but squaring those smaller gaps already
 * creates strong commitment: a lead of 4 is worth 16 (vs 4 linearly), while a lead of 1 is
 * worth only 1 (vs 4 linearly), pushing the AI to convert small edges into bigger ones.
 */
public class GamblerStrategy extends AbstractPrunedSearchStrategy {

    private static final int GAP_CAP = 18; // 18² = 324 < WIN

    public GamblerStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Gambler"; }
    @Override public String description() {
        return "Squares the distance gap: nearly indifferent to small imbalances, commits hard to any large lead";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        int gap = Math.max(-GAP_CAP, Math.min(GAP_CAP, oppDist - myDist));
        return gap * Math.abs(gap); // sign(gap) * gap²
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist) + wallAdvantage(state);
    }
}

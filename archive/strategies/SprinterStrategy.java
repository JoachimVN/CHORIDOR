package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure pawn racer — the AI never places a wall. At the opponent's turns in the minimax tree,
 * normal (pruned) wall candidates are still generated so the evaluation stays realistic.
 */
public class SprinterStrategy extends AbstractPrunedSearchStrategy {

    private final Player aiPlayer;

    public SprinterStrategy(Player aiPlayer) {
        super(aiPlayer);
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Sprinter"; }
    @Override public String description() {
        return "Never places walls — pure pawn racing backed by deep search";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return oppDist - 4 * myDist;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist); // no wall reserve needed: AI never places walls
    }

    @Override
    protected List<Move> candidates(GameState state) {
        if (state.getCurrentPlayer() == aiPlayer) {
            return new ArrayList<>(validator.getLegalPawnMoves(state));
        }
        return super.candidates(state); // opponent still uses walls in the search tree
    }
}

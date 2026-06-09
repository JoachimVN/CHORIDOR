package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.Comparator;

public class GreedyStrategy implements Strategy {

    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    @Override public String displayName() { return "Sprinter"; }
    @Override public String description()   { return "Always advances toward the goal, never places walls"; }

    @Override
    public Move decide(GameState state) {
        Player me = state.getCurrentPlayer();
        return validator.getLegalPawnMoves(state).stream()
            .min(Comparator.comparingInt(m ->
                pathChecker.shortestPathWithJumps(state.withPawnMove(m.target()), me)))
            .orElseThrow();
    }
}

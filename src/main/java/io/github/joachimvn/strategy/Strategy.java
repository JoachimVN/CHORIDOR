package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Move;

public interface Strategy {
    Move decide(GameState state);

    default String name() {
        return getClass().getSimpleName();
    }
}

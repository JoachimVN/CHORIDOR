package io.github.joachimvn.ai;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Move;

public interface Strategy {
    Move decide(GameState state);
    String displayName();
    String description();
}

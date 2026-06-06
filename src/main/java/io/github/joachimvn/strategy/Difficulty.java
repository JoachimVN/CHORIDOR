package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.Player;

public enum Difficulty {

    EASY  ("Easy",   player -> new RandomStrategy()),
    MEDIUM("Medium", player -> new MinimaxStrategy(player, 300)),
    HARD  ("Hard",   player -> new MinimaxStrategy(player, 1000));

    private final String                          displayName;
    private final java.util.function.Function<Player, Strategy> factory;

    Difficulty(String displayName, java.util.function.Function<Player, Strategy> factory) {
        this.displayName = displayName;
        this.factory     = factory;
    }

    public String   displayName()              { return displayName; }
    public Strategy createStrategy(Player p)   { return factory.apply(p); }
}

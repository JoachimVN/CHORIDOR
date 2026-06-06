package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.Player;

import java.util.function.Function;

public enum Difficulty {

    RANDOM ("Random",  p -> new RandomStrategy()),
    GREEDY ("Greedy",  p -> new GreedyStrategy()),
    MINIMAX("Minimax", p -> new MinimaxStrategy(p, 1000));

    private final String              displayName;
    private final Function<Player, Strategy> factory;

    Difficulty(String displayName, Function<Player, Strategy> factory) {
        this.displayName = displayName;
        this.factory     = factory;
    }

    public String   displayName()            { return displayName; }
    public Strategy createStrategy(Player p) { return factory.apply(p); }
}

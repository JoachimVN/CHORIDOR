package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.Player;

import java.util.function.Function;

public enum Difficulty {

    RANDOM ("Random",  "Makes completely random moves, never plans ahead",
            p -> new RandomStrategy()),

    GREEDY ("Greedy",  "Always advances toward the goal, never places walls",
            p -> new GreedyStrategy()),

    MINIMAX("Minimax", "Searches several moves ahead using minimax with alpha-beta pruning",
            p -> new MinimaxStrategy(p, 1000));

    private final String              displayName;
    private final String              description;
    private final Function<Player, Strategy> factory;

    Difficulty(String displayName, String description, Function<Player, Strategy> factory) {
        this.displayName = displayName;
        this.description = description;
        this.factory     = factory;
    }

    public String   displayName()            { return displayName; }
    public String   description()            { return description; }
    public Strategy createStrategy(Player p) { return factory.apply(p); }
}

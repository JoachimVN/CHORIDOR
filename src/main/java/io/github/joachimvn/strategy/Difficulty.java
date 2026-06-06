package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.Player;

import java.util.function.Function;

public enum Difficulty {

    RANDOM  ("Random",   "Makes completely random moves, never plans ahead",
             p -> new RandomStrategy()),

    GREEDY  ("Greedy",   "Always advances toward the goal, never places walls",
             p -> new GreedyStrategy()),

    MINIMAX ("Minimax",  "Searches several moves ahead using minimax with alpha-beta pruning",
             p -> new MinimaxStrategy(p, 1000)),

    TACTICAL("Tactical", "Prunes the wall search space to go much deeper — prioritises blocking over advancing",
             p -> new TacticalStrategy(p)),

    RUSHER  ("Rusher",   "Same deep search as Tactical, but races toward the goal rather than blocking",
             p -> new RusherStrategy(p));

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

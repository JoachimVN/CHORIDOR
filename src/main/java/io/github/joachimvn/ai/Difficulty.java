package io.github.joachimvn.ai;

import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ai.*;

import java.util.function.Function;

public enum Difficulty {

    RANDOM  (p -> new RandomStrategy()),
    GREEDY  (p -> new GreedyStrategy()),
    MINIMAX (p -> new MinimaxStrategy(p, 1000)),
    TACTICAL(p -> new TacticalStrategy(p)),
    RUSHER  (p -> new RusherStrategy(p));

    private final Function<Player, Strategy> factory;
    private final String displayName;
    private final String description;

    Difficulty(Function<Player, Strategy> factory) {
        this.factory = factory;
        Strategy sample = factory.apply(Player.ONE);
        this.displayName = sample.displayName();
        this.description = sample.description();
    }

    public String   displayName()            { return displayName; }
    public String   description()            { return description; }
    public Strategy createStrategy(Player p) { return factory.apply(p); }
}

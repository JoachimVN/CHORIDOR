package io.github.joachimvn.ai;

import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ai.strategies.*;

import java.util.function.Function;

public enum Difficulty {

    RANDOM   (p -> new RandomStrategy()),
    GREEDY   (p -> new GreedyStrategy()),
    MINIMAX  (p -> new MinimaxStrategy(p, 1000)),
    TACTICAL (TacticalStrategy::new),
    RUSHER   (RusherStrategy::new),
    BALANCED (BalancedStrategy::new),
    ADAPTIVE (AdaptiveStrategy::new),
    ECONOMIST(EconomistStrategy::new),
    SHARP    (SharpStrategy::new),
    SPRINTER (SprinterStrategy::new),
    BLITZER  (BlitzerStrategy::new),
    AGGRESSOR(AggressorStrategy::new),
    TRAPPER  (TrapperStrategy::new),
    CLOSER   (CloserStrategy::new),
    SNIPER      (SniperStrategy::new),
    GAMBLER     (GamblerStrategy::new),
    MONTE_CARLO (MonteCarloStrategy::new),
    ONE_STEP    (OneStepStrategy::new),
    WALL_DUMPER (WallDumperStrategy::new),
    COPYCAT     (CopycatStrategy::new);

    private final Function<Player, Strategy> factory;
    private final Strategy sample;

    Difficulty(Function<Player, Strategy> factory) {
        this.factory = factory;
        this.sample  = factory.apply(Player.ONE);
    }

    /** A representative instance — source of truth for name and description. */
    public Strategy sample()                 { return sample; }
    public Strategy createStrategy(Player p) { return factory.apply(p); }
}

package io.github.joachimvn.ai;

import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ai.strategies.*;

import java.util.function.Function;

public enum Difficulty {

    RANDOM          (p -> new RandomStrategy()),
    SPRINTER        (p -> new GreedyStrategy()),
    MINIMAX         (p -> new MinimaxStrategy(p, 1000)),
    SHARP           (SharpStrategy::new),
    TRAPPER         (TrapperStrategy::new),
    COPYCAT         (CopycatStrategy::new),
    SHORT_SIGHTED   (OneStepStrategy::new),
    FORTNITE        (WallDumperStrategy::new),
    MONTE_CARLO     (MonteCarloStrategy::new),
    COMEBACKER      (RacePlannerStrategy::new),
    THREAT_RESPONDER(ThreatResponderStrategy::new),
    PATH_COUNT      (PathCountStrategy::new),
    BAITER          (BaiterStrategy::new),
    WIKIPEDIA       (WikipediaStrategy::new),
    CORRIDOR        (CorridorStrategy::new),
    FORKER          (DualThreatStrategy::new);

    private final Function<Player, Strategy> factory;
    private final Strategy sample;

    Difficulty(Function<Player, Strategy> factory) {
        this.factory = factory;
        this.sample  = factory.apply(Player.ONE);
    }

    /** A representative instance — source of truth for name and description. */
    public Strategy sample()                 { return sample; }
    public Strategy createStrategy(Player p) { return factory.apply(p); }

    /**
     * The wall-clock time budget this strategy is allowed per decision, in milliseconds.
     * Returns 0 for fast heuristics that have no explicit budget.
     */
    public long timeBudgetMs() {
        return switch (this) {
            case MONTE_CARLO                                               -> 950;
            case MINIMAX, SHARP, TRAPPER, BAITER,
                 WIKIPEDIA, PATH_COUNT                                     -> 1000;
            default                                                        -> 0;
        };
    }

    /**
     * Relative skill tier (1 = weakest, 5 = strongest).
     * Used to estimate game length: closely matched = longer game; large gap = short decisive game.
     */
    public int skillLevel() {
        return switch (this) {
            case RANDOM                                                    -> 1;
            case SPRINTER, SHORT_SIGHTED, FORTNITE, COPYCAT               -> 2;
            case COMEBACKER, THREAT_RESPONDER, CORRIDOR                   -> 3;
            case FORKER                                                    -> 4;
            case MINIMAX, SHARP, TRAPPER, BAITER,
                 PATH_COUNT, WIKIPEDIA, MONTE_CARLO                       -> 5;
        };
    }
}

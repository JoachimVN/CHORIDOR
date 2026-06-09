package io.github.joachimvn.ai;

import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ai.strategies.*;

import java.util.function.Function;

public enum Difficulty {

    RANDOM          (p -> new RandomStrategy()),
    GREEDY          (p -> new GreedyStrategy()),
    MINIMAX         (p -> new MinimaxStrategy(p, 1000)),
    ECONOMIST       (EconomistStrategy::new),
    SHARP           (SharpStrategy::new),
    TRAPPER         (TrapperStrategy::new),
    COPYCAT         (CopycatStrategy::new),
    ONE_STEP        (OneStepStrategy::new),
    WALL_DUMPER     (WallDumperStrategy::new),
    MONTE_CARLO     (MonteCarloStrategy::new),
    RACE_PLANNER    (RacePlannerStrategy::new),
    THREAT_RESPONDER(ThreatResponderStrategy::new),
    PATH_COUNT      (PathCountStrategy::new),
    BAITER          (BaiterStrategy::new),
    WIKI            (WikipediaStrategy::new),
    CORRIDOR        (CorridorStrategy::new),
    TEMPO           (TempoStrategy::new),
    DUAL_THREAT     (DualThreatStrategy::new),
    INFLUENCE       (InfluenceStrategy::new),
    ZUGZWANG        (ZugzwangStrategy::new);

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
            case MINIMAX, ECONOMIST, SHARP, TRAPPER, BAITER,
                 WIKI, PATH_COUNT                                          -> 1000;
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
            case GREEDY, ONE_STEP, WALL_DUMPER, COPYCAT                   -> 2;
            case RACE_PLANNER, THREAT_RESPONDER, CORRIDOR                 -> 3;
            case TEMPO, DUAL_THREAT, INFLUENCE, ZUGZWANG                  -> 4;
            case MINIMAX, ECONOMIST, SHARP, TRAPPER, BAITER,
                 PATH_COUNT, WIKI, MONTE_CARLO                            -> 5;
        };
    }
}

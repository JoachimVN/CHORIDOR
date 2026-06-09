package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Ensemble strategy: polls four fast heuristics and picks the most-voted move.
 * When multiple moves tie in votes, the one that minimises BFS distance to goal wins.
 *
 * <p>The panel covers complementary concerns — greedy advance, sprint planning,
 * threat response, and corridor control — so the consensus move tends to be
 * solid in all dimensions without excelling at any single one.
 */
public class PortfolioStrategy implements Strategy {

    private final Strategy[] panel;
    private final PathChecker pathChecker = new PathChecker();

    public PortfolioStrategy(Player aiPlayer) {
        panel = new Strategy[]{
            new GreedyStrategy(),
            new RacePlannerStrategy(aiPlayer),
            new ThreatResponderStrategy(aiPlayer),
            new CorridorStrategy(aiPlayer)
        };
    }

    @Override public String displayName() { return "Portfolio"; }
    @Override public String description() {
        return "Polls four fast heuristics and picks the most-voted move; ties broken by BFS";
    }

    @Override
    public Move decide(GameState state) {
        Map<Move, Integer> votes = new LinkedHashMap<>();
        for (Strategy s : panel)
            votes.merge(s.decide(state), 1, Integer::sum);

        int max = votes.values().stream().mapToInt(i -> i).max().orElse(0);
        Player current = state.getCurrentPlayer();
        return votes.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .min(Comparator.comparingInt(m -> distAfter(state, m, current)))
                .orElse(panel[0].decide(state));
    }

    private int distAfter(GameState state, Move m, Player p) {
        GameState next = switch (m) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
        return pathChecker.shortestPathWithJumps(next, p);
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategyTest {
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    private void assertLegal(GameState state, Move move) {
        switch (move) {
            case PawnMove pm -> assertTrue(validator.getLegalPawnMoves(state).contains(pm),
                () -> "illegal pawn move: " + pm);
            case WallMove wm -> assertTrue(validator.isWallLegal(state, wm.wall()),
                () -> "illegal wall move: " + wm);
        }
    }

    @Test
    void randomStrategyIsDeterministicWithSeed() {
        GameState state = new GameState();
        Move a = new RandomStrategy(42).decide(state);
        Move b = new RandomStrategy(42).decide(state);
        assertEquals(a, b, "same seed must yield the same move");
        assertLegal(state, a);
    }

    @Test
    void greedyAdvancesTowardGoal() {
        GameState state = new GameState();
        Strategy greedy = new GreedyStrategy();
        Move move = greedy.decide(state);
        assertInstanceOf(PawnMove.class, move, "greedy never places walls");
        assertLegal(state, move);

        int before = pathChecker.shortestPath(state, Player.ONE);
        int after  = pathChecker.shortestPath(state.withPawnMove(((PawnMove) move).target()), Player.ONE);
        assertTrue(after < before, "greedy must reduce its distance to goal");
    }

    @Test
    void searchStrategiesReturnLegalMoves() {
        GameState state = new GameState();
        // Short budget keeps Minimax snappy; the pruned strategies use their own 1s cap.
        assertLegal(state, new MinimaxStrategy(Player.ONE, 150).decide(state));
        assertLegal(state, new TacticalStrategy(Player.ONE).decide(state));
        assertLegal(state, new RusherStrategy(Player.ONE).decide(state));
    }
}

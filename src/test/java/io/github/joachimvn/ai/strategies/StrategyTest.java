package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.GameEngine;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertLegal(state, new SharpStrategy(Player.ONE).decide(state));
        assertLegal(state, new TrapperStrategy(Player.ONE).decide(state));
        assertLegal(state, new PathCountStrategy(Player.ONE).decide(state));
        assertLegal(state, new BaiterStrategy(Player.ONE).decide(state));
        assertLegal(state, new WikipediaStrategy(Player.ONE).decide(state));
    }

    @Test
    void searchStrategiesFinishInsteadOfDawdling() {
        // Player.ONE one step from its goal row (0), opponent far away and not blocking. The
        // search must take the winning step rather than shuffle around an equal-"WIN" plateau.
        GameState state = new GameState()
            .withPawnMove(new Position(1, 4))   // ONE -> (1,4); TWO to move
            .withPawnMove(new Position(4, 0));  // TWO -> (4,0); back to ONE
        assertEquals(Player.ONE, state.getCurrentPlayer());
        assertEquals(1, pathChecker.shortestPath(state, Player.ONE), "ONE should be one step from goal");

        List<Strategy> strategies = List.of(
            new MinimaxStrategy(Player.ONE, 200),
            new SharpStrategy(Player.ONE),
            new TrapperStrategy(Player.ONE),
            new PathCountStrategy(Player.ONE),
            new BaiterStrategy(Player.ONE),
            new WikipediaStrategy(Player.ONE));

        for (Strategy s : strategies) {
            Move move = s.decide(state);
            assertLegal(state, move);
            assertInstanceOf(PawnMove.class, move,
                () -> s.displayName() + " must take the winning step, not place a wall");
            int after = pathChecker.shortestPath(state.withPawnMove(((PawnMove) move).target()), Player.ONE);
            assertEquals(0, after, () -> s.displayName() + " must step onto the goal to win, not dawdle");
        }
    }

    @Test
    void searchMirrorMatchTerminates() {
        // Two equal search AIs must actually play out a game, not stall forever shuffling pawns
        // or dumping walls. A short time budget keeps the test quick while exercising the shared
        // search (depth-to-win bias, progress tie-break, wall-reserve cost).
        GameEngine engine = new GameEngine();
        Strategy one = new MinimaxStrategy(Player.ONE, 50);
        Strategy two = new MinimaxStrategy(Player.TWO, 50);

        GameState state = new GameState();
        int ply = 0;
        while (!engine.isGameOver(state) && ply < 300) {
            Strategy mover = state.getCurrentPlayer() == Player.ONE ? one : two;
            state = engine.applyMove(state, mover.decide(state));
            ply++;
        }
        assertTrue(engine.isGameOver(state),
            "AI vs AI must finish rather than stall; stopped after " + ply + " plies");
    }
}

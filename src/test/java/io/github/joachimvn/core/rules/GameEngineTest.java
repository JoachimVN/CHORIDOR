package io.github.joachimvn.core.rules;

import io.github.joachimvn.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;
import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {
    private final GameEngine engine = new GameEngine();
    private final MoveValidator validator = new MoveValidator();

    @Test
    void initialState() {
        GameState state = new GameState();
        assertEquals(new Position(8, 4), state.getPawnPosition(Player.ONE));
        assertEquals(new Position(0, 4), state.getPawnPosition(Player.TWO));
        assertEquals(Player.ONE, state.getCurrentPlayer());
        assertEquals(10, state.getWallCount(Player.ONE));
        assertFalse(engine.isGameOver(state));
    }

    @Test
    void applyPawnMoveUpdatesPositionAndSwitchesPlayer() {
        GameState state = new GameState();
        GameState next = engine.applyMove(state, new PawnMove(new Position(7, 4)));
        assertEquals(new Position(7, 4), next.getPawnPosition(Player.ONE));
        assertEquals(Player.TWO, next.getCurrentPlayer());
    }

    @Test
    void horizontalWallBlocksCorrectEdges() {
        GameState state = new GameState();
        // H wall at (7,3): blocks vertical movement at cols 3 and 4 across the row 7↔8 boundary
        GameState walled = state.withWallMove(new Wall(HORIZONTAL, 7, 3));
        assertTrue(walled.isEdgeBlocked(new Position(8, 3), new Position(7, 3)));
        assertTrue(walled.isEdgeBlocked(new Position(8, 4), new Position(7, 4)));
        assertFalse(walled.isEdgeBlocked(new Position(8, 2), new Position(7, 2)));
        assertFalse(walled.isEdgeBlocked(new Position(8, 5), new Position(7, 5)));
    }

    @Test
    void verticalWallBlocksCorrectEdges() {
        GameState state = new GameState();
        // V wall at (7,3): blocks horizontal movement at rows 7 and 8 across the col 3↔4 boundary
        GameState walled = state.withWallMove(new Wall(VERTICAL, 7, 3));
        assertTrue(walled.isEdgeBlocked(new Position(7, 3), new Position(7, 4)));
        assertTrue(walled.isEdgeBlocked(new Position(8, 3), new Position(8, 4)));
        assertFalse(walled.isEdgeBlocked(new Position(6, 3), new Position(6, 4)));
    }

    @Test
    void illegalPawnMoveThrows() {
        GameState state = new GameState();
        assertThrows(IllegalArgumentException.class,
            () -> engine.applyMove(state, new PawnMove(new Position(5, 5))));
    }

    @Test
    void winConditionDetectedOnGoalRow() {
        GameState state = new GameState();
        GameState winning = state.withPawnMove(new Position(0, 4));
        assertTrue(engine.isGameOver(winning));
        assertEquals(Optional.of(Player.ONE), engine.getWinner(winning));
    }

    @Test
    void initialLegalMovesAreCorrect() {
        GameState state = new GameState();
        List<PawnMove> moves = validator.getLegalPawnMoves(state);
        // ONE starts at (8,4): can go up, left, right but not down (out of bounds)
        assertTrue(moves.contains(new PawnMove(new Position(7, 4))));
        assertTrue(moves.contains(new PawnMove(new Position(8, 3))));
        assertTrue(moves.contains(new PawnMove(new Position(8, 5))));
        assertFalse(moves.contains(new PawnMove(new Position(9, 4))));
    }

    @Test
    void wallCompletingTrapIsIllegal() {
        // Move ONE to the bottom-left corner (bypassing move validation).
        // H(7,0) blocks upward movement at cols 0 and 1 from row 8.
        // V(7,1) would seal the col 1↔2 edge at rows 7–8, boxing ONE into
        // {(8,0),(8,1)} with no path to goal row 0 — must be rejected.
        GameState state = new GameState();
        state = state.withPawnMove(new Position(8, 0));                    // ONE→(8,0), TWO's turn
        state = state.withPawnMove(state.getPawnPosition(Player.TWO));     // TWO stays, ONE's turn
        state = state.withWallMove(new Wall(HORIZONTAL, 7, 0));            // ONE places H wall, TWO's turn
        state = state.withPawnMove(state.getPawnPosition(Player.TWO));     // TWO stays, ONE's turn

        assertFalse(validator.isWallLegal(state, new Wall(VERTICAL, 7, 1)));
        assertTrue(validator.isWallLegal(state,  new Wall(VERTICAL, 3, 3)));
    }
}

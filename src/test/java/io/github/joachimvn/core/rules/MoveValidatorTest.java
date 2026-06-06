package io.github.joachimvn.core.rules;

import io.github.joachimvn.core.model.*;
import org.junit.jupiter.api.Test;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;
import static org.junit.jupiter.api.Assertions.*;

class MoveValidatorTest {
    private final MoveValidator validator = new MoveValidator();

    /** Place pawns so that ONE (current player) is directly below TWO, with ONE to move. */
    private GameState adjacentVertical(Position one, Position two) {
        // withPawnMove ignores turn/legality and just relocates the current player,
        // so two calls (ONE then TWO) set both pawns and return the turn to ONE.
        return new GameState().withPawnMove(one).withPawnMove(two);
    }

    @Test
    void straightJumpOverOpponent() {
        // ONE(8,4), TWO(7,4): straight square (6,4) is open → only the jump is offered.
        GameState state = adjacentVertical(new Position(8, 4), new Position(7, 4));
        var moves = validator.getLegalPawnMoves(state);
        assertTrue(moves.contains(new PawnMove(new Position(6, 4))), "straight jump");
        assertFalse(moves.contains(new PawnMove(new Position(7, 4))), "cannot land on opponent");
    }

    @Test
    void diagonalJumpWhenStraightBlockedByWall() {
        // ONE(8,4), TWO(7,4), wall sealing the (7,4)-(6,4) edge → diagonals instead.
        GameState state = new GameState()
            .withWallMove(new Wall(HORIZONTAL, 6, 4)) // ONE places wall, turn → TWO
            .withPawnMove(new Position(7, 4));        // TWO → (7,4), turn → ONE
        var moves = validator.getLegalPawnMoves(state);
        assertTrue(moves.contains(new PawnMove(new Position(7, 3))), "left diagonal");
        assertTrue(moves.contains(new PawnMove(new Position(7, 5))), "right diagonal");
        assertFalse(moves.contains(new PawnMove(new Position(6, 4))), "straight blocked");
    }

    @Test
    void diagonalJumpWhenStraightBlockedByBoardEdge() {
        // ONE(1,4), TWO(0,4): straight-over (-1,4) is off-board → diagonals along row 0.
        GameState state = adjacentVertical(new Position(1, 4), new Position(0, 4));
        var moves = validator.getLegalPawnMoves(state);
        assertTrue(moves.contains(new PawnMove(new Position(0, 3))), "left diagonal");
        assertTrue(moves.contains(new PawnMove(new Position(0, 5))), "right diagonal");
        assertFalse(moves.contains(new PawnMove(new Position(0, 4))), "cannot land on opponent");
    }

    @Test
    void parallelAndCrossingWallsOverlap() {
        GameState state = new GameState().withWallMove(new Wall(HORIZONTAL, 4, 4));
        assertFalse(validator.isWallLegal(state, new Wall(HORIZONTAL, 4, 5)), "overlaps to the right");
        assertFalse(validator.isWallLegal(state, new Wall(HORIZONTAL, 4, 3)), "overlaps to the left");
        assertFalse(validator.isWallLegal(state, new Wall(VERTICAL,   4, 4)), "crosses at same anchor");
        assertTrue(validator.isWallLegal(state,  new Wall(HORIZONTAL, 4, 6)), "clear of the existing wall");
    }

    @Test
    void wallOutOfBoundsIsIllegal() {
        GameState state = new GameState();
        assertFalse(validator.isWallLegal(state, new Wall(HORIZONTAL, 8, 0)), "row past last anchor");
        assertFalse(validator.isWallLegal(state, new Wall(VERTICAL,   0, 8)), "col past last anchor");
        assertFalse(validator.isWallLegal(state, new Wall(HORIZONTAL, -1, 0)), "negative anchor");
    }

    @Test
    void initialBoardOffersEveryWallAnchor() {
        // 8x8 anchor grid x 2 orientations, none overlapping or path-sealing on an empty board.
        assertEquals(128, validator.getLegalWallMoves(new GameState()).size());
    }

    @Test
    void exhaustedWallSupplyForbidsWalls() {
        GameState state = new GameState();
        // Drain ONE's supply by repeatedly placing then passing the turn back.
        Wall[] tenWalls = new Wall[GameState.WALLS_PER_PLAYER];
        int placed = 0;
        for (int c = 0; c <= 6 && placed < tenWalls.length; c += 2) {
            for (int r = 0; r <= 6 && placed < tenWalls.length; r += 2) {
                tenWalls[placed++] = new Wall(HORIZONTAL, r, c);
            }
        }
        for (Wall w : tenWalls) {
            state = state.withWallMove(w);          // ONE places, turn → TWO
            state = state.withPawnMove(state.getPawnPosition(Player.TWO)); // TWO passes, turn → ONE
        }
        assertEquals(0, state.getWallCount(Player.ONE));
        assertFalse(validator.isWallLegal(state, new Wall(VERTICAL, 1, 1)), "no walls left to place");
        assertTrue(validator.getLegalWallMoves(state).isEmpty());
    }
}

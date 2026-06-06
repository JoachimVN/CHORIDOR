package io.github.joachimvn.core.rules;

import io.github.joachimvn.core.model.*;
import org.junit.jupiter.api.Test;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static org.junit.jupiter.api.Assertions.*;

class PathCheckerTest {
    private final PathChecker pathChecker = new PathChecker();

    @Test
    void initialDistancesSpanTheBoard() {
        GameState state = new GameState();
        assertEquals(8, pathChecker.shortestPath(state, Player.ONE));
        assertEquals(8, pathChecker.shortestPath(state, Player.TWO));
    }

    @Test
    void bothPlayersHavePathOnEmptyBoard() {
        assertTrue(pathChecker.bothPlayersHavePath(new GameState()));
    }

    @Test
    void distanceIsZeroOnGoalRow() {
        // TWO's goal row is 8; relocate it there and the distance collapses to 0.
        GameState state = new GameState()
            .withPawnMove(new Position(8, 4))  // ONE passes, turn → TWO
            .withPawnMove(new Position(8, 0)); // TWO → goal row, turn → ONE
        assertEquals(0, pathChecker.shortestPath(state, Player.TWO));
    }

    @Test
    void wallsForceALongerDetour() {
        // Seal the row 7↔8 boundary at columns 2-5, leaving column 6 as the nearest gap.
        // ONE must walk right to column 6 (2 steps) before climbing, lengthening 8 → 10.
        GameState state = new GameState()
            .withWallMove(new Wall(HORIZONTAL, 7, 4))  // blocks cols 4,5
            .withWallMove(new Wall(HORIZONTAL, 7, 2)); // blocks cols 2,3
        assertEquals(10, pathChecker.shortestPath(state, Player.ONE));
    }
}

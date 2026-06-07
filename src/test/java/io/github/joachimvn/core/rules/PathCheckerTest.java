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

    @Test
    void jumpAwareBfsShortensDistanceWhenPawnsAreAdjacent() {
        // ONE at (5,4), TWO at (4,4): directly adjacent, open board.
        // Standard BFS ignores opponent occupancy — just walks through (4,4) as empty: 5 steps.
        // Jump-aware BFS: ONE jumps straight over TWO to land on (3,4) in 1 move, then 3 more = 4.
        GameState state = new GameState()
            .withPawnMove(new Position(5, 4))  // ONE → (5,4); TWO to move
            .withPawnMove(new Position(4, 4)); // TWO → (4,4); back to ONE
        assertEquals(5, pathChecker.shortestPath(state, Player.ONE));
        assertEquals(4, pathChecker.shortestPathWithJumps(state, Player.ONE));
    }

    @Test
    void jumpAwareBfsFallsBackToDiagonalWhenStraightBlocked() {
        // ONE at (5,4), TWO at (4,4). H-wall at (3,4) blocks the (4,4)↔(3,4) edge,
        // so the straight jump is unavailable. ONE must jump diagonally — landing on (4,3) or
        // (4,5) at cost 1, then needs 4 more steps to row 0: total 5.
        // Standard BFS (no jump) is forced to detour: (5,4)→(4,4)→(4,3)→(3,3)→…→(0,3) = 6.
        GameState state = new GameState()
            .withPawnMove(new Position(5, 4))
            .withPawnMove(new Position(4, 4))
            .withWallMove(new Wall(HORIZONTAL, 3, 4)); // blocks cols 4,5 at row 3↔4 boundary
        assertEquals(6, pathChecker.shortestPath(state, Player.ONE));
        assertEquals(5, pathChecker.shortestPathWithJumps(state, Player.ONE));
    }
}

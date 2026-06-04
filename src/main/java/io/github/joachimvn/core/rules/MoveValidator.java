package io.github.joachimvn.core.rules;

import io.github.joachimvn.core.model.*;

import java.util.ArrayList;
import java.util.List;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;

public class MoveValidator {
    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int MAX_WALL_ANCHOR = GameState.BOARD_SIZE - 2;
    private final PathChecker pathChecker = new PathChecker();

    public List<PawnMove> getLegalPawnMoves(GameState state) {
        List<PawnMove> moves = new ArrayList<>();
        Player current = state.getCurrentPlayer();
        Position pos = state.getPawnPosition(current);
        Position oppPos = state.getPawnPosition(current.opponent());
        for (int[] d : DIRS) {
            Position neighbor = pos.offset(d[0], d[1]);
            if (!neighbor.isOnBoard() || state.isEdgeBlocked(pos, neighbor)) continue;
            if (neighbor.equals(oppPos)) {
                addJumpMoves(state, oppPos, d, moves);
            } else {
                moves.add(new PawnMove(neighbor));
            }
        }
        return moves;
    }

    // Quoridor jump rules: straight over opponent if unblocked, else diagonal on either side.
    private void addJumpMoves(GameState state, Position oppPos, int[] dir, List<PawnMove> moves) {
        Position straight = oppPos.offset(dir[0], dir[1]);
        if (straight.isOnBoard() && !state.isEdgeBlocked(oppPos, straight)) {
            moves.add(new PawnMove(straight));
        } else {
            int[][] perps = {{-dir[1], dir[0]}, {dir[1], -dir[0]}};
            for (int[] p : perps) {
                Position diag = oppPos.offset(p[0], p[1]);
                if (diag.isOnBoard() && !state.isEdgeBlocked(oppPos, diag)) {
                    moves.add(new PawnMove(diag));
                }
            }
        }
    }

    public boolean isWallLegal(GameState state, Wall wall) {
        if (state.getWallCount(state.getCurrentPlayer()) <= 0) return false;
        if (wall.row() < 0 || wall.row() > MAX_WALL_ANCHOR
            || wall.col() < 0 || wall.col() > MAX_WALL_ANCHOR) return false;
        if (state.hasWall(wall) || wallHasOverlap(state, wall)) return false;
        return pathChecker.bothPlayersHavePath(state.withWallMove(wall));
    }

    // Adjacent/crossing walls that would share an edge or cross at the same anchor.
    private boolean wallHasOverlap(GameState state, Wall wall) {
        if (wall.orientation() == HORIZONTAL) {
            return state.hasWall(new Wall(HORIZONTAL, wall.row(), wall.col() - 1))
                || state.hasWall(new Wall(HORIZONTAL, wall.row(), wall.col() + 1))
                || state.hasWall(new Wall(VERTICAL,   wall.row(), wall.col()));
        } else {
            return state.hasWall(new Wall(VERTICAL,   wall.row() - 1, wall.col()))
                || state.hasWall(new Wall(VERTICAL,   wall.row() + 1, wall.col()))
                || state.hasWall(new Wall(HORIZONTAL, wall.row(),     wall.col()));
        }
    }

    public List<WallMove> getLegalWallMoves(GameState state) {
        List<WallMove> moves = new ArrayList<>();
        if (state.getWallCount(state.getCurrentPlayer()) <= 0) return moves;
        for (Wall.Orientation o : Wall.Orientation.values()) {
            for (int r = 0; r <= MAX_WALL_ANCHOR; r++) {
                for (int c = 0; c <= MAX_WALL_ANCHOR; c++) {
                    Wall wall = new Wall(o, r, c);
                    if (isWallLegal(state, wall)) moves.add(new WallMove(wall));
                }
            }
        }
        return moves;
    }
}

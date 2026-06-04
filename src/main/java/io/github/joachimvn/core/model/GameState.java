package io.github.joachimvn.core.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;

public final class GameState {
    public static final int BOARD_SIZE = 9;
    public static final int WALLS_PER_PLAYER = 10;

    private final EnumMap<Player, Position> pawns;
    private final Set<Wall> walls;
    private final EnumMap<Player, Integer> wallCounts;
    private final Player currentPlayer;

    public GameState() {
        pawns = new EnumMap<>(Player.class);
        pawns.put(Player.ONE, new Position(8, 4));
        pawns.put(Player.TWO, new Position(0, 4));
        walls = new HashSet<>();
        wallCounts = new EnumMap<>(Player.class);
        wallCounts.put(Player.ONE, WALLS_PER_PLAYER);
        wallCounts.put(Player.TWO, WALLS_PER_PLAYER);
        currentPlayer = Player.ONE;
    }

    private GameState(EnumMap<Player, Position> pawns, Set<Wall> walls,
                      EnumMap<Player, Integer> wallCounts, Player currentPlayer) {
        this.pawns = new EnumMap<>(pawns);
        this.walls = new HashSet<>(walls);
        this.wallCounts = new EnumMap<>(wallCounts);
        this.currentPlayer = currentPlayer;
    }

    public Position getPawnPosition(Player player) { return pawns.get(player); }
    public Set<Wall> getWalls() { return Collections.unmodifiableSet(walls); }
    public int getWallCount(Player player) { return wallCounts.get(player); }
    public Player getCurrentPlayer() { return currentPlayer; }

    public GameState withPawnMove(Position target) {
        EnumMap<Player, Position> newPawns = new EnumMap<>(pawns);
        newPawns.put(currentPlayer, target);
        return new GameState(newPawns, walls, wallCounts, currentPlayer.opponent());
    }

    public GameState withWallMove(Wall wall) {
        Set<Wall> newWalls = new HashSet<>(walls);
        newWalls.add(wall);
        EnumMap<Player, Integer> newCounts = new EnumMap<>(wallCounts);
        newCounts.put(currentPlayer, wallCounts.get(currentPlayer) - 1);
        return new GameState(pawns, newWalls, newCounts, currentPlayer.opponent());
    }

    /**
     * H wall at (r,c) blocks vertical movement at cols c and c+1 across the r↔r+1 boundary.
     * V wall at (r,c) blocks horizontal movement at rows r and r+1 across the c↔c+1 boundary.
     * Out-of-bounds wall lookups are safe — they simply won't match any stored wall.
     */
    public boolean isEdgeBlocked(Position from, Position to) {
        int dr = to.row() - from.row();
        int dc = to.col() - from.col();
        if (Math.abs(dr) + Math.abs(dc) != 1) return false;
        if (dc == 0) { // vertical movement — blocked by H walls
            int edgeRow = Math.min(from.row(), to.row());
            return walls.contains(new Wall(HORIZONTAL, edgeRow, from.col()))
                || walls.contains(new Wall(HORIZONTAL, edgeRow, from.col() - 1));
        }
        if (dr == 0) { // horizontal movement — blocked by V walls
            int edgeCol = Math.min(from.col(), to.col());
            return walls.contains(new Wall(VERTICAL, from.row(), edgeCol))
                || walls.contains(new Wall(VERTICAL, from.row() - 1, edgeCol));
        }
        return false;
    }

    public boolean hasWall(Wall wall) { return walls.contains(wall); }
}

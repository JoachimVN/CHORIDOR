package io.github.joachimvn.core.model;

public record Position(int row, int col) {
    public boolean isOnBoard() {
        return row >= 0 && row < GameState.BOARD_SIZE
            && col >= 0 && col < GameState.BOARD_SIZE;
    }

    public Position offset(int dr, int dc) {
        return new Position(row + dr, col + dc);
    }
}

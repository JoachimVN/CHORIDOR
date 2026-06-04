package io.github.joachimvn.core.model;

public record Position(int row, int col) {
    public boolean isOnBoard() {
        return row >= 0 && row <= 8 && col >= 0 && col <= 8;
    }

    public Position offset(int dr, int dc) {
        return new Position(row + dr, col + dc);
    }
}

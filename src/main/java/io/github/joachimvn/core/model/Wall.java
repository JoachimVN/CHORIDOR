package io.github.joachimvn.core.model;

// No bounds validation in constructor — out-of-bounds instances are used as
// lookup keys in GameState.isEdgeBlocked and will simply never match stored walls.
public record Wall(Orientation orientation, int row, int col) {
    public enum Orientation { HORIZONTAL, VERTICAL }
}

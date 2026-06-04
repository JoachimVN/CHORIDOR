package io.github.joachimvn.core.model;

public enum Player {
    ONE, TWO;

    public Player opponent() {
        return this == ONE ? TWO : ONE;
    }

    public int goalRow() {
        return this == ONE ? 0 : 8;
    }
}

package io.github.joachimvn.core.rules;

import io.github.joachimvn.core.model.*;

import java.util.Optional;

public class GameEngine {
    private final MoveValidator validator = new MoveValidator();

    public GameState applyMove(GameState state, Move move) {
        return switch (move) {
            case PawnMove pm -> {
                if (!validator.getLegalPawnMoves(state).contains(pm))
                    throw new IllegalArgumentException("Illegal pawn move: " + pm);
                yield state.withPawnMove(pm.target());
            }
            case WallMove wm -> {
                if (!validator.isWallLegal(state, wm.wall()))
                    throw new IllegalArgumentException("Illegal wall move: " + wm);
                yield state.withWallMove(wm.wall());
            }
        };
    }

    public Optional<Player> getWinner(GameState state) {
        for (Player p : Player.values()) {
            if (state.getPawnPosition(p).row() == p.goalRow())
                return Optional.of(p);
        }
        return Optional.empty();
    }

    public boolean isGameOver(GameState state) {
        return getWinner(state).isPresent();
    }
}

package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Move;
import io.github.joachimvn.core.model.Wall;
import io.github.joachimvn.core.model.WallMove;
import io.github.joachimvn.core.rules.MoveValidator;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

public class RandomStrategy implements Strategy {
    private static final int WALL_ANCHOR_MAX = GameState.BOARD_SIZE - 2;
    private static final int WALL_SAMPLE_ATTEMPTS = 30;

    private final Random rng;
    private final MoveValidator validator = new MoveValidator();

    public RandomStrategy() { this.rng = new Random(); }
    public RandomStrategy(long seed) { this.rng = new Random(seed); }

    @Override public String displayName() { return "Random"; }
    @Override public String description()   { return "Makes completely random moves, never plans ahead"; }

    @Override
    public Move decide(GameState state) {
        List<? extends Move> pawnMoves = validator.getLegalPawnMoves(state);
        if (pawnMoves.isEmpty()) throw new NoSuchElementException("No legal moves available");

        if (state.getWallCount(state.getCurrentPlayer()) > 0 && rng.nextBoolean()) {
            Wall.Orientation[] orientations = Wall.Orientation.values();
            for (int i = 0; i < WALL_SAMPLE_ATTEMPTS; i++) {
                Wall candidate = new Wall(
                    orientations[rng.nextInt(orientations.length)],
                    rng.nextInt(WALL_ANCHOR_MAX + 1),
                    rng.nextInt(WALL_ANCHOR_MAX + 1)
                );
                if (validator.isWallLegal(state, candidate)) return new WallMove(candidate);
            }
        }
        return pawnMoves.get(rng.nextInt(pawnMoves.size()));
    }
}

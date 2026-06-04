package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Move;
import io.github.joachimvn.core.rules.MoveValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

public class RandomStrategy implements Strategy {
    private final Random rng;
    private final MoveValidator validator = new MoveValidator();

    public RandomStrategy() { this.rng = new Random(); }
    public RandomStrategy(long seed) { this.rng = new Random(seed); }

    @Override
    public Move decide(GameState state) {
        List<Move> moves = new ArrayList<>();
        moves.addAll(validator.getLegalPawnMoves(state));
        moves.addAll(validator.getLegalWallMoves(state));
        if (moves.isEmpty()) {
            throw new NoSuchElementException("No legal moves available for current player");
        }
        return moves.get(rng.nextInt(moves.size()));
    }
}

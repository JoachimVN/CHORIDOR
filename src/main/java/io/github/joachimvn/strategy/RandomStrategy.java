package io.github.joachimvn.strategy;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Move;
import io.github.joachimvn.core.rules.MoveValidator;

import java.util.List;
import java.util.Random;

public class RandomStrategy implements Strategy {
    private final Random rng;
    private final MoveValidator validator = new MoveValidator();

    public RandomStrategy() { this.rng = new Random(); }
    public RandomStrategy(long seed) { this.rng = new Random(seed); }

    @Override
    public Move decide(GameState state) {
        List<? extends Move> moves = validator.getLegalPawnMoves(state);
        return moves.get(rng.nextInt(moves.size()));
    }
}

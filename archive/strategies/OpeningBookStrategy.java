package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;

/**
 * Plays a memorised central-wall opening for the first several turns, then hands off
 * to deep pruned minimax for the rest of the game.
 *
 * <p>The book targets the centre-board rows with horizontal and vertical walls that
 * create chokepoints without sealing off paths. Walls are tried in order; any that are
 * already placed, cross an existing wall, or would block all paths are skipped.
 * Once the book is exhausted (or all remaining entries are illegal), every subsequent
 * turn uses the full minimax inherited from {@link AbstractPrunedSearchStrategy}.
 *
 * <p>The opening goal: lock in a positional advantage early (narrow the opponent's
 * route options) while spending walls at a controlled rate, then let the search engine
 * convert the resulting position.
 */
public class OpeningBookStrategy extends AbstractPrunedSearchStrategy {

    private static final Wall[] BOOK = {
        new Wall(HORIZONTAL, 4, 3),
        new Wall(HORIZONTAL, 4, 5),
        new Wall(VERTICAL,   3, 4),
        new Wall(VERTICAL,   5, 4),
        new Wall(HORIZONTAL, 2, 3),
        new Wall(HORIZONTAL, 6, 4),
    };

    private int bookIdx = 0;

    public OpeningBookStrategy(Player aiPlayer) {
        super(aiPlayer);
    }

    @Override public String displayName() { return "Opening Book"; }
    @Override public String description() {
        return "Plays a memorised central-wall opening, then switches to deep minimax";
    }

    @Override
    public Move decide(GameState state) {
        if (bookIdx < BOOK.length && state.getWallCount(state.getCurrentPlayer()) > 0) {
            while (bookIdx < BOOK.length) {
                Wall w = BOOK[bookIdx++];
                if (validator.isWallLegal(state, w)) return new WallMove(w);
            }
        }
        return super.decide(state);
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return 3 * (oppDist - myDist);
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return score(myDist, oppDist) + wallAdvantage(state);
    }
}

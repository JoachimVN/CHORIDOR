package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;
import java.util.stream.Collectors;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;

/**
 * Pre-planned opening blitz: places all 10 walls in a fixed centre-board pattern as fast
 * as possible, then switches to pure greedy pawn advancement once the walls are gone.
 *
 * <p>The opening script targets the middle rows with alternating horizontal and vertical
 * walls to create a labyrinthine centre without illegally sealing off paths. Whenever a
 * scripted wall is already placed, crosses an existing wall, or would block all paths, the
 * validator rejects it and the next script entry is tried. Any still-available legal wall
 * is used as a final fallback before switching to pawn mode.
 */
public class WallDumperStrategy implements Strategy {

    private static final Wall[] SCRIPT = {
        new Wall(HORIZONTAL, 4, 3),
        new Wall(HORIZONTAL, 4, 5),
        new Wall(VERTICAL,   3, 4),
        new Wall(VERTICAL,   5, 4),
        new Wall(HORIZONTAL, 2, 2),
        new Wall(HORIZONTAL, 6, 5),
        new Wall(VERTICAL,   3, 2),
        new Wall(VERTICAL,   5, 6),
        new Wall(HORIZONTAL, 2, 6),
        new Wall(HORIZONTAL, 6, 1),
    };

    private final Player      aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public WallDumperStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Fortnite"; }
    @Override public String description() {
        return "Blasts all 10 walls into a pre-planned centre formation, then runs for the goal";
    }

    @Override
    public Move decide(GameState state) {
        List<WallMove> legalWalls = validator.getLegalWallMoves(state);

        if (!legalWalls.isEmpty() && state.getWallCount(aiPlayer) > 0) {
            Set<Wall> legalSet = legalWalls.stream()
                .map(WallMove::wall)
                .collect(Collectors.toSet());
            for (Wall w : SCRIPT) {
                if (legalSet.contains(w)) return new WallMove(w);
            }
            // Script exhausted or all blocked — dump any remaining legal wall
            return legalWalls.get(0);
        }

        // No walls left: greedily advance the pawn toward goal
        return bestPawnAdvance(state);
    }

    private Move bestPawnAdvance(GameState state) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");
        int bestDist = pathChecker.shortestPathWithJumps(state, aiPlayer);
        PawnMove best = pawns.get(0);
        for (PawnMove pm : pawns) {
            int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), aiPlayer);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return best;
    }
}

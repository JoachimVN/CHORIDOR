package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Reactive mimic: reads what the opponent just did and mirrors the move type.
 *
 * <p>If the opponent placed a wall this turn, Copycat responds with the wall that most
 * extends the opponent's own BFS path (a counter-wall). If the opponent moved their pawn,
 * Copycat takes the BFS-optimal pawn advance toward its goal.
 *
 * <p>The previous game state and Copycat's own last move are stored between turns so the
 * opponent's action can be inferred by comparing wall counts: if the opponent's count fell,
 * they spent a wall; otherwise they moved their pawn.
 */
public class CopycatStrategy implements Strategy {

    private final Player      aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    private GameState lastState  = null;
    private Move      lastMyMove = null;

    public CopycatStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Copycat"; }
    @Override public String description() {
        return "Mirrors the opponent: counters wall placements with walls, pawn advances with pawn advances";
    }

    @Override
    public Move decide(GameState state) {
        Move chosen = didOpponentPlaceWall(state) ? bestWall(state) : bestPawnAdvance(state);
        lastState  = state;
        lastMyMove = chosen;
        return chosen;
    }

    private boolean didOpponentPlaceWall(GameState state) {
        if (lastState == null || lastMyMove == null) return false;
        GameState afterMyMove = apply(lastState, lastMyMove);
        return state.getWallCount(aiPlayer.opponent()) < afterMyMove.getWallCount(aiPlayer.opponent());
    }

    private Move bestWall(GameState state) {
        List<WallMove> walls = validator.getLegalWallMoves(state);
        if (walls.isEmpty()) return bestPawnAdvance(state);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());
        WallMove best = walls.get(0);
        int bestImpact = Integer.MIN_VALUE;
        for (WallMove wm : walls) {
            int impact = pathChecker.shortestPathWithJumps(state.withWallMove(wm.wall()), aiPlayer.opponent()) - oppDist;
            if (impact > bestImpact) { bestImpact = impact; best = wm; }
        }
        return best;
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

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

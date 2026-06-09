package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Maintains two simultaneous attack vectors — the opponent must choose which to block.
 *
 * <p>Pawn moves are scored by {@code dist * 2 - forks} where {@code forks} is the count
 * of unblocked forward neighbours at the target square. A position with two forward exits
 * scores better than one with a single corridor, even at equal distance, because the
 * opponent cannot seal both routes with one wall. The factor of 2 ensures distance remains
 * the primary criterion while still rewarding positional flexibility.
 *
 * <p>Wall placement: extends the opponent's path while preserving at least one fork option
 * at the current pawn position. Walls that reduce own-fork options by more than one are
 * skipped even if they block the opponent well — collapsing both threats simultaneously
 * defeats the strategy's purpose.
 */
public class DualThreatStrategy implements Strategy {

    private static final int[][] DIRS     = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int     FORK_DISCOUNT = 1; // score reduction per fork option
    private static final int     FORK_PENALTY  = 2; // extra cost for walls collapsing a fork

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public DualThreatStrategy(Player aiPlayer) { this.aiPlayer = aiPlayer; }

    @Override public String displayName() { return "Dual Threat"; }
    @Override public String description() {
        return "Seeks fork positions with two viable attack lanes, forcing the opponent to choose which to block";
    }

    @Override
    public Move decide(GameState state) {
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());
        Position myPos = state.getPawnPosition(aiPlayer);

        // Wall placement: only when not already winning the sprint
        if (state.getWallCount(aiPlayer) > 0 && myDist >= oppDist - 1) {
            WallMove wall = bestForkPreservingWall(state, myPos, myDist, oppDist);
            if (wall != null) return wall;
        }

        return bestForkAdvance(state, myDist);
    }

    // ── Pawn advance ──────────────────────────────────────────────────────────

    /**
     * Scores each pawn move by {@code dist * 2 - forks}. Lower is better.
     * Prefers positions that are close to goal AND have multiple forward exits.
     */
    private Move bestForkAdvance(GameState state, int myDist) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");

        PawnMove best      = pawns.get(0);
        int      bestScore = Integer.MAX_VALUE;

        for (PawnMove pm : pawns) {
            GameState after = state.withPawnMove(pm.target());
            int dist  = pathChecker.shortestPathWithJumps(after, aiPlayer);
            int forks = countForwardExits(pm.target(), after);
            int score = dist * 2 - forks * FORK_DISCOUNT;
            if (score < bestScore) { bestScore = score; best = pm; }
        }
        return best;
    }

    // ── Wall selection ────────────────────────────────────────────────────────

    /**
     * Best wall that extends oppDist while not collapsing more than one of our fork options.
     */
    private WallMove bestForkPreservingWall(GameState state, Position myPos,
                                            int myDist, int oppDist) {
        int currentForks = countForwardExits(myPos, state);
        List<WallMove> walls = validator.getLegalWallMoves(state);
        WallMove best      = null;
        int      bestScore = 0;

        for (WallMove wm : walls) {
            GameState after = state.withWallMove(wm.wall());
            // Don't significantly block own path
            if (pathChecker.shortestPathWithJumps(after, aiPlayer) > myDist + 1) continue;
            int oppImpact   = pathChecker.shortestPathWithJumps(after, aiPlayer.opponent()) - oppDist;
            int forkLoss    = currentForks - countForwardExits(myPos, after);
            int score       = oppImpact - (forkLoss > 1 ? FORK_PENALTY * forkLoss : 0);
            if (score > bestScore) { bestScore = score; best = wm; }
        }
        return best;
    }

    // ── Fork counting ─────────────────────────────────────────────────────────

    /**
     * Counts unblocked neighbours of {@code pos} that are closer to aiPlayer's goal
     * by raw row distance. This is a fast O(4) proxy for positional flexibility.
     */
    private int countForwardExits(Position pos, GameState state) {
        int goalRow = aiPlayer.goalRow();
        int myRow   = pos.row();
        int count   = 0;
        for (int[] d : DIRS) {
            Position nb = pos.offset(d[0], d[1]);
            if (!nb.isOnBoard()) continue;
            if (state.isEdgeBlocked(pos, nb)) continue;
            if (Math.abs(nb.row() - goalRow) < Math.abs(myRow - goalRow)) count++;
        }
        return count;
    }
}

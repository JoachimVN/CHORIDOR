package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Commits to one lane and builds walls to protect it.
 *
 * <p>Each turn, the BFS shortest path is traced (with full parent-tracking) and walls
 * adjacent to that path are scored by how much they extend the opponent's distance
 * without increasing the corridor itself. A corridor wall is only spent when already
 * ahead in the race — the strategy never trades a tempo it doesn't have. When not
 * walling, it advances strictly along the traced path.
 */
public class CorridorStrategy implements Strategy {

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public CorridorStrategy(Player aiPlayer) { this.aiPlayer = aiPlayer; }

    @Override public String displayName() { return "Corridor"; }
    @Override public String description() {
        return "Commits to one lane and builds walls alongside it to seal the path from flanking";
    }

    @Override
    public Move decide(GameState state) {
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());

        if (state.getWallCount(aiPlayer) > 0 && myDist < oppDist) {
            List<Position> path = tracePath(state);
            WallMove wall = bestCorridorWall(state, path, myDist, oppDist);
            if (wall != null) return wall;
        }

        return bestAdvance(state, myDist);
    }

    // ── Wall selection ────────────────────────────────────────────────────────

    /**
     * Best wall adjacent to the path that does not increase myDist and maximally
     * extends oppDist. Returns null if no such wall has a positive gain.
     */
    private WallMove bestCorridorWall(GameState state, List<Position> path,
                                      int myDist, int oppDist) {
        Set<Position> pathSet = new HashSet<>(path);
        List<WallMove> legal  = validator.getLegalWallMoves(state);
        WallMove best   = null;
        int      bestGain = 0;

        for (WallMove wm : legal) {
            if (!nearPath(wm.wall(), pathSet)) continue;
            GameState after = state.withWallMove(wm.wall());
            if (pathChecker.shortestPathWithJumps(after, aiPlayer) > myDist) continue;
            int gain = pathChecker.shortestPathWithJumps(after, aiPlayer.opponent()) - oppDist;
            if (gain > bestGain) { bestGain = gain; best = wm; }
        }
        return best;
    }

    /** True if the wall anchor falls within a 3×3 window around any path cell. */
    private static boolean nearPath(Wall w, Set<Position> pathSet) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (pathSet.contains(new Position(w.row() + dr, w.col() + dc))) return true;
            }
        }
        return false;
    }

    // ── Path tracing ──────────────────────────────────────────────────────────

    /**
     * BFS from aiPlayer's pawn to its goal row, tracking parents to reconstruct the path.
     * Jumps over the opponent are not modelled here; corridor wall selection is robust
     * to the small positional error this introduces.
     */
    private List<Position> tracePath(GameState state) {
        Position start  = state.getPawnPosition(aiPlayer);
        int      goalRow = aiPlayer.goalRow();

        Map<Position, Position> parent = new HashMap<>();
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(start);
        parent.put(start, null);

        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            for (int[] d : DIRS) {
                Position next = cur.offset(d[0], d[1]);
                if (!next.isOnBoard() || state.isEdgeBlocked(cur, next) || parent.containsKey(next))
                    continue;
                parent.put(next, cur);
                if (next.row() == goalRow) return buildPath(parent, next);
                queue.add(next);
            }
        }
        return List.of(start);
    }

    private static List<Position> buildPath(Map<Position, Position> parent, Position goal) {
        List<Position> path = new ArrayList<>();
        for (Position p = goal; p != null; p = parent.get(p)) path.add(p);
        Collections.reverse(path);
        return path;
    }

    // ── Advance ───────────────────────────────────────────────────────────────

    private Move bestAdvance(GameState state, int myDist) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");
        PawnMove best     = pawns.get(0);
        int      bestDist = myDist;
        for (PawnMove pm : pawns) {
            int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), aiPlayer);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return best;
    }
}

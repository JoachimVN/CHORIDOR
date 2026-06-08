package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Path-count heuristic: counts cells that lie on any shortest path to goal.
 *
 * <p>A player with many cells on their shortest-path DAG is hard to wall off — the opponent
 * must simultaneously block many corridors. A player funneled into a single corridor can be
 * sealed with one well-placed wall. The score weights this "path breadth" heavily alongside
 * BFS distance, rewarding moves that widen your own corridor and narrow the opponent's.
 *
 * <p>Move candidates follow Sharp's impact-based filtering so the search stays at depth 8.
 */
public class PathCountStrategy extends AbstractSearchStrategy {

    private static final int   DEPTH               = 8;
    private static final long  TIME_LIMIT_MS        = 1000;
    private static final int   WALL_PRUNE_DIST      = 4;
    private static final int   MIN_WALL_IMPACT      = 1;
    private static final int   MAX_WALL_CANDIDATES  = 8;

    private static final int[][] DIRS = {{-1,0},{1,0},{0,-1},{0,1}};

    public PathCountStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
    }

    @Override public String displayName() { return "Path Count"; }
    @Override public String description() {
        return "Counts cells on all shortest paths — maximizes its own corridor width, minimizes the opponent's";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return (oppDist - myDist) * 10;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        int myPaths  = pathDagWidth(state, aiPlayer());
        int oppPaths = pathDagWidth(state, aiPlayer().opponent());
        return (myPaths - oppPaths) * 3 + (oppDist - myDist) * 10 + wallAdvantage(state);
    }

    /**
     * Counts cells that lie on at least one shortest path from player's pawn to their goal row.
     * A higher count means the player has more redundant routes and is harder to wall off.
     */
    private int pathDagWidth(GameState state, Player player) {
        int boardSize = GameState.BOARD_SIZE;
        Position start = state.getPawnPosition(player);
        int goalRow    = player.goalRow();

        // Forward BFS: shortest distance from start to each cell
        int[][] fwd = new int[boardSize][boardSize];
        for (int[] r : fwd) Arrays.fill(r, Integer.MAX_VALUE);
        fwd[start.row()][start.col()] = 0;
        Queue<Position> q = new ArrayDeque<>();
        q.add(start);
        while (!q.isEmpty()) {
            Position cur = q.poll();
            for (int[] d : DIRS) {
                Position nxt = cur.offset(d[0], d[1]);
                if (!nxt.isOnBoard() || state.isEdgeBlocked(cur, nxt)) continue;
                if (fwd[nxt.row()][nxt.col()] == Integer.MAX_VALUE) {
                    fwd[nxt.row()][nxt.col()] = fwd[cur.row()][cur.col()] + 1;
                    q.add(nxt);
                }
            }
        }

        // Shortest distance to goal row
        int best = Integer.MAX_VALUE;
        for (int c = 0; c < boardSize; c++) {
            if (fwd[goalRow][c] < best) best = fwd[goalRow][c];
        }
        if (best == Integer.MAX_VALUE) return 0;

        // Backward BFS: shortest distance from goal row back to each cell
        // (walls are symmetric, so isEdgeBlocked is the same in both directions)
        int[][] bwd = new int[boardSize][boardSize];
        for (int[] r : bwd) Arrays.fill(r, Integer.MAX_VALUE);
        for (int c = 0; c < boardSize; c++) {
            if (fwd[goalRow][c] == best) {
                bwd[goalRow][c] = 0;
                q.add(new Position(goalRow, c));
            }
        }
        while (!q.isEmpty()) {
            Position cur = q.poll();
            for (int[] d : DIRS) {
                Position nxt = cur.offset(d[0], d[1]);
                if (!nxt.isOnBoard() || state.isEdgeBlocked(cur, nxt)) continue;
                if (bwd[nxt.row()][nxt.col()] == Integer.MAX_VALUE) {
                    bwd[nxt.row()][nxt.col()] = bwd[cur.row()][cur.col()] + 1;
                    q.add(nxt);
                }
            }
        }

        // Count cells on any shortest path: fwd[r][c] + bwd[r][c] == best
        int count = 0;
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if (fwd[r][c] != Integer.MAX_VALUE && bwd[r][c] != Integer.MAX_VALUE
                        && fwd[r][c] + bwd[r][c] == best) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    protected List<Move> candidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        int myDist     = pathChecker.shortestPathWithJumps(state, current);
        int oppDist    = pathChecker.shortestPathWithJumps(state, opp);
        Position p1    = state.getPawnPosition(Player.ONE);
        Position p2    = state.getPawnPosition(Player.TWO);

        List<Move> result = new ArrayList<>();

        // Advancing pawn moves only; fall back to all when fully blocked
        List<PawnMove> all = validator.getLegalPawnMoves(state);
        boolean hasAdvancing = false;
        for (PawnMove pm : all) {
            if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist) {
                result.add(pm);
                hasAdvancing = true;
            }
        }
        if (!hasAdvancing) result.addAll(all);

        // Impact-based wall filtering (same as Sharp)
        if (state.getWallCount(current) > 0) {
            List<WallMove> legalWalls = validator.getLegalWallMoves(state);
            List<int[]> impacts = new ArrayList<>();
            for (int i = 0; i < legalWalls.size(); i++) {
                Wall w = legalWalls.get(i).wall();
                if (!nearEither(w, p1, p2)) continue;
                int impact = pathChecker.shortestPathWithJumps(state.withWallMove(w), opp) - oppDist;
                if (impact >= MIN_WALL_IMPACT) impacts.add(new int[]{impact, i});
            }
            impacts.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
            int limit = Math.min(impacts.size(), MAX_WALL_CANDIDATES);
            for (int k = 0; k < limit; k++) result.add(legalWalls.get(impacts.get(k)[1]));
        }
        return result;
    }

    private boolean nearEither(Wall wall, Position p1, Position p2) {
        return chebyshev(wall, p1) <= WALL_PRUNE_DIST
            || chebyshev(wall, p2) <= WALL_PRUNE_DIST;
    }

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }
}

package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Deep search scored by Voronoi territory rather than path length.
 *
 * <p>After each candidate move the board is partitioned: every cell is awarded to
 * whichever player can reach it in fewer BFS steps from their current pawn position
 * (ties are neutral). The score is {@code myTerritory − oppTerritory}, weighted 3×
 * over the raw BFS distance gap. A player who dominates the centre tends to have many
 * short alternative paths and is harder to wall off — something the distance-only
 * heuristics miss entirely.
 *
 * <p>Depth is capped at 6 (vs 8 for pruned strategies) to absorb the extra cost of
 * two full board-spanning BFS scans per evaluation node.
 */
public class TerritoryStrategy extends AbstractSearchStrategy {

    private static final int   DEPTH          = 6;
    private static final long  TIME_LIMIT_MS  = 1000;
    private static final int   WALL_PRUNE_DIST = 4;

    private final Player aiPlayer;

    public TerritoryStrategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Territory"; }
    @Override public String description() {
        return "Scores board control by Voronoi territory — who can reach more squares first";
    }

    @Override
    protected int score(int myDist, int oppDist) {
        return oppDist - myDist;
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        return voronoiDiff(state) * 3 + (oppDist - myDist) + wallAdvantage(state);
    }

    @Override
    protected List<Move> candidates(GameState state) {
        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));
        if (state.getWallCount(state.getCurrentPlayer()) > 0) {
            Position p1 = state.getPawnPosition(Player.ONE);
            Position p2 = state.getPawnPosition(Player.TWO);
            for (WallMove wm : validator.getLegalWallMoves(state)) {
                if (nearEither(wm.wall(), p1, p2)) moves.add(wm);
            }
        }
        return moves;
    }

    /** myTerritory − oppTerritory where territory = cells reached faster than the opponent. */
    private int voronoiDiff(GameState state) {
        int[][] dMy  = bfsAll(state, aiPlayer);
        int[][] dOpp = bfsAll(state, aiPlayer.opponent());
        int diff = 0;
        for (int r = 0; r < GameState.BOARD_SIZE; r++) {
            for (int c = 0; c < GameState.BOARD_SIZE; c++) {
                int dm = dMy[r][c], do_ = dOpp[r][c];
                if      (dm < do_) diff++;
                else if (do_ < dm) diff--;
            }
        }
        return diff;
    }

    /** BFS from the given player's pawn to ALL reachable cells, respecting walls. */
    private int[][] bfsAll(GameState state, Player player) {
        int size = GameState.BOARD_SIZE;
        int[][] dist = new int[size][size];
        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
        Position start = state.getPawnPosition(player);
        dist[start.row()][start.col()] = 0;
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(start);
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            int d = dist[cur.row()][cur.col()];
            for (int[] dr : dirs) {
                Position next = cur.offset(dr[0], dr[1]);
                if (!next.isOnBoard()) continue;
                if (dist[next.row()][next.col()] != Integer.MAX_VALUE) continue;
                if (state.isEdgeBlocked(cur, next)) continue;
                dist[next.row()][next.col()] = d + 1;
                queue.add(next);
            }
        }
        return dist;
    }

    private boolean nearEither(Wall wall, Position p1, Position p2) {
        return chebyshev(wall, p1) <= WALL_PRUNE_DIST
            || chebyshev(wall, p2) <= WALL_PRUNE_DIST;
    }

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }
}

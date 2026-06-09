package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Counts cells that lie on at least one shortest path from a player's pawn to their
 * goal row — the "DAG width". A higher count means more redundant routes and is
 * harder to wall off; a player funnelled into a single corridor can be sealed with
 * one well-placed wall.
 */
final class PathWidth {

    private static final int[][] DIRS = {{-1,0},{1,0},{0,-1},{0,1}};

    private PathWidth() {}

    static int dagWidth(GameState state, Player player) {
        int boardSize = GameState.BOARD_SIZE;
        int goalRow   = player.goalRow();
        int[][] fwd   = bfs(state, state.getPawnPosition(player));

        int best = Integer.MAX_VALUE;
        for (int c = 0; c < boardSize; c++)
            if (fwd[goalRow][c] < best) best = fwd[goalRow][c];
        if (best == Integer.MAX_VALUE) return 0;

        Queue<Position> seeds = new ArrayDeque<>();
        for (int c = 0; c < boardSize; c++)
            if (fwd[goalRow][c] == best) seeds.add(new Position(goalRow, c));

        int[][] bwd = bfs(state, seeds);

        int count = 0;
        for (int r = 0; r < boardSize; r++)
            for (int c = 0; c < boardSize; c++)
                if (fwd[r][c] != Integer.MAX_VALUE && bwd[r][c] != Integer.MAX_VALUE
                        && fwd[r][c] + bwd[r][c] == best)
                    count++;
        return count;
    }

    static int[][] bfs(GameState state, Position start) {
        Queue<Position> q = new ArrayDeque<>();
        q.add(start);
        return bfs(state, q);
    }

    static int[][] bfs(GameState state, Queue<Position> seeds) {
        int n = GameState.BOARD_SIZE;
        int[][] dist = new int[n][n];
        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
        for (Position s : seeds) dist[s.row()][s.col()] = 0;
        Queue<Position> q = new ArrayDeque<>(seeds);
        while (!q.isEmpty()) {
            Position cur = q.poll();
            for (int[] d : DIRS) {
                Position nxt = cur.offset(d[0], d[1]);
                if (!nxt.isOnBoard() || state.isEdgeBlocked(cur, nxt)) continue;
                if (dist[nxt.row()][nxt.col()] == Integer.MAX_VALUE) {
                    dist[nxt.row()][nxt.col()] = dist[cur.row()][cur.col()] + 1;
                    q.add(nxt);
                }
            }
        }
        return dist;
    }
}

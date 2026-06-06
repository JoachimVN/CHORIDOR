package io.github.joachimvn.core.rules;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.core.model.Position;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class PathChecker {
    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public boolean hasPath(GameState state, Player player) {
        return shortestPath(state, player) < Integer.MAX_VALUE;
    }

    public int shortestPath(GameState state, Player player) {
        Position start = state.getPawnPosition(player);
        int goalRow = player.goalRow();
        if (start.row() == goalRow) return 0;
        Set<Position> visited = new HashSet<>();
        Queue<Position> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        int dist = 0;
        while (!queue.isEmpty()) {
            dist++;
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Position curr = queue.poll();
                for (int[] d : DIRS) {
                    Position next = curr.offset(d[0], d[1]);
                    if (!next.isOnBoard() || visited.contains(next) || state.isEdgeBlocked(curr, next)) continue;
                    if (next.row() == goalRow) return dist;
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    public boolean bothPlayersHavePath(GameState state) {
        return hasPath(state, Player.ONE) && hasPath(state, Player.TWO);
    }
}

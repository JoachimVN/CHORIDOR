package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Territory-based evaluation: pick the move that maximises board influence.
 *
 * <p>Influence is computed by running a BFS flood-fill from each player's pawn position,
 * respecting walls. For every cell on the 9×9 board the faster arrival wins it. Score =
 * (#cells reached first by me) − (#cells reached first by opponent). Cells equidistant
 * are neutral and not counted.
 *
 * <p>This is a one-move lookahead: each legal move (pawn + wall) is evaluated by the
 * influence score of the resulting position, and the move producing the highest score is
 * taken. Because influence implicitly measures path availability, the strategy tends to
 * open wide corridors for itself and close them for the opponent — a go-inspired approach.
 */
public class InfluenceStrategy implements Strategy {

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int     N    = GameState.BOARD_SIZE;

    private final Player        aiPlayer;
    private final MoveValidator validator = new MoveValidator();

    public InfluenceStrategy(Player aiPlayer) { this.aiPlayer = aiPlayer; }

    @Override public String displayName() { return "Influence"; }
    @Override public String description() {
        return "Picks moves that maximise board cells reached before the opponent — territory over tempo";
    }

    @Override
    public Move decide(GameState state) {
        List<Move> candidates = buildCandidates(state);
        if (candidates.isEmpty()) throw new NoSuchElementException("No legal moves");

        Move best      = candidates.get(0);
        int  bestScore = Integer.MIN_VALUE;

        for (Move m : candidates) {
            GameState after = apply(state, m);
            int score = influence(after);
            if (score > bestScore) { bestScore = score; best = m; }
        }
        return best;
    }

    // ── Influence calculation ─────────────────────────────────────────────────

    /**
     * BFS flood-fill from both pawns; returns #cells where aiPlayer arrives first
     * minus #cells where opponent arrives first.
     */
    private int influence(GameState state) {
        Position myPos  = state.getPawnPosition(aiPlayer);
        Position oppPos = state.getPawnPosition(aiPlayer.opponent());

        int[][] myDist  = floodFill(state, myPos);
        int[][] oppDist = floodFill(state, oppPos);

        int score = 0;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                int md = myDist[r][c];
                int od = oppDist[r][c];
                if (md == Integer.MAX_VALUE && od == Integer.MAX_VALUE) continue;
                if (md < od) score++;
                else if (od < md) score--;
            }
        }
        return score;
    }

    /**
     * BFS from {@code start} through all reachable cells, honouring walls.
     * Opponent pawn occupancy is intentionally ignored: influence measures
     * reach potential, not current occupancy.
     */
    private int[][] floodFill(GameState state, Position start) {
        int[][] dist = new int[N][N];
        for (int[] row : dist) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        dist[start.row()][start.col()] = 0;
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            int nextDist = dist[cur.row()][cur.col()] + 1;
            for (int[] d : DIRS) {
                Position nb = cur.offset(d[0], d[1]);
                if (!nb.isOnBoard() || state.isEdgeBlocked(cur, nb)) continue;
                if (dist[nb.row()][nb.col()] == Integer.MAX_VALUE) {
                    dist[nb.row()][nb.col()] = nextDist;
                    queue.add(nb);
                }
            }
        }
        return dist;
    }

    // ── Candidate generation ──────────────────────────────────────────────────

    /**
     * Pawn moves (always) plus wall moves if walls remain. Wall moves are pruned to those
     * within Chebyshev distance 4 of either pawn to keep evaluation tractable.
     */
    private List<Move> buildCandidates(GameState state) {
        java.util.List<Move> moves = new java.util.ArrayList<>(validator.getLegalPawnMoves(state));
        if (state.getWallCount(aiPlayer) <= 0) return moves;

        Position p1 = state.getPawnPosition(Player.ONE);
        Position p2 = state.getPawnPosition(Player.TWO);
        for (WallMove wm : validator.getLegalWallMoves(state)) {
            if (chebyshev(wm.wall(), p1) <= 4 || chebyshev(wm.wall(), p2) <= 4)
                moves.add(wm);
        }
        return moves;
    }

    private static int chebyshev(Wall w, Position p) {
        return Math.max(Math.abs(w.row() - p.row()), Math.abs(w.col() - p.col()));
    }

    private static GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

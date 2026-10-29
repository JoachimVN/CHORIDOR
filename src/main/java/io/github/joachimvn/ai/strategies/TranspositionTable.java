package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.Random;

/**
 * Fixed-size transposition table using Zobrist hashing.
 *
 * <p>Maps game positions to previously computed alpha-beta scores so the search
 * avoids re-evaluating positions reached via different move sequences. With
 * iterative deepening, later iterations overwrite shallow entries naturally.
 *
 * <p>Thread-unsafe — one instance per strategy instance.
 */
final class TranspositionTable {

    static final int EXACT = 0;
    static final int LOWER = 1; // failed high (beta cutoff) — score is a lower bound
    static final int UPPER = 2; // all moves failed low — score is an upper bound
    static final int MISS  = Integer.MIN_VALUE; // sentinel: no usable entry found

    private static final int  SIZE = 1 << 20; // ~1M entries
    private static final long MASK = SIZE - 1L;

    // Zobrist keys
    private final long[][] posKey;     // [player 0/1][row*9+col]
    private final long[][] wallKey;    // [orientation 0=H/1=V][row*8+col]
    private final long[][] wcKey;      // [player 0/1][wall count 0..10]
    private final long    sideKey;     // XOR in when Player.TWO is to move

    // Parallel arrays — better cache locality than object array
    private final long[] keys   = new long[SIZE];
    private final int[]  scores = new int[SIZE];
    private final byte[] depths = new byte[SIZE];
    private final byte[] flags  = new byte[SIZE];

    TranspositionTable() {
        Random rng = new Random(0xCAFEBABEL);
        int n = GameState.BOARD_SIZE;

        posKey = new long[2][n * n];
        for (int p = 0; p < 2; p++)
            for (int i = 0; i < n * n; i++)
                posKey[p][i] = rng.nextLong();

        // Walls anchor at rows/cols 0..(n-2), giving (n-1)*(n-1) = 64 positions each
        int wallSlots = (n - 1) * (n - 1);
        wallKey = new long[2][wallSlots];
        for (int o = 0; o < 2; o++)
            for (int i = 0; i < wallSlots; i++)
                wallKey[o][i] = rng.nextLong();

        wcKey = new long[2][GameState.WALLS_PER_PLAYER + 1];
        for (int p = 0; p < 2; p++)
            for (int c = 0; c <= GameState.WALLS_PER_PLAYER; c++)
                wcKey[p][c] = rng.nextLong();

        sideKey = rng.nextLong();
    }

    long hash(GameState state) {
        int n = GameState.BOARD_SIZE;
        Position p1 = state.getPawnPosition(Player.ONE);
        Position p2 = state.getPawnPosition(Player.TWO);

        long h = posKey[0][p1.row() * n + p1.col()]
               ^ posKey[1][p2.row() * n + p2.col()]
               ^ wcKey[0][state.getWallCount(Player.ONE)]
               ^ wcKey[1][state.getWallCount(Player.TWO)];

        if (state.getCurrentPlayer() == Player.TWO) h ^= sideKey;

        for (Wall w : state.getWalls()) {
            int oi  = w.orientation() == Wall.Orientation.HORIZONTAL ? 0 : 1;
            int idx = w.row() * (n - 1) + w.col();
            h ^= wallKey[oi][idx];
        }
        return h;
    }

    /**
     * Returns a usable stored score, or {@link #MISS} if none is available.
     * The entry is usable when: key matches, stored depth ≥ requested depth,
     * and the flag is compatible with the current alpha/beta window.
     */
    int probe(long hash, int depth, int alpha, int beta) {
        int idx = (int) (hash & MASK);
        if (keys[idx] != hash || Byte.toUnsignedInt(depths[idx]) < depth)
            return MISS;
        int  score = scores[idx];
        byte flag  = flags[idx];
        if (flag == EXACT)                       return score;
        if (flag == LOWER && score >= beta)      return score;
        if (flag == UPPER && score <= alpha)     return score;
        return MISS;
    }

    void store(long hash, int score, int depth, int flag) {
        int idx = (int) (hash & MASK);
        // Depth-preferred replacement: only overwrite if new depth is at least as deep
        if (keys[idx] == 0 || Byte.toUnsignedInt(depths[idx]) <= depth) {
            keys[idx]   = hash;
            scores[idx] = score;
            depths[idx] = (byte) depth;
            flags[idx]  = (byte) flag;
        }
    }

    void clear() {
        java.util.Arrays.fill(keys, 0L);
    }
}

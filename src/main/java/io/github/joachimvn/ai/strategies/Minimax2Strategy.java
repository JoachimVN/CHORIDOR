package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.core.model.*;

import java.util.*;

/**
 * Enhanced minimax with three key improvements over the baseline strategies:
 *
 * <ol>
 *   <li><b>Transposition table</b> — Zobrist-hashed cache of previously evaluated
 *       positions, eliminating redundant work when transpositions occur across
 *       different move sequences. Enables effective depth well beyond the nominal 12.</li>
 *   <li><b>Killer move heuristic</b> — moves that caused beta cutoffs at a given
 *       depth are tried first at sibling nodes of the same depth, improving
 *       alpha-beta efficiency for wall placements.</li>
 *   <li><b>Phase-aware composite evaluation</b> — opening (pawn-only), midgame
 *       (distance + path-width DAG + wall reserve), and endgame (pure distance race).</li>
 * </ol>
 *
 * <p>Move candidates follow Wikipedia's opening filter + Sharp's impact-based
 * midgame filter to keep branching factor at ~8–12.
 */
public class Minimax2Strategy extends AbstractSearchStrategy {

    private static final int  DEPTH           = 12;
    private static final long TIME_LIMIT_MS   = 1000;
    private static final int  MAX_KILLERS     = 2;

    private static final int  WALL_PRUNE_DIST = 4;
    private static final int  MIN_WALL_IMPACT = 1;
    private static final int  MAX_WALL_CANDS  = 8;

    private static final int  OPENING_DIST    = 4; // pawn-only moves when BFS dist > this
    private static final int  ENDGAME_WALLS   = 2; // pure race when either player ≤ this

    private final TranspositionTable tt      = new TranspositionTable();
    private final Move[][]           killers = new Move[DEPTH + 2][MAX_KILLERS];

    public Minimax2Strategy(Player aiPlayer) {
        super(aiPlayer, TIME_LIMIT_MS, DEPTH);
    }

    @Override public String displayName() { return "Minimax 2"; }
    @Override public String description() {
        return "Minimax with transposition table, killer moves, and phase-aware evaluation";
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    @Override
    protected int score(int myDist, int oppDist) {
        return 3 * (oppDist - myDist);
    }

    @Override
    protected int score(GameState state, int myDist, int oppDist) {
        boolean endgame = state.getWallCount(aiPlayer()) <= ENDGAME_WALLS
                       || state.getWallCount(aiPlayer().opponent()) <= ENDGAME_WALLS;
        if (endgame) return 5 * (oppDist - myDist);
        int myWidth  = PathWidth.dagWidth(state, aiPlayer());
        int oppWidth = PathWidth.dagWidth(state, aiPlayer().opponent());
        return 3 * (oppDist - myDist) + 2 * (myWidth - oppWidth) + wallAdvantage(state);
    }

    // ── Move candidates ───────────────────────────────────────────────────────

    @Override
    protected List<Move> candidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        int myDist     = pathChecker.shortestPathWithJumps(state, current);

        // Opening: pawn-only advancing moves (conserve walls)
        if (myDist > OPENING_DIST) {
            List<PawnMove> all = validator.getLegalPawnMoves(state);
            List<Move> advancing = new ArrayList<>();
            for (PawnMove pm : all)
                if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist)
                    advancing.add(pm);
            return advancing.isEmpty() ? new ArrayList<>(all) : advancing;
        }

        // Midgame / endgame: advancing pawns + impact-sorted walls (Sharp-style)
        int oppDist = pathChecker.shortestPathWithJumps(state, opp);
        Position p1 = state.getPawnPosition(Player.ONE);
        Position p2 = state.getPawnPosition(Player.TWO);

        List<Move> result = new ArrayList<>();

        List<PawnMove> allPawns = validator.getLegalPawnMoves(state);
        boolean hasAdvancing = false;
        for (PawnMove pm : allPawns) {
            if (pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current) < myDist) {
                result.add(pm);
                hasAdvancing = true;
            }
        }
        if (!hasAdvancing) result.addAll(allPawns);

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
            int limit = Math.min(impacts.size(), MAX_WALL_CANDS);
            for (int k = 0; k < limit; k++) result.add(legalWalls.get(impacts.get(k)[1]));
        }
        return result;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Override
    public Move decide(GameState state) {
        long deadline = System.currentTimeMillis() + TIME_LIMIT_MS;
        for (Move[] k : killers) Arrays.fill(k, null);
        tt.clear();

        List<Move> moves = candidates(state);
        if (moves.isEmpty()) throw new NoSuchElementException("No legal moves available");

        // Racing shortcut: if already winning the race, take the best advancing pawn move
        PawnMove racing = racingMove(state, moves, deadline);
        if (racing != null) return racing;

        // Sort root candidates: advancing pawn moves first, then by BFS distance
        moves.sort(rootOrder(state));

        Move best = moves.get(0);
        for (int depth = 1; depth <= DEPTH; depth++) {
            if (System.currentTimeMillis() >= deadline) break;
            Move candidate = searchAtDepth(state, moves, depth, deadline);
            if (candidate != null) best = candidate;
        }
        return best;
    }

    private Move searchAtDepth(GameState state, List<Move> moves, int depth, long deadline) {
        Move best       = null;
        int  bestScore  = Integer.MIN_VALUE + 1;
        int  bestMyDist = Integer.MAX_VALUE;
        int  bestOppDist = Integer.MIN_VALUE;
        int  alpha      = Integer.MIN_VALUE + 1;
        int  beta       = Integer.MAX_VALUE;

        for (Move move : moves) {
            if (System.currentTimeMillis() >= deadline) break;
            GameState next = apply(state, move);
            int s = minimaxTT(next, depth - 1, alpha, beta, false, deadline, 1);
            int myDist  = pathChecker.shortestPathWithJumps(next, aiPlayer());
            int oppDist = pathChecker.shortestPathWithJumps(next, aiPlayer().opponent());
            boolean better = s > bestScore
                || (s == bestScore && myDist  < bestMyDist)
                || (s == bestScore && myDist  == bestMyDist && oppDist > bestOppDist);
            if (better) {
                bestScore   = s;
                bestMyDist  = myDist;
                bestOppDist = oppDist;
                best        = move;
            }
            alpha = Math.max(alpha, bestScore);
        }
        return best;
    }

    private int minimaxTT(GameState state, int depth, int alpha, int beta,
                          boolean maximizing, long deadline, int ply) {
        // TT probe
        long hash    = tt.hash(state);
        int  ttScore = tt.probe(hash, depth, alpha, beta);
        if (ttScore != TranspositionTable.MISS) return ttScore;

        // Evaluate current position
        int s = evaluate(state);
        if (Math.abs(s) >= WIN) {
            int score = s > 0 ? s + depth : s - depth;
            tt.store(hash, score, depth, TranspositionTable.EXACT);
            return score;
        }
        if (depth == 0 || System.currentTimeMillis() >= deadline) {
            tt.store(hash, s, depth, TranspositionTable.EXACT);
            return s;
        }

        List<Move> moves = candidates(state);
        promoteKillers(moves, ply);

        int origAlpha = alpha;
        int origBeta  = beta;

        if (maximizing) {
            int max = Integer.MIN_VALUE + 1;
            for (Move move : moves) {
                max = Math.max(max, minimaxTT(apply(state, move), depth - 1, alpha, beta, false, deadline, ply + 1));
                alpha = Math.max(alpha, max);
                if (beta <= alpha) {
                    recordKiller(move, ply);
                    break;
                }
            }
            int flag = max <= origAlpha ? TranspositionTable.UPPER
                     : max >= beta      ? TranspositionTable.LOWER
                     :                    TranspositionTable.EXACT;
            tt.store(hash, max, depth, flag);
            return max;
        } else {
            int min = Integer.MAX_VALUE;
            for (Move move : moves) {
                min = Math.min(min, minimaxTT(apply(state, move), depth - 1, alpha, beta, true, deadline, ply + 1));
                beta = Math.min(beta, min);
                if (beta <= alpha) {
                    recordKiller(move, ply);
                    break;
                }
            }
            int flag = min >= origBeta  ? TranspositionTable.LOWER
                     : min <= alpha     ? TranspositionTable.UPPER
                     :                    TranspositionTable.EXACT;
            tt.store(hash, min, depth, flag);
            return min;
        }
    }

    // ── Killer moves ──────────────────────────────────────────────────────────

    private void recordKiller(Move move, int ply) {
        if (!(move instanceof WallMove)) return; // only track wall killers; pawn ordering is BFS-based
        if (ply >= killers.length) return;
        if (!move.equals(killers[ply][0])) {
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = move;
        }
    }

    private void promoteKillers(List<Move> moves, int ply) {
        if (ply >= killers.length) return;
        // Promote killer[1] first so killer[0] ends up at index 0 after both insertions
        for (int k = MAX_KILLERS - 1; k >= 0; k--) {
            Move killer = killers[ply][k];
            if (killer == null) continue;
            int idx = moves.indexOf(killer);
            if (idx > 0) {
                moves.remove(idx);
                moves.add(0, killer);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PawnMove racingMove(GameState state, List<Move> moves, long deadline) {
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer());
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer().opponent());
        if (myDist <= 0 || myDist >= oppDist) return null;
        PawnMove best    = null;
        int      bestDist = myDist;
        for (Move m : moves) {
            if (System.currentTimeMillis() >= deadline) break;
            if (m instanceof PawnMove pm) {
                int d = pathChecker.shortestPathWithJumps(apply(state, pm), aiPlayer());
                if (d < bestDist) { bestDist = d; best = pm; }
            }
        }
        return best;
    }

    private java.util.Comparator<Move> rootOrder(GameState state) {
        Player scored = state.getCurrentPlayer() == aiPlayer() ? aiPlayer() : aiPlayer().opponent();
        return (a, b) -> {
            boolean aIsPawn = a instanceof PawnMove;
            boolean bIsPawn = b instanceof PawnMove;
            if (aIsPawn != bIsPawn) return aIsPawn ? -1 : 1;
            if (!aIsPawn) return 0;
            int dA = pathChecker.shortestPathWithJumps(apply(state, a), scored);
            int dB = pathChecker.shortestPathWithJumps(apply(state, b), scored);
            return Integer.compare(dA, dB);
        };
    }

    private boolean nearEither(Wall wall, Position p1, Position p2) {
        return chebyshev(wall, p1) <= WALL_PRUNE_DIST
            || chebyshev(wall, p2) <= WALL_PRUNE_DIST;
    }

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }
}

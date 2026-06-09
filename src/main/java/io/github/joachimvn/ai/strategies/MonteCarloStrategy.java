package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * MCTS with UCB1 tree policy, BFS-guided rollouts, combined-impact wall pruning,
 * inter-turn tree reuse, and RAVE (Rapid Action Value Estimation).
 *
 * <p><b>Tree reuse:</b> after each decision the subtree rooted at the chosen move
 * is retained. When the opponent responds, their move is matched against that
 * subtree's children; if found the matched node becomes the new root, carrying over
 * all accumulated visit/win statistics. This effectively 2–3× the number of useful
 * simulations available per turn.
 *
 * <p><b>RAVE:</b> each node maintains per-action RAVE statistics that accumulate
 * whenever a move appears anywhere in a simulation, not only as the first move. UCB1
 * scores are blended with RAVE scores via a β that decays as the node's own visit
 * count grows, shifting trust from RAVE to first-hand evidence over time.
 *
 * <p><b>Tree policy:</b> UCB1-RAVE selection on fully-expanded nodes; one random
 * child expanded per iteration.
 *
 * <p><b>Rollout:</b> BFS-optimal pawn advance with sampled wall placement evaluated
 * by BFS impact. Periodic early cutoff via BFS when one player has a dominant lead.
 *
 * <p><b>Final selection:</b> most-visited child (robust best), BFS tiebreaker.
 */
public class MonteCarloStrategy implements Strategy {

    private static final long   TIME_LIMIT_MS    = 950;
    private static final int    MAX_ROLLOUT_DEPTH = 60;
    private static final int    WALL_PRUNE_DIST   = 5;
    private static final int    MAX_WALL_CANDS    = 10;
    private static final float  WALL_PROB         = 0.25f;
    private static final int    WALL_SAMPLES      = 3;
    private static final double UCB_C             = Math.sqrt(2);
    private static final int    CUTOFF_INTERVAL   = 10;
    private static final int    CUTOFF_MARGIN     = 5;
    private static final double RAVE_K            = 500.0; // blend constant: higher = trust RAVE longer

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private final Random        random      = new Random();

    private Position prevPosition = null;
    private Node     savedRoot    = null; // tree reuse: subtree from previous turn

    // Reusable buffer to avoid per-rollout allocation
    private final List<Move> rolloutMoves = new ArrayList<>(MAX_ROLLOUT_DEPTH);

    // ── MCTS node ────────────────────────────────────────────────────────────

    private static final class Node {
        final GameState  state;
        final Move       move;
        Node             parent;
        final Player     mover;
        final List<Node> children  = new ArrayList<>();
        List<Move>       untried;
        int wins;
        int visits;
        // RAVE: per-action statistics for moves observed anywhere in rollouts below this node
        final Map<Move, int[]> rave = new HashMap<>(); // move -> [wins, visits]

        Node(GameState state, Move move, Node parent, Player mover, List<Move> candidates) {
            this.state   = state;
            this.move    = move;
            this.parent  = parent;
            this.mover   = mover;
            this.untried = new ArrayList<>(candidates);
        }

        boolean isFullyExpanded() { return untried.isEmpty(); }
    }

    // ── Construction ─────────────────────────────────────────────────────────

    public MonteCarloStrategy(Player aiPlayer) { this.aiPlayer = aiPlayer; }

    @Override public String displayName() { return "Monte Carlo"; }
    @Override public String description() {
        return "MCTS with UCB1-RAVE, BFS rollouts, combined-impact wall pruning, and inter-turn tree reuse";
    }

    // ── Main entry ───────────────────────────────────────────────────────────

    @Override
    public Move decide(GameState state) {
        List<Move> candidates = buildCandidates(state);
        if (candidates.isEmpty()) throw new NoSuchElementException("No legal moves");

        Node root    = resolveRoot(state, candidates);
        long deadline = System.currentTimeMillis() + TIME_LIMIT_MS;

        while (System.currentTimeMillis() < deadline) {
            Node node = selectAndExpand(root, deadline);
            if (node == null) break;
            boolean aiWon = rollout(node.state, deadline);
            backpropagate(node, aiWon, rolloutMoves);
        }

        Move chosen = robustBest(root, state);
        persistSubtree(root, chosen);
        prevPosition = state.getPawnPosition(aiPlayer);
        return chosen;
    }

    // ── Tree reuse ────────────────────────────────────────────────────────────

    /**
     * Attempts to find the current game state inside the saved subtree from the
     * previous turn. If found, reuses that node (and all its accumulated statistics)
     * as the new root. Otherwise starts a fresh tree.
     */
    private Node resolveRoot(GameState state, List<Move> candidates) {
        if (savedRoot != null) {
            for (Node child : savedRoot.children) {
                if (child.state.equals(state)) {
                    child.parent = null; // detach to allow GC of the rest of the tree
                    return child;
                }
            }
        }
        return new Node(state, null, null, aiPlayer.opponent(), candidates);
    }

    /** Saves the subtree rooted at the chosen move's child for the next turn. */
    private void persistSubtree(Node root, Move chosen) {
        savedRoot = null;
        for (Node child : root.children) {
            if (chosen.equals(child.move)) {
                child.parent = null;
                savedRoot = child;
                return;
            }
        }
    }

    // ── Selection + expansion ────────────────────────────────────────────────

    private Node selectAndExpand(Node root, long deadline) {
        Node node = root;
        while (node.isFullyExpanded() && !node.children.isEmpty()) {
            if (System.currentTimeMillis() >= deadline) return null;
            node = ucb1RaveSelect(node);
        }
        if (!node.untried.isEmpty()) {
            Move move  = node.untried.remove(random.nextInt(node.untried.size()));
            GameState next = apply(node.state, move);
            Player mover   = node.state.getCurrentPlayer();
            Node child     = new Node(next, move, node, mover, buildCandidates(next));
            node.children.add(child);
            return child;
        }
        return node;
    }

    private Node ucb1RaveSelect(Node parent) {
        double logN = Math.log(parent.visits);
        Node   best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Node child : parent.children) {
            double uct = (double) child.wins / child.visits
                       + UCB_C * Math.sqrt(logN / child.visits);
            int[]  rv  = parent.rave.get(child.move);
            double score;
            if (rv != null && rv[1] > 0) {
                double raveWr = (double) rv[0] / rv[1];
                // β decays toward 0 as child.visits grows — shifts trust from RAVE to UCT
                double beta = Math.sqrt(RAVE_K / (3.0 * child.visits + RAVE_K));
                score = (1.0 - beta) * uct + beta * raveWr;
            } else {
                score = uct;
            }
            if (score > bestScore) { bestScore = score; best = child; }
        }
        return best;
    }

    // ── Backpropagation with RAVE ─────────────────────────────────────────────

    private void backpropagate(Node node, boolean aiWon, List<Move> simMoves) {
        Set<Move> seen = new HashSet<>(simMoves);
        for (Node cur = node; cur != null; cur = cur.parent) {
            cur.visits++;
            if ((cur.mover == aiPlayer) == aiWon) cur.wins++;
            // Credit RAVE statistics for every move in this simulation that is a known
            // action at this node (i.e. it belongs to a child or an untried move).
            updateRave(cur, seen, aiWon);
        }
    }

    private void updateRave(Node node, Set<Move> seen, boolean aiWon) {
        for (Node child : node.children) {
            if (seen.contains(child.move)) {
                int[] rv = node.rave.computeIfAbsent(child.move, k -> new int[2]);
                rv[1]++;
                if ((node.mover == aiPlayer) == aiWon) rv[0]++;
            }
        }
        for (Move m : node.untried) {
            if (seen.contains(m)) {
                int[] rv = node.rave.computeIfAbsent(m, k -> new int[2]);
                rv[1]++;
                if ((node.mover == aiPlayer) == aiWon) rv[0]++;
            }
        }
    }

    // ── Rollout ──────────────────────────────────────────────────────────────

    private boolean rollout(GameState state, long deadline) {
        rolloutMoves.clear();
        GameState cur = state;
        for (int d = 0; d < MAX_ROLLOUT_DEPTH; d++) {
            if (hasWon(cur, aiPlayer))            return true;
            if (hasWon(cur, aiPlayer.opponent())) return false;
            if (System.currentTimeMillis() >= deadline) break;

            if (d % CUTOFF_INTERVAL == 0) {
                int myD  = pathChecker.shortestPathWithJumps(cur, aiPlayer);
                int oppD = pathChecker.shortestPathWithJumps(cur, aiPlayer.opponent());
                if (myD  <= 1)                    return true;
                if (oppD <= 1)                    return false;
                if (myD  <= oppD - CUTOFF_MARGIN) return true;
                if (oppD <= myD  - CUTOFF_MARGIN) return false;
            }

            Move move = rolloutMove(cur);
            rolloutMoves.add(move);
            cur = apply(cur, move);
        }
        int fd1 = pathChecker.shortestPathWithJumps(cur, aiPlayer);
        int fd2 = pathChecker.shortestPathWithJumps(cur, aiPlayer.opponent());
        return fd1 < fd2;
    }

    private Move rolloutMove(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        if (state.getWallCount(current) > 0 && random.nextFloat() < WALL_PROB) {
            Move wm = sampleWallMove(state, opp);
            if (wm != null) return wm;
        }
        return bestAdvance(state, current);
    }

    private Move sampleWallMove(GameState state, Player opp) {
        int oppDist   = pathChecker.shortestPathWithJumps(state, opp);
        WallMove best = null;
        int bestImpact = 1;
        for (int attempt = 0; attempt < WALL_SAMPLES * 4; attempt++) {
            Wall.Orientation ori = random.nextBoolean()
                ? Wall.Orientation.HORIZONTAL : Wall.Orientation.VERTICAL;
            int r = random.nextInt(GameState.BOARD_SIZE - 1);
            int c = random.nextInt(GameState.BOARD_SIZE - 1);
            Wall w = new Wall(ori, r, c);
            if (state.hasWall(w) || quickOverlap(state, w)) continue;
            int newOppDist = pathChecker.shortestPathWithJumps(state.withWallMove(w), opp);
            if (newOppDist == Integer.MAX_VALUE) continue;
            int impact = newOppDist - oppDist;
            if (impact > bestImpact) { bestImpact = impact; best = new WallMove(w); }
        }
        return best;
    }

    private static boolean quickOverlap(GameState state, Wall w) {
        if (w.orientation() == Wall.Orientation.HORIZONTAL) {
            return state.hasWall(new Wall(Wall.Orientation.HORIZONTAL, w.row(), w.col() - 1))
                || state.hasWall(new Wall(Wall.Orientation.HORIZONTAL, w.row(), w.col() + 1))
                || state.hasWall(new Wall(Wall.Orientation.VERTICAL,   w.row(), w.col()));
        } else {
            return state.hasWall(new Wall(Wall.Orientation.VERTICAL,   w.row() - 1, w.col()))
                || state.hasWall(new Wall(Wall.Orientation.VERTICAL,   w.row() + 1, w.col()))
                || state.hasWall(new Wall(Wall.Orientation.HORIZONTAL, w.row(),     w.col()));
        }
    }

    private Move bestAdvance(GameState state, Player current) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) return pawns.get(0);
        PawnMove best = null;
        int bestDist  = Integer.MAX_VALUE;
        for (PawnMove pm : pawns) {
            int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return best != null ? best : pawns.get(0);
    }

    // ── Candidate generation ─────────────────────────────────────────────────

    private List<Move> buildCandidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));
        if (state.getWallCount(current) <= 0) return moves;

        int oppDist = pathChecker.shortestPathWithJumps(state, opp);
        int myDist  = pathChecker.shortestPathWithJumps(state, current);
        Position p1 = state.getPawnPosition(Player.ONE);
        Position p2 = state.getPawnPosition(Player.TWO);

        List<WallMove> legal = validator.getLegalWallMoves(state);
        List<int[]>    scored = new ArrayList<>();
        for (int i = 0; i < legal.size(); i++) {
            Wall w = legal.get(i).wall();
            if (!nearEither(w, p1, p2)) continue;
            GameState after = state.withWallMove(w);
            int oppImpact = pathChecker.shortestPathWithJumps(after, opp) - oppDist;
            int myGain    = myDist - pathChecker.shortestPathWithJumps(after, current);
            int score     = oppImpact + myGain;
            if (score > 0) scored.add(new int[]{score, i});
        }
        scored.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
        int limit = Math.min(scored.size(), MAX_WALL_CANDS);
        for (int k = 0; k < limit; k++) moves.add(legal.get(scored.get(k)[1]));
        return moves;
    }

    // ── Final move selection ──────────────────────────────────────────────────

    private Move robustBest(Node root, GameState state) {
        if (root.children.isEmpty()) return bfsBestAdvance(state);
        Node best = null;
        for (Node child : root.children) {
            if (best == null
                    || child.visits > best.visits
                    || (child.visits == best.visits
                        && myDistAfter(state, child.move) < myDistAfter(state, best.move))) {
                best = child;
            }
        }
        Move chosen = best.move;
        if (chosen instanceof PawnMove pm && pm.target().equals(prevPosition)) {
            Move alt = bfsBestAdvance(state);
            if (alt != null) chosen = alt;
        }
        return chosen;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Move bfsBestAdvance(GameState state) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) return null;
        PawnMove best = null;
        int bestDist  = Integer.MAX_VALUE;
        for (PawnMove pm : pawns) {
            if (!pm.target().equals(prevPosition)) {
                int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), aiPlayer);
                if (d < bestDist) { bestDist = d; best = pm; }
            }
        }
        return best != null ? best : pawns.get(0);
    }

    private int myDistAfter(GameState state, Move move) {
        return pathChecker.shortestPathWithJumps(apply(state, move), aiPlayer);
    }

    private boolean hasWon(GameState state, Player player) {
        return state.getPawnPosition(player).row() == player.goalRow();
    }

    private boolean nearEither(Wall wall, Position p1, Position p2) {
        return chebyshev(wall, p1) <= WALL_PRUNE_DIST
            || chebyshev(wall, p2) <= WALL_PRUNE_DIST;
    }

    private static int chebyshev(Wall wall, Position p) {
        return Math.max(Math.abs(wall.row() - p.row()), Math.abs(wall.col() - p.col()));
    }

    private GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

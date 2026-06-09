package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * MCTS with UCB1 tree policy, BFS-guided rollouts, and combined-impact wall pruning.
 *
 * <p><b>Tree policy:</b> UCB1 selection on fully-expanded nodes; one random child
 * expanded per iteration. {@code wins} at every node is tracked from the mover's
 * perspective so the adversarial structure is correct at all depths, not just root.
 *
 * <p><b>Rollout:</b> goal-row greedy advance (no per-step BFS, ~10× faster) with
 * sampled wall placement evaluated by BFS impact. Periodic early cutoff via BFS
 * when one player has a dominant lead. Terminal evaluated by BFS distance.
 *
 * <p><b>Wall pruning:</b> score = {@code oppImpact + myGain} (opponent path
 * lengthened + own path shortened). Walls with {@code score > 0} qualify, so
 * walls that don't slow the opponent but protect your own advance are included.
 * Chebyshev proximity filter (≤ 5) bounds candidate set size.
 *
 * <p><b>Final selection:</b> most-visited child (robust best), with BFS tiebreaker.
 */
public class MonteCarloStrategy implements Strategy {

    private static final long   TIME_LIMIT_MS       = 950;
    private static final int    MAX_ROLLOUT_DEPTH   = 60;
    private static final int    WALL_PRUNE_DIST     = 5;
    private static final int    MAX_WALL_CANDIDATES = 10;
    private static final float  WALL_PROB           = 0.25f;
    private static final int    WALL_SAMPLES        = 3;
    private static final double UCB_C               = Math.sqrt(2);
    private static final int    CUTOFF_INTERVAL     = 10;
    private static final int    CUTOFF_MARGIN       = 5;

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private final Random        random      = new Random();

    private Position prevPosition = null;

    // ── MCTS node ────────────────────────────────────────────────────────────

    private static final class Node {
        final GameState  state;
        final Move       move;
        final Node       parent;
        final Player     mover;
        final List<Node> children = new ArrayList<>();
        List<Move>       untried;
        int wins;
        int visits;

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
        return "MCTS with UCB1 tree policy, BFS rollouts, and combined-impact wall pruning";
    }

    // ── Main entry ───────────────────────────────────────────────────────────

    @Override
    public Move decide(GameState state) {
        List<Move> candidates = buildCandidates(state);
        if (candidates.isEmpty()) throw new NoSuchElementException("No legal moves");

        // Root mover = opponent because whoever moved TO reach this state was them
        Node root    = new Node(state, null, null, aiPlayer.opponent(), candidates);
        long deadline = System.currentTimeMillis() + TIME_LIMIT_MS;

        while (System.currentTimeMillis() < deadline) {
            Node node = selectAndExpand(root, deadline);
            if (node == null) break;
            boolean aiWon = rollout(node.state, deadline);
            backpropagate(node, aiWon);
        }

        Move chosen = robustBest(root, state);
        prevPosition = state.getPawnPosition(aiPlayer);
        return chosen;
    }

    // ── Selection + expansion ────────────────────────────────────────────────

    private Node selectAndExpand(Node root, long deadline) {
        Node node = root;

        // Walk down via UCB1 until an unexpanded or childless node
        while (node.isFullyExpanded() && !node.children.isEmpty()) {
            if (System.currentTimeMillis() >= deadline) return null;
            node = ucb1Select(node);
        }

        // Expand one random untried child
        if (!node.untried.isEmpty()) {
            Move move      = node.untried.remove(random.nextInt(node.untried.size()));
            GameState next = apply(node.state, move);
            Player mover   = node.state.getCurrentPlayer();
            Node child     = new Node(next, move, node, mover, buildCandidates(next));
            node.children.add(child);
            return child;
        }

        return node; // terminal node
    }

    private Node ucb1Select(Node parent) {
        double logN = Math.log(parent.visits);
        Node   best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Node child : parent.children) {
            double score = (double) child.wins / child.visits
                         + UCB_C * Math.sqrt(logN / child.visits);
            if (score > bestScore) { bestScore = score; best = child; }
        }
        return best;
    }

    // ── Backpropagation ──────────────────────────────────────────────────────

    private void backpropagate(Node node, boolean aiWon) {
        for (Node cur = node; cur != null; cur = cur.parent) {
            cur.visits++;
            // wins from this node's mover's perspective
            if ((cur.mover == aiPlayer) == aiWon) cur.wins++;
        }
    }

    // ── Rollout ──────────────────────────────────────────────────────────────

    private boolean rollout(GameState state, long deadline) {
        GameState cur = state;
        for (int d = 0; d < MAX_ROLLOUT_DEPTH; d++) {
            if (hasWon(cur, aiPlayer))            return true;
            if (hasWon(cur, aiPlayer.opponent())) return false;
            if (System.currentTimeMillis() >= deadline) break;

            // Periodic BFS early cutoff — cheap on 9×9
            if (d % CUTOFF_INTERVAL == 0) {
                int myD  = pathChecker.shortestPathWithJumps(cur, aiPlayer);
                int oppD = pathChecker.shortestPathWithJumps(cur, aiPlayer.opponent());
                if (myD  <= 1)                   return true;
                if (oppD <= 1)                   return false;
                if (myD  <= oppD - CUTOFF_MARGIN) return true;
                if (oppD <= myD  - CUTOFF_MARGIN) return false;
            }

            cur = rolloutStep(cur);
        }
        int fd1 = pathChecker.shortestPathWithJumps(cur, aiPlayer);
        int fd2 = pathChecker.shortestPathWithJumps(cur, aiPlayer.opponent());
        return fd1 < fd2;
    }

    private GameState rolloutStep(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();

        if (state.getWallCount(current) > 0 && random.nextFloat() < WALL_PROB) {
            GameState wState = sampleWallStep(state, opp);
            if (wState != null) return wState;
        }

        return rolloutAdvance(state, current);
    }

    /**
     * Fast wall sampler for rollouts. Generates random wall positions, rejects overlaps via
     * O(1) hash lookups (no BFS), then evaluates impact for the few that pass. Avoids the
     * ~512-BFS cost of getLegalWallMoves while still finding impactful walls.
     * Walls that would disconnect a player are detected by MAX_VALUE BFS result and skipped.
     */
    private GameState sampleWallStep(GameState state, Player opp) {
        if (state.getWallCount(state.getCurrentPlayer()) <= 0) return null;
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
            if (newOppDist == Integer.MAX_VALUE) continue; // would disconnect opponent
            int impact = newOppDist - oppDist;
            if (impact > bestImpact) { bestImpact = impact; best = new WallMove(w); }
        }
        return best != null ? state.withWallMove(best.wall()) : null;
    }

    /** Fast overlap check — mirrors MoveValidator logic without the BFS path check. */
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

    /**
     * BFS-optimal advance: picks the pawn move with the shortest path to goal,
     * correctly routing around walls. Goal-row greedy is faster but gets stuck
     * behind walls and sends the pawn into dead ends — BFS is necessary here.
     */
    private GameState rolloutAdvance(GameState state, Player current) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) return state;
        PawnMove best = null;
        int bestDist  = Integer.MAX_VALUE;
        for (PawnMove pm : pawns) {
            int d = pathChecker.shortestPathWithJumps(state.withPawnMove(pm.target()), current);
            if (d < bestDist) { bestDist = d; best = pm; }
        }
        return best != null ? state.withPawnMove(best.target()) : state.withPawnMove(pawns.get(0).target());
    }

    // ── Candidate generation ─────────────────────────────────────────────────

    /**
     * Pawn moves + top wall candidates scored by {@code oppImpact + myGain}.
     * Including walls where myGain > 0 captures defensive placements that protect
     * your own path even when they don't immediately slow the opponent.
     */
    private List<Move> buildCandidates(GameState state) {
        Player current = state.getCurrentPlayer();
        Player opp     = current.opponent();
        List<Move> moves = new ArrayList<>(validator.getLegalPawnMoves(state));
        if (state.getWallCount(current) <= 0) return moves;

        int oppDist = pathChecker.shortestPathWithJumps(state, opp);
        int myDist  = pathChecker.shortestPathWithJumps(state, current);
        Position p1 = state.getPawnPosition(Player.ONE);
        Position p2 = state.getPawnPosition(Player.TWO);

        List<WallMove> legalWalls = validator.getLegalWallMoves(state);
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < legalWalls.size(); i++) {
            Wall w = legalWalls.get(i).wall();
            if (!nearEither(w, p1, p2)) continue;
            GameState after = state.withWallMove(w);
            int oppImpact = pathChecker.shortestPathWithJumps(after, opp) - oppDist;
            int myGain    = myDist - pathChecker.shortestPathWithJumps(after, current);
            int score     = oppImpact + myGain;
            if (score > 0) scored.add(new int[]{score, i});
        }

        scored.sort(Comparator.comparingInt((int[] e) -> e[0]).reversed());
        int limit = Math.min(scored.size(), MAX_WALL_CANDIDATES);
        for (int k = 0; k < limit; k++) moves.add(legalWalls.get(scored.get(k)[1]));
        return moves;
    }

    // ── Final move selection ──────────────────────────────────────────────────

    private Move robustBest(Node root, GameState state) {
        if (root.children.isEmpty()) {
            // No tree built — fall back to best pawn move by BFS
            return bfsBestAdvance(state);
        }

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

        // Anti-oscillation: if chosen pawn move returns to previous position, use BFS advance
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

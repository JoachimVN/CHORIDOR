package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.*;

/**
 * Adversarial beam search: a fundamentally different tree exploration strategy from alpha-beta.
 *
 * <p>At each depth level all beam states are expanded, scored with a static evaluation,
 * and pruned to the top {@value #BEAM_SIZE}. Pruning alternates polarity:
 * <ul>
 *   <li>AI's plies — keep the {@value #BEAM_SIZE} <em>highest</em>-scored states.</li>
 *   <li>Opponent's plies — keep the {@value #BEAM_SIZE} <em>lowest</em>-scored states
 *       (opponent's best choices from AI's perspective).</li>
 * </ul>
 *
 * <p>Unlike alpha-beta, beam search prunes based on a static score at each intermediate
 * ply rather than game-tree values propagated from leaves. This means it can miss moves
 * that look locally bad but are globally good ("positional sacrifices") — but it can also
 * find tactically sharp lines that shallow minimax ignores because the tree was pruned
 * away by an unlucky node ordering.
 */
public class BeamSearchStrategy implements Strategy {

    private static final int BEAM_SIZE      = 12;
    private static final int BEAM_DEPTH     = 10;
    private static final int WALL_PRUNE_DIST = 4;
    private static final int WIN = 4 * GameState.BOARD_SIZE * GameState.BOARD_SIZE + 1;

    private record Entry(GameState state, Move root) {}

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public BeamSearchStrategy(Player aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    @Override public String displayName() { return "Beam Search"; }
    @Override public String description() {
        return "Keeps the top-K positions at each depth, alternating maximize/minimize — different from alpha-beta";
    }

    @Override
    public Move decide(GameState state) {
        List<Move> roots = candidates(state);
        if (roots.isEmpty()) throw new NoSuchElementException("No legal moves");

        // Initial beam: AI has already moved (root expansions)
        List<Entry> beam = new ArrayList<>();
        for (Move m : roots) beam.add(new Entry(apply(state, m), m));

        // Prune initial beam: AI's move → keep HIGHEST scored
        if (beam.size() > BEAM_SIZE) {
            beam.sort(Comparator.comparingInt(e -> -eval(e.state)));
            beam = new ArrayList<>(beam.subList(0, BEAM_SIZE));
        }

        for (int depth = 1; depth < BEAM_DEPTH; depth++) {
            List<Entry> expanded = new ArrayList<>();
            for (Entry e : beam) {
                if (isTerminal(e.state)) { expanded.add(e); continue; }
                for (Move m : candidates(e.state)) {
                    expanded.add(new Entry(apply(e.state, m), e.root));
                }
            }
            if (expanded.isEmpty()) break;

            // depth 1 = opponent just moved → minimize (keep LOWEST for AI)
            // depth 2 = AI moves → maximize (keep HIGHEST for AI)
            boolean opponentPly = (depth % 2 == 1);
            if (opponentPly) {
                expanded.sort(Comparator.comparingInt(e -> eval(e.state)));          // ascending
            } else {
                expanded.sort(Comparator.comparingInt(e -> -eval(e.state)));         // descending
            }
            beam = new ArrayList<>(expanded.subList(0, Math.min(BEAM_SIZE, expanded.size())));
        }

        // Return the root move of the highest-scoring final beam state
        return beam.stream()
                   .max(Comparator.comparingInt(e -> eval(e.state)))
                   .map(e -> e.root)
                   .orElse(roots.get(0));
    }

    private int eval(GameState state) {
        if (hasWon(state, aiPlayer))            return  WIN;
        if (hasWon(state, aiPlayer.opponent())) return -WIN;
        int myDist  = pathChecker.shortestPathWithJumps(state, aiPlayer);
        int oppDist = pathChecker.shortestPathWithJumps(state, aiPlayer.opponent());
        if (myDist  == Integer.MAX_VALUE) return -WIN;
        if (oppDist == Integer.MAX_VALUE) return  WIN;
        return oppDist - myDist;
    }

    private boolean isTerminal(GameState state) {
        return hasWon(state, aiPlayer) || hasWon(state, aiPlayer.opponent());
    }

    private boolean hasWon(GameState state, Player player) {
        return state.getPawnPosition(player).row() == player.goalRow();
    }

    private List<Move> candidates(GameState state) {
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

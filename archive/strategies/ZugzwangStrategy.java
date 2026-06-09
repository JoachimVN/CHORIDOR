package io.github.joachimvn.ai.strategies;

import io.github.joachimvn.ai.Strategy;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Two-ply evaluation that accounts for the opponent's best pawn response.
 *
 * <p>Every candidate move is scored by the resulting tempo gap <em>after</em> the
 * opponent replies optimally with a pawn advance:
 * <pre>
 *   score(move) = myDistAfter − opponentBestDistAfterTheirReply
 * </pre>
 * A negative or zero score means we come out ahead even after the opponent responds.
 *
 * <p>The key difference from race-based strategies: when a direct advance would hand
 * the opponent a reply that closes the gap (or flips the lead), a lateral "pass" move
 * — stepping sideways without losing distance — can score better because it denies that
 * reply sequence. This mirrors the zugzwang concept: sometimes being forced to move is
 * genuinely a disadvantage, and the right play is to move cheaply rather than usefully.
 *
 * <p>Walls are evaluated by the same two-ply metric, scored as
 * {@code (oppDistAfterWall − oppBestReplyDist) − myDistChange}, so they must both slow
 * the opponent <em>and</em> survive a free pawn reply to be worth playing.
 */
public class ZugzwangStrategy implements Strategy {

    private final Player        aiPlayer;
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();

    public ZugzwangStrategy(Player aiPlayer) { this.aiPlayer = aiPlayer; }

    @Override public String displayName() { return "Zugzwang"; }
    @Override public String description() {
        return "Two-ply lookahead that accounts for the opponent's best reply — sidesteps when advancing hands tempo away";
    }

    @Override
    public Move decide(GameState state) {
        List<PawnMove> pawns = validator.getLegalPawnMoves(state);
        if (pawns.isEmpty()) throw new NoSuchElementException("No legal moves");

        Move bestMove  = pawns.get(0);
        int  bestScore = Integer.MAX_VALUE;

        // Evaluate pawn moves
        for (PawnMove pm : pawns) {
            int score = twoPlyscore(state, pm);
            if (score < bestScore) { bestScore = score; bestMove = pm; }
        }

        // Evaluate wall moves if walls remain — only consider if improving on best pawn
        if (state.getWallCount(aiPlayer) > 0) {
            for (WallMove wm : validator.getLegalWallMoves(state)) {
                int score = twoPlyscore(state, wm);
                // Walls only worth it if they clearly beat the best pawn option
                if (score < bestScore - 1) { bestScore = score; bestMove = wm; }
            }
        }

        return bestMove;
    }

    // ── Two-ply evaluation ────────────────────────────────────────────────────

    /**
     * Score = myDistAfterMove − opponentBestPawnReplyDist.
     * Lower is better: negative means we're ahead after the opponent's best response.
     */
    private int twoPlyscore(GameState state, Move move) {
        GameState after = apply(state, move);

        int myNewDist = pathChecker.shortestPathWithJumps(after, aiPlayer);
        if (myNewDist == Integer.MAX_VALUE) return Integer.MAX_VALUE; // disconnected

        // Opponent's best pawn reply
        int oppBestDist = pathChecker.shortestPathWithJumps(after, aiPlayer.opponent());
        List<PawnMove> oppPawns = validator.getLegalPawnMoves(after);
        for (PawnMove opm : oppPawns) {
            int d = pathChecker.shortestPathWithJumps(after.withPawnMove(opm.target()), aiPlayer.opponent());
            if (d < oppBestDist) oppBestDist = d;
        }

        return myNewDist - oppBestDist;
    }

    private static GameState apply(GameState state, Move move) {
        return switch (move) {
            case PawnMove(var t) -> state.withPawnMove(t);
            case WallMove(var w) -> state.withWallMove(w);
        };
    }
}

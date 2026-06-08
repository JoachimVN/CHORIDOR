package io.github.joachimvn.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.core.model.Wall;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Snapshot of a completed game, used to record notable matches for the post-tournament summary.
 *
 * @param loserFinalDist BFS distance the loser still had to cover when the winner finished;
 *                       lower = closer game (used for "best game" tracking)
 */
public record GameRecord(
    Difficulty d1,
    Difficulty d2,
    Difficulty winner,
    int moveCount,
    int wallCount,
    int loserFinalDist,
    GameState finalState,
    ConcurrentHashMap<Wall, Player> wallOwners
) {}

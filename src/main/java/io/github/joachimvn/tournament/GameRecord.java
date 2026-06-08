package io.github.joachimvn.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.core.model.Wall;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Snapshot of a completed game, used to record notable matches
 * (shortest, longest, most walls placed) for the post-tournament summary.
 */
public record GameRecord(
    Difficulty d1,
    Difficulty d2,
    Difficulty winner,
    int moveCount,
    int wallCount,
    GameState finalState,
    ConcurrentHashMap<Wall, Player> wallOwners
) {}

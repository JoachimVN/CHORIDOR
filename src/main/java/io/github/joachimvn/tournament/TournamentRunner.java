package io.github.joachimvn.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.GameEngine;
import io.github.joachimvn.core.rules.PathChecker;

import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Headless tournament engine with live-state exposure and pause support.
 *
 * <p>Two threading domains:
 * <ul>
 *   <li><b>Worker threads</b> — run games, write to {@link #liveStates} and
 *       {@link #liveMatchups} via ConcurrentHashMap (lock-free for the FX thread).</li>
 *   <li><b>JavaFX thread</b> — owns {@link #results} and {@link #matchupWins}; all
 *       mutations go through {@code Platform.runLater}.</li>
 * </ul>
 */
public class TournamentRunner {

    private static final int MAX_MOVES = 300;

    // ── FX-thread-only ──────────────────────────────────────────────────────
    private final Map<Difficulty, int[]> results = new LinkedHashMap<>();
    private final Map<Difficulty, Map<Difficulty, Integer>> matchupWins = new LinkedHashMap<>();

    // ── Shared: written by workers, read by FX AnimationTimer ───────────────
    private final ConcurrentHashMap<Integer, GameState>                       liveStates     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Difficulty[]>                    liveMatchups   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Wall, Player>> liveWallOwners = new ConcurrentHashMap<>();
    /** Populated just before a game is removed from liveStates; lets the UI show a winner overlay. */
    private final ConcurrentHashMap<Integer, Difficulty>                      liveWinners     = new ConcurrentHashMap<>();
    /** Final game state captured before liveStates is cleared — lets the UI show the winning move. */
    private final ConcurrentHashMap<Integer, GameState>                       finalGameStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Wall, Player>> finalWallOwners = new ConcurrentHashMap<>();

    /** Notable games updated atomically by worker threads as games complete. */
    private final AtomicReference<GameRecord> shortestGame = new AtomicReference<>();
    private final AtomicReference<GameRecord> longestGame  = new AtomicReference<>();
    private final AtomicReference<GameRecord> bestGame     = new AtomicReference<>();

    // ── Control ─────────────────────────────────────────────────────────────
    private final AtomicBoolean  cancelled   = new AtomicBoolean(false);
    private volatile boolean     paused      = false;
    private final AtomicInteger  nextGameId  = new AtomicInteger(0);
    private ExecutorService      pool;

    // ── Accessors ────────────────────────────────────────────────────────────
    public Map<Difficulty, int[]>                    getResults()      { return results; }
    public Map<Difficulty, Map<Difficulty, Integer>> getMatchupWins()  { return matchupWins; }
    public ConcurrentHashMap<Integer, GameState>                       getLiveStates()     { return liveStates; }
    public ConcurrentHashMap<Integer, Difficulty[]>                    getLiveMatchups()   { return liveMatchups; }
    public ConcurrentHashMap<Integer, ConcurrentHashMap<Wall, Player>> getLiveWallOwners() { return liveWallOwners; }
    public ConcurrentHashMap<Integer, Difficulty>                      getLiveWinners()     { return liveWinners; }
    public ConcurrentHashMap<Integer, GameState>                       getFinalGameStates() { return finalGameStates; }
    public ConcurrentHashMap<Integer, ConcurrentHashMap<Wall, Player>> getFinalWallOwners() { return finalWallOwners; }
    public GameRecord getShortestGame() { return shortestGame.get(); }
    public GameRecord getLongestGame()  { return longestGame.get(); }
    public GameRecord getBestGame()     { return bestGame.get(); }
    public boolean isPaused()  { return paused; }

    public int totalGames(List<Difficulty> strategies) {
        int n = strategies.size();
        return n * (n - 1);
    }

    public void pause()  { paused = true; }
    public void resume() { paused = false; }

    /**
     * @param onProgress   called on the FX thread after each game (done, total)
     * @param onGameResult called on the FX thread with (winner, loser) after each game
     * @param onComplete   called on the FX thread when all games finish (not called if cancelled)
     */
    public void start(List<Difficulty> strategies,
                      BiConsumer<Integer, Integer>     onProgress,
                      BiConsumer<Difficulty, Difficulty> onGameResult,
                      Runnable                          onComplete) {
        cancelled.set(false);
        paused = false;
        liveStates.clear();
        liveMatchups.clear();
        liveWallOwners.clear();
        liveWinners.clear();
        finalGameStates.clear();
        finalWallOwners.clear();
        shortestGame.set(null);
        longestGame.set(null);
        bestGame.set(null);
        results.clear();
        matchupWins.clear();
        for (Difficulty a : strategies) {
            results.put(a, new int[]{0, 0});
            Map<Difficulty, Integer> row = new LinkedHashMap<>();
            for (Difficulty b : strategies) { if (a != b) row.put(b, 0); }
            matchupWins.put(a, row);
        }

        List<Difficulty[]> matchups = buildMatchups(strategies);
        int total = matchups.size();
        AtomicInteger completed = new AtomicInteger(0);

        // Cap concurrent games at half the strategy count so each AI gets reasonable CPU time.
        int threads = Math.max(1, Math.min(strategies.size() / 2,
                                           Runtime.getRuntime().availableProcessors() - 1));
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "tournament-worker");
            t.setDaemon(true);
            return t;
        });

        for (Difficulty[] matchup : matchups) {
            pool.submit(() -> {
                if (cancelled.get()) return;
                int gameId = nextGameId.getAndIncrement();
                Difficulty winner = playGame(gameId, matchup[0], matchup[1]);
                int done = completed.incrementAndGet();
                Platform.runLater(() -> {
                    if (cancelled.get()) return;
                    if (winner != null) {
                        Difficulty loser = winner == matchup[0] ? matchup[1] : matchup[0];
                        results.get(winner)[0]++;
                        results.get(loser)[1]++;
                        matchupWins.get(winner).merge(loser, 1, Integer::sum);
                        onGameResult.accept(winner, loser);
                    }
                    onProgress.accept(done, total);
                    if (done == total) onComplete.run();
                });
            });
        }
        pool.shutdown();
    }

    public void cancel() {
        cancelled.set(true);
        paused = false;
        if (pool != null) pool.shutdownNow();
        liveStates.clear();
        liveMatchups.clear();
        liveWallOwners.clear();
    }

    private static List<Difficulty[]> buildMatchups(List<Difficulty> strategies) {
        List<Difficulty[]> matchups = new ArrayList<>();
        for (Difficulty a : strategies) {
            for (Difficulty b : strategies) {
                if (a != b) matchups.add(new Difficulty[]{a, b});
            }
        }
        Collections.shuffle(matchups);
        return matchups;
    }

    private Difficulty playGame(int gameId, Difficulty d1, Difficulty d2) {
        GameEngine  engine      = new GameEngine();
        PathChecker pathChecker = new PathChecker();
        var s1 = d1.createStrategy(Player.ONE);
        var s2 = d2.createStrategy(Player.TWO);
        GameState state = new GameState();
        ConcurrentHashMap<Wall, Player> wallOwners = new ConcurrentHashMap<>();
        liveStates.put(gameId, state);
        liveMatchups.put(gameId, new Difficulty[]{d1, d2});
        liveWallOwners.put(gameId, wallOwners);

        Difficulty winner = null;
        int moveCount = 0;
        try {
            Player current = null;
            for (int m = 0; m < MAX_MOVES; m++) {
                while (paused && !cancelled.get()) {
                    try { Thread.sleep(50); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (cancelled.get()) return null;
                if (engine.isGameOver(state)) break;

                current = state.getCurrentPlayer();
                var strategy = current == Player.ONE ? s1 : s2;
                try {
                    var move = strategy.decide(state);
                    if (move instanceof WallMove wm) wallOwners.put(wm.wall(), current);
                    state = engine.applyMove(state, move);
                    liveStates.put(gameId, state);
                    moveCount++;
                } catch (Exception e) {
                    winner = current == Player.ONE ? d2 : d1;
                    return winner;
                }
            }

            Optional<Player> w = engine.getWinner(state);
            winner = w.isPresent() ? (w.get() == Player.ONE ? d1 : d2)
                   : (pathChecker.shortestPathWithJumps(state, Player.ONE)
                   <= pathChecker.shortestPathWithJumps(state, Player.TWO) ? d1 : d2);
            return winner;
        } finally {
            // Expose final state and winner BEFORE clearing live maps.
            if (winner != null) {
                liveWinners.put(gameId, winner);
                finalGameStates.put(gameId, state);
                finalWallOwners.put(gameId, wallOwners);

                // Update notable game records atomically
                int walls = state.getWalls().size();
                final int mc = moveCount;
                Player loserPlayer = winner == d1 ? Player.TWO : Player.ONE;
                int loserDist = pathChecker.shortestPathWithJumps(state, loserPlayer);
                GameRecord rec = new GameRecord(d1, d2, winner, moveCount, walls,
                                                loserDist, state, new ConcurrentHashMap<>(wallOwners));
                shortestGame.updateAndGet(cur ->
                    (cur == null || mc < cur.moveCount()) ? rec : cur);
                longestGame.updateAndGet(cur ->
                    (cur == null || mc > cur.moveCount()) ? rec : cur);
                // Best game = composite score: tactical richness × closeness of finish.
                // Rewards walls placed and game length; divides by (loserDist+1) so close
                // finishes score higher, but a short low-wall game can't win by luck.
                double score = (walls * 3.0 + mc) / (loserDist + 1.0);
                bestGame.updateAndGet(cur -> {
                    if (cur == null) return rec;
                    double curScore = (cur.wallCount() * 3.0 + cur.moveCount()) / (cur.loserFinalDist() + 1.0);
                    return score > curScore ? rec : cur;
                });
            }
            liveStates.remove(gameId);
            liveMatchups.remove(gameId);
            liveWallOwners.remove(gameId);
        }
    }
}

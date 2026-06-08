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
import java.util.function.BiConsumer;

/**
 * Runs a round-robin tournament between a set of AI strategies.
 *
 * <p>Each pair of strategies plays two games (swapping P1 and P2) so no strategy has a
 * permanent first-mover advantage. Games run in parallel on a thread pool; all result
 * mutations and progress callbacks are dispatched onto the JavaFX Application Thread via
 * {@code Platform.runLater} so callers can safely update UI in the listener.
 *
 * <p>The results map ({@link #getResults()}) is owned by the JavaFX thread and must only
 * be read from there.
 */
public class TournamentRunner {


    private static final int MAX_MOVES = 300;

    /** [wins, losses] per strategy — FX-thread-only. */
    private final Map<Difficulty, int[]> results = new LinkedHashMap<>();
    private ExecutorService pool;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public Map<Difficulty, int[]> getResults() { return results; }

    public int totalGames(List<Difficulty> strategies) {
        int n = strategies.size();
        return n * (n - 1);
    }

    /** @param onProgress called after each game (done, total) on the FX thread
     *  @param onComplete called when all games finish on the FX thread (not called if cancelled) */
    public void start(List<Difficulty> strategies,
                      BiConsumer<Integer, Integer> onProgress, Runnable onComplete) {
        cancelled.set(false);
        results.clear();
        for (Difficulty d : strategies) results.put(d, new int[]{0, 0});

        List<Difficulty[]> matchups = buildMatchups(strategies);
        int total = matchups.size();
        AtomicInteger completed = new AtomicInteger(0);

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "tournament-worker");
            t.setDaemon(true);
            return t;
        });

        for (Difficulty[] matchup : matchups) {
            pool.submit(() -> {
                if (cancelled.get()) return;
                Difficulty winner = playGame(matchup[0], matchup[1]);
                int done = completed.incrementAndGet();
                Platform.runLater(() -> {
                    if (cancelled.get()) return;
                    if (winner != null) {
                        Difficulty loser = winner == matchup[0] ? matchup[1] : matchup[0];
                        results.get(winner)[0]++;
                        results.get(loser)[1]++;
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
        if (pool != null) pool.shutdownNow();
    }

    private static List<Difficulty[]> buildMatchups(List<Difficulty> strategies) {
        List<Difficulty[]> matchups = new ArrayList<>();
        for (Difficulty a : strategies) {
            for (Difficulty b : strategies) {
                if (a != b) matchups.add(new Difficulty[]{a, b});
            }
        }
        Collections.shuffle(matchups); // random order → fairer live standings
        return matchups;
    }

    static Difficulty playGame(Difficulty d1, Difficulty d2) {
        GameEngine engine = new GameEngine();
        PathChecker pathChecker = new PathChecker();
        var s1 = d1.createStrategy(Player.ONE);
        var s2 = d2.createStrategy(Player.TWO);
        GameState state = new GameState();

        for (int moves = 0; moves < MAX_MOVES; moves++) {
            if (engine.isGameOver(state)) break;
            Player current = state.getCurrentPlayer();
            var strategy = current == Player.ONE ? s1 : s2;
            try {
                state = engine.applyMove(state, strategy.decide(state));
            } catch (Exception e) {
                return current == Player.ONE ? d2 : d1; // forfeit on error
            }
        }

        Optional<Player> winner = engine.getWinner(state);
        if (winner.isPresent()) return winner.get() == Player.ONE ? d1 : d2;

        // Move-limit tie-break: closer player wins
        return pathChecker.shortestPathWithJumps(state, Player.ONE)
             <= pathChecker.shortestPathWithJumps(state, Player.TWO) ? d1 : d2;
    }
}

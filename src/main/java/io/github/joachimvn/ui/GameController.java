package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.GameEngine;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.strategy.Strategy;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class GameController {

    // Index by Player.ordinal(). null = human.
    private final Strategy[] playerStrategies = new Strategy[2];
    private final String[]   playerNames      = {"Player 1", "Player 2"};

    // Which player the human controls — only meaningful in HvAI mode.
    private Player humanPlayer = Player.ONE;

    private GameState state = new GameState();
    private final MoveValidator validator = new MoveValidator();
    private final GameEngine engine = new GameEngine();
    private List<PawnMove> legalPawnMoves;
    private Wall previewWall;
    private Wall lastCandidate;
    private boolean gameOver;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<Wall, Player> wallOwners = new LinkedHashMap<>();
    private final AudioClip moveSound = new AudioClip(
        getClass().getResource("/audio/sfx/Move.wav").toExternalForm());
    private final AudioClip wallSound = new AudioClip(
        getClass().getResource("/audio/sfx/Wall.wav").toExternalForm());
    private final AudioClip jumpSound = new AudioClip(
        getClass().getResource("/audio/sfx/Jump.wav").toExternalForm());
    private final AudioClip winSound = new AudioClip(
        getClass().getResource("/audio/sfx/Win.wav").toExternalForm());
    private final AudioClip lossSound = new AudioClip(
        getClass().getResource("/audio/sfx/Loss.wav").toExternalForm());
    private final AudioClip selectSound = new AudioClip(
        getClass().getResource("/audio/sfx/Select.wav").toExternalForm());
    private boolean muted = false;

    private volatile boolean aiThinking = false;
    private final AtomicInteger generation = new AtomicInteger(0);

    public GameController() {
        refreshLegalMoves();
    }

    public void addListener(Runnable r) { listeners.add(r); }

    public GameState getState()              { return state; }
    public List<PawnMove> getLegalPawnMoves(){ return legalPawnMoves; }
    public Wall getPreviewWall()             { return previewWall; }
    public boolean isGameOver()              { return gameOver; }
    public boolean isAiThinking()            { return aiThinking; }
    public boolean isVsAi()                  { return !isHuman(Player.ONE) || !isHuman(Player.TWO); }
    public boolean isAiVsAi()               { return !isHuman(Player.ONE) && !isHuman(Player.TWO); }
    public Player  getHumanPlayer()          { return humanPlayer; }
    public Player  getWallOwner(Wall wall)   { return wallOwners.get(wall); }
    public Player  getWinner()               { return engine.getWinner(state).orElse(null); }

    private boolean isHuman(Player p) { return playerStrategies[p.ordinal()] == null; }

    public String getPlayerName(Player player) {
        if (isAiVsAi()) return playerNames[player.ordinal()];
        if (isVsAi())   return isHuman(player) ? "You" : "AI";
        return "Player " + label(player);
    }

    public String getStatusText() {
        if (gameOver) {
            Player winner = engine.getWinner(state).orElseThrow();
            String name = getPlayerName(winner);
            return "You".equals(name) ? "You win!" : name + " wins!";
        }
        Player p = state.getCurrentPlayer();
        String name = getPlayerName(p);
        String prefix = "You".equals(name) ? "Your" : name + "'s";
        return prefix + " turn";
    }

    public void replay() { reset(); }

    public void startGame(Strategy p1Strategy, Strategy p2Strategy, Player humanPlayer,
                          String p1Name, String p2Name) {
        playerStrategies[0] = p1Strategy;
        playerStrategies[1] = p2Strategy;
        this.humanPlayer = humanPlayer;
        playerNames[0] = p1Name;
        playerNames[1] = p2Name;
        reset();
    }

    public void updatePreviewWall(Wall candidate) {
        if (gameOver || aiThinking) return;
        if (Objects.equals(candidate, lastCandidate)) return;
        lastCandidate = candidate;
        Wall valid = (candidate != null && validator.isWallLegal(state, candidate)) ? candidate : null;
        if (!Objects.equals(valid, previewWall)) {
            previewWall = valid;
            notifyListeners();
        }
    }

    public void clickCell(int row, int col) {
        if (gameOver || aiThinking || !isHuman(state.getCurrentPlayer())) return;
        Position target = new Position(row, col);
        boolean legal = legalPawnMoves.stream().anyMatch(m -> m.target().equals(target));
        if (!legal) return;
        Position from = state.getPawnPosition(state.getCurrentPlayer());
        boolean isJump = Math.abs(target.row() - from.row()) + Math.abs(target.col() - from.col()) > 1;
        state = engine.applyMove(state, new PawnMove(target));
        clearPreview();
        play(isJump ? jumpSound : moveSound);
        afterMove();
    }

    public void clickWall() {
        if (gameOver || aiThinking || previewWall == null || !isHuman(state.getCurrentPlayer())) return;
        wallOwners.put(previewWall, state.getCurrentPlayer());
        state = engine.applyMove(state, new WallMove(previewWall));
        clearPreview();
        play(wallSound);
        afterMove();
    }

    private void reset() {
        generation.incrementAndGet();
        aiThinking = false;
        play(selectSound);
        state = new GameState();
        gameOver = false;
        wallOwners.clear();
        clearPreview();
        refreshLegalMoves();
        notifyListeners();
        scheduleAiMove();
    }

    private void afterMove() {
        gameOver = engine.isGameOver(state);
        if (gameOver) {
            Player winner = engine.getWinner(state).orElseThrow();
            boolean humanLost = isVsAi() && !isAiVsAi() && !isHuman(winner);
            play(humanLost ? lossSound : winSound);
        }
        refreshLegalMoves();
        notifyListeners();
        scheduleAiMove();
    }

    private void scheduleAiMove() {
        if (gameOver) return;
        Player current = state.getCurrentPlayer();
        Strategy strategy = playerStrategies[current.ordinal()];
        if (strategy == null) return;
        aiThinking = true;
        notifyListeners();
        int gen = generation.get();
        GameState snapshot = state;
        Thread t = new Thread(() -> {
            Move move = strategy.decide(snapshot);
            Platform.runLater(() -> {
                if (generation.get() != gen) return;
                aiThinking = false;
                applyAiMove(move);
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void applyAiMove(Move move) {
        if (gameOver) return;
        switch (move) {
            case PawnMove pm -> {
                Position from = state.getPawnPosition(state.getCurrentPlayer());
                boolean isJump = Math.abs(pm.target().row() - from.row())
                               + Math.abs(pm.target().col() - from.col()) > 1;
                state = engine.applyMove(state, pm);
                clearPreview();
                play(isJump ? jumpSound : moveSound);
            }
            case WallMove wm -> {
                wallOwners.put(wm.wall(), state.getCurrentPlayer());
                state = engine.applyMove(state, wm);
                clearPreview();
                play(wallSound);
            }
        }
        afterMove();
    }

    private void clearPreview() {
        previewWall = null;
        lastCandidate = null;
    }

    private void refreshLegalMoves() {
        legalPawnMoves = gameOver ? List.of() : validator.getLegalPawnMoves(state);
    }

    public boolean isMuted() { return muted; }

    public void toggleMute() {
        if (muted) {
            muted = false;
            selectSound.play();
        } else {
            selectSound.play();
            muted = true;
        }
    }

    public void playLossSound() { play(lossSound); }

    private void play(AudioClip clip) { if (!muted) clip.play(); }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private static String label(Player p) {
        return p == Player.ONE ? "1" : "2";
    }
}

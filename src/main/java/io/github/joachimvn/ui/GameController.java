package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.GameEngine;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.strategy.MinimaxStrategy;
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

    private static final Player AI_PLAYER = Player.TWO;

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

    private Strategy aiStrategy = null;
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
    public boolean isVsAi()                  { return aiStrategy != null; }
    public Player getWallOwner(Wall wall)    { return wallOwners.get(wall); }

    public String getStatusText() {
        if (aiThinking) return "Thinking...";
        if (gameOver) {
            Player winner = engine.getWinner(state).orElseThrow();
            return "Player " + label(winner) + " wins!";
        }
        Player p = state.getCurrentPlayer();
        return "Player " + label(p) + "'s turn  —  Walls: " + state.getWallCount(p);
    }

    public void setVsAi(boolean vsAi) {
        aiStrategy = vsAi ? new MinimaxStrategy(AI_PLAYER) : null;
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
        if (gameOver || aiThinking) return;
        if (aiStrategy != null && state.getCurrentPlayer() == AI_PLAYER) return;
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
        if (gameOver || aiThinking || previewWall == null) return;
        if (aiStrategy != null && state.getCurrentPlayer() == AI_PLAYER) return;
        wallOwners.put(previewWall, state.getCurrentPlayer());
        state = engine.applyMove(state, new WallMove(previewWall));
        clearPreview();
        play(wallSound);
        afterMove();
    }

    public void reset() {
        generation.incrementAndGet();
        aiThinking = false;
        play(selectSound);
        state = new GameState();
        gameOver = false;
        wallOwners.clear();
        clearPreview();
        refreshLegalMoves();
        notifyListeners();
    }

    private void afterMove() {
        gameOver = engine.isGameOver(state);
        if (gameOver) play(winSound);
        refreshLegalMoves();
        notifyListeners();
        scheduleAiMove();
    }

    private void scheduleAiMove() {
        if (gameOver || aiStrategy == null || state.getCurrentPlayer() != AI_PLAYER) return;
        aiThinking = true;
        notifyListeners();
        int gen = generation.get();
        GameState snapshot = state;
        Thread t = new Thread(() -> {
            Move move = aiStrategy.decide(snapshot);
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

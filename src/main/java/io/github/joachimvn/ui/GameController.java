package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.GameEngine;
import io.github.joachimvn.core.rules.MoveValidator;
import javafx.scene.media.AudioClip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GameController {

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

    public GameController() {
        refreshLegalMoves();
    }

    public void addListener(Runnable r) { listeners.add(r); }

    public GameState getState()              { return state; }
    public List<PawnMove> getLegalPawnMoves(){ return legalPawnMoves; }
    public Wall getPreviewWall()             { return previewWall; }
    public boolean isGameOver()              { return gameOver; }
    public Player getWallOwner(Wall wall)    { return wallOwners.get(wall); }

    public String getStatusText() {
        if (gameOver) {
            Player winner = engine.getWinner(state).orElseThrow();
            return "Player " + label(winner) + " wins!";
        }
        Player p = state.getCurrentPlayer();
        return "Player " + label(p) + "'s turn  —  Walls: " + state.getWallCount(p);
    }

    public void updatePreviewWall(Wall candidate) {
        if (gameOver) return;
        if (Objects.equals(candidate, lastCandidate)) return;
        lastCandidate = candidate;
        Wall valid = (candidate != null && validator.isWallLegal(state, candidate)) ? candidate : null;
        if (!Objects.equals(valid, previewWall)) {
            previewWall = valid;
            notifyListeners();
        }
    }

    public void clickCell(int row, int col) {
        if (gameOver) return;
        Position target = new Position(row, col);
        boolean legal = legalPawnMoves.stream().anyMatch(m -> m.target().equals(target));
        if (!legal) return;
        state = engine.applyMove(state, new PawnMove(target));
        clearPreview();
        afterMove();
    }

    public void clickWall() {
        if (gameOver || previewWall == null) return;
        wallOwners.put(previewWall, state.getCurrentPlayer());
        state = engine.applyMove(state, new WallMove(previewWall));
        clearPreview();
        afterMove();
    }

    public void reset() {
        state = new GameState();
        gameOver = false;
        wallOwners.clear();
        clearPreview();
        refreshLegalMoves();
        notifyListeners();
    }

    private void afterMove() {
        moveSound.play();
        gameOver = engine.isGameOver(state);
        refreshLegalMoves();
        notifyListeners();
    }

    private void clearPreview() {
        previewWall = null;
        lastCandidate = null;
    }

    private void refreshLegalMoves() {
        legalPawnMoves = gameOver ? List.of() : validator.getLegalPawnMoves(state);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private static String label(Player p) {
        return p == Player.ONE ? "1" : "2";
    }
}

package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.*;
import io.github.joachimvn.core.rules.GameEngine;
import io.github.joachimvn.core.rules.MoveValidator;
import io.github.joachimvn.core.rules.PathChecker;
import io.github.joachimvn.ai.Strategy;
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

    private GameState state = new GameState();
    private final MoveValidator validator   = new MoveValidator();
    private final PathChecker   pathChecker = new PathChecker();
    private final GameEngine engine = new GameEngine();
    private List<PawnMove> legalPawnMoves;
    private Wall previewWall;
    private Wall lastCandidate;
    private boolean gameOver;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<Wall, Player> wallOwners = new LinkedHashMap<>();

    // Snapshot after every move (index 0 = starting position), for the look-back review.
    // GameState is immutable, so storing references is safe. reviewCursor == -1 means live play.
    private final List<GameState> history = new ArrayList<>();
    private int reviewCursor = -1;

    // Standard Quoridor notation for each ply (index 0 = notation of move 1, etc.).
    private final List<String> moveLog = new ArrayList<>();
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

    public GameState getState()              { return isReviewing() ? history.get(reviewCursor) : state; }
    public List<PawnMove> getLegalPawnMoves(){ return legalPawnMoves; }
    public Wall getPreviewWall()             { return previewWall; }
    public boolean isGameOver()              { return gameOver; }
    public boolean isAiThinking()            { return aiThinking; }
    public boolean isVsAi()                  { return !isHuman(Player.ONE) || !isHuman(Player.TWO); }
    public boolean isAiVsAi()               { return !isHuman(Player.ONE) && !isHuman(Player.TWO); }
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

    public void startGame(Strategy p1Strategy, Strategy p2Strategy, String p1Name, String p2Name) {
        playerStrategies[0] = p1Strategy;
        playerStrategies[1] = p2Strategy;
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
        PawnMove pm = new PawnMove(target);
        state = engine.applyMove(state, pm);
        clearPreview();
        play(isJump ? jumpSound : moveSound);
        afterMove(pm);
    }

    public void clickWall() {
        if (gameOver || aiThinking || previewWall == null || !isHuman(state.getCurrentPlayer())) return;
        WallMove wm = new WallMove(previewWall);
        wallOwners.put(previewWall, state.getCurrentPlayer());
        state = engine.applyMove(state, wm);
        clearPreview();
        play(wallSound);
        afterMove(wm);
    }

    private void reset() {
        generation.incrementAndGet();
        aiThinking = false;
        play(selectSound);
        state = new GameState();
        gameOver = false;
        wallOwners.clear();
        history.clear();
        history.add(state);
        moveLog.clear();
        reviewCursor = -1;
        clearPreview();
        refreshLegalMoves();
        notifyListeners();
        scheduleAiMove();
    }

    private void afterMove(Move move) {
        history.add(state);
        moveLog.add(toNotation(move));
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
        afterMove(move);
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

    /** Play the selection click, e.g. for setup-screen controls. Honours the mute toggle. */
    public void playSelect() { play(selectSound); }

    private void play(AudioClip clip) { if (!muted) clip.play(); }

    // ── Game review (look-back) ───────────────────────────────────────────────

    public boolean isReviewing()     { return reviewCursor >= 0; }
    public int     getReviewCursor() { return reviewCursor; }
    /** Number of moves played in the current game (history has moveCount + 1 positions). */
    public int     getMoveCount()    { return Math.max(0, history.size() - 1); }

    /** Enter review at the final position. No-op until at least one move has been played. */
    public void enterReview() {
        if (history.size() <= 1) return;
        reviewCursor = history.size() - 1;
        notifyListeners();
    }

    public void exitReview() {
        if (!isReviewing()) return;
        reviewCursor = -1;
        notifyListeners();
    }

    public void reviewFirst() { seekReview(0); }
    public void reviewPrev()  { seekReview(reviewCursor - 1); }
    public void reviewNext()  { seekReview(reviewCursor + 1); }
    public void reviewLast()  { seekReview(history.size() - 1); }

    private void seekReview(int index) {
        if (!isReviewing()) return;
        int clamped = Math.clamp(index, 0, history.size() - 1);
        if (clamped == reviewCursor) return;
        reviewCursor = clamped;
        play(moveSound);
        notifyListeners();
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private static String label(Player p) {
        return p == Player.ONE ? "1" : "2";
    }

    // ── Quoridor notation ─────────────────────────────────────────────────────

    /**
     * Standard Quoridor notation. Columns a–i map left-to-right; rows 1–9 map bottom-to-top
     * (row 1 = our row 8 = player-one's start, row 9 = our row 0 = player-two's start).
     *
     * <ul>
     *   <li>Pawn to Position(r,c) → {@code a+c}{@code 9−r}  e.g. {@code e2}</li>
     *   <li>Wall(H, r, c)         → {@code a+c}{@code 8−r}h  e.g. {@code e4h}</li>
     *   <li>Wall(V, r, c)         → {@code a+c}{@code 8−r}v  e.g. {@code e4v}</li>
     * </ul>
     */
    public static String toNotation(Move move) {
        return switch (move) {
            case PawnMove(var target) ->
                String.valueOf((char)('a' + target.col())) + (9 - target.row());
            case WallMove(var wall) -> {
                char col = (char)('a' + wall.col());
                int  row = 8 - wall.row();
                char ori = wall.orientation() == Wall.Orientation.HORIZONTAL ? 'h' : 'v';
                yield "" + col + row + ori;
            }
        };
    }

    /**
     * The notation for the move that led to review position {@code cursor}, in chess style:
     * {@code "1. e2"} (red's first move) or {@code "1... e8"} (blue's first move).
     * Returns {@code null} at cursor 0 (start position — no move yet).
     */
    public String getMoveNotation(int cursor) {
        if (cursor <= 0 || cursor > moveLog.size()) return null;
        int turn = (cursor + 1) / 2;
        boolean redMoved = (cursor % 2) == 1;
        return turn + (redMoved ? ". " : "... ") + moveLog.get(cursor - 1);
    }

    /** Full game notation formatted like chess: {@code "1. e2 e8  2. e2h e7  ..."} */
    public String fullNotation() {
        if (moveLog.isEmpty()) return "(no moves yet)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moveLog.size(); i++) {
            if (i % 2 == 0) {
                if (i > 0) sb.append("  ");
                sb.append(i / 2 + 1).append(". ");
            } else {
                sb.append(' ');
            }
            sb.append(moveLog.get(i));
        }
        return sb.toString();
    }

    /**
     * Returns a compact, human-readable text representation of the current board — paste this
     * into a bug report or a conversation to get an exact diagnosis without relying on screenshots.
     *
     * Format: a 9×9 character grid (R=red pawn, B=blue pawn, ·=empty cell) followed by
     * horizontal-wall markers (═) below each row boundary and vertical-wall markers (║) between
     * column pairs, then a summary line.
     */
    public String boardStateText() {
        GameState s = getState();
        Position p1 = s.getPawnPosition(Player.ONE);
        Position p2 = s.getPawnPosition(Player.TWO);

        StringBuilder sb = new StringBuilder();
        sb.append("Moves: ").append(fullNotation()).append("\n\n");
        appendColumnHeader(sb);
        for (int r = 0; r < GameState.BOARD_SIZE; r++) {
            appendCellRow(sb, s, r, p1, p2);
            if (r < GameState.BOARD_SIZE - 1) appendWallRow(sb, s, r);
        }

        int d1 = pathChecker.shortestPathWithJumps(s, Player.ONE);
        int d2 = pathChecker.shortestPathWithJumps(s, Player.TWO);
        sb.append(String.format(
            "Red@(%d,%d) d=%d walls=%d  |  Blue@(%d,%d) d=%d walls=%d  |  Turn: %s%n",
            p1.row(), p1.col(), d1, s.getWallCount(Player.ONE),
            p2.row(), p2.col(), d2, s.getWallCount(Player.TWO),
            s.getCurrentPlayer() == Player.ONE ? "Red" : "Blue"));
        return sb.toString();
    }

    private static void appendColumnHeader(StringBuilder sb) {
        sb.append("   ");
        for (int c = 0; c < GameState.BOARD_SIZE; c++)
            sb.append(String.format(" %c  ", 'a' + c));
        sb.append("\n");
    }

    private static void appendCellRow(StringBuilder sb, GameState s, int r,
                                       Position p1, Position p2) {
        sb.append(String.format("%d  ", 9 - r));
        for (int c = 0; c < GameState.BOARD_SIZE; c++) {
            Position pos = new Position(r, c);
            char cell;
            if      (pos.equals(p1)) cell = 'R';
            else if (pos.equals(p2)) cell = 'B';
            else                     cell = '·';
            sb.append('[').append(cell).append(']');
            if (c < GameState.BOARD_SIZE - 1)
                sb.append(s.isEdgeBlocked(pos, new Position(r, c + 1)) ? '║' : ' ');
        }
        sb.append("\n");
    }

    private static void appendWallRow(StringBuilder sb, GameState s, int r) {
        sb.append("   ");
        for (int c = 0; c < GameState.BOARD_SIZE; c++) {
            sb.append(s.isEdgeBlocked(new Position(r, c), new Position(r + 1, c)) ? " ═══" : "    ");
            if (c < GameState.BOARD_SIZE - 1) sb.append(' ');
        }
        sb.append("\n");
    }
}

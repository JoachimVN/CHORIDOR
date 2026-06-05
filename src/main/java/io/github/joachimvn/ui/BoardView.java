package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.PawnMove;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.core.model.Position;
import io.github.joachimvn.core.model.Wall;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;

public class BoardView extends Canvas {

    // Board geometry
    private static final int    CELL = 54;
    private static final int    GAP  = 10;
    private static final int    STEP = CELL + GAP;
    private static final int    SIZE = GameState.BOARD_SIZE * CELL + (GameState.BOARD_SIZE - 1) * GAP;
    private static final int    MAX_ANCHOR     = GameState.BOARD_SIZE - 2;
    private static final int    WALL_LENGTH    = 2 * CELL + GAP;
    private static final int    GOAL_STRIP_H   = 3;

    // Ratios (applied to CELL)
    private static final double PAWN_PAD_RATIO  = 0.16;
    private static final double LEGAL_DOT_RATIO = 0.26;
    private static final double HOVER_DOT_RATIO = 0.36;
    private static final double PREVIEW_OPACITY = 0.45;
    private static final double STRIP_OPACITY   = 0.70;

    // Win overlay layout
    private static final double OVERLAY_OPACITY       = 0.60;
    private static final double OVERLAY_PANEL_ALPHA   = 0.97;
    private static final int    OVERLAY_W             = 340;
    private static final int    OVERLAY_H             = 120;
    private static final int    OVERLAY_RADIUS        = 12;
    private static final int    OVERLAY_TITLE_FONT_SZ = 28;
    private static final int    OVERLAY_HINT_FONT_SZ  = 14;
    private static final double OVERLAY_TITLE_Y_FRAC  = 0.42;
    private static final double OVERLAY_HINT_Y_FRAC   = 0.72;
    private static final String FONT_NAME             = "System";
    private static final String WHITE_HEX             = "#FFFFFF";

    // Colours — public so App can reference them as the single source of truth
    public static final Color P1_COLOR = Color.web("#9E4A40");
    public static final Color P2_COLOR = Color.web("#3E68A8");

    private static final Color BG         = Color.web("#0F1117");
    private static final Color CELL_CLR   = Color.web("#191C2A");
    private static final Color P1_STRIP   = P1_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    private static final Color P2_STRIP   = P2_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    private static final Color LEGAL_DOT  = Color.web(WHITE_HEX, 0.18);
    private static final Color HOVER_DOT  = Color.web(WHITE_HEX, 0.38);
    private static final Color HOVER_BG   = Color.web(WHITE_HEX, 0.06);

    private final GameController ctrl;
    private int hoverRow = -1;
    private int hoverCol = -1;

    public BoardView(GameController ctrl) {
        super(SIZE, SIZE);
        this.ctrl = ctrl;
        setOnMouseMoved(e -> {
            boolean changed = updateHoverCell(e.getX(), e.getY());
            ctrl.updatePreviewWall(wallCandidate(e.getX(), e.getY()));
            if (changed) refresh();
        });
        setOnMouseExited(e -> {
            boolean changed = (hoverRow != -1 || hoverCol != -1);
            hoverRow = -1;
            hoverCol = -1;
            ctrl.updatePreviewWall(null);
            if (changed) refresh();
        });
        setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));
    }

    public void refresh() {
        GraphicsContext gc  = getGraphicsContext2D();
        GameState state     = ctrl.getState();
        List<PawnMove> legal = ctrl.getLegalPawnMoves();

        gc.setFill(BG);
        gc.fillRect(0, 0, SIZE, SIZE);

        for (int r = 0; r < GameState.BOARD_SIZE; r++) {
            for (int c = 0; c < GameState.BOARD_SIZE; c++) {
                drawCell(gc, r, c, legal);
            }
        }

        for (Wall wall : state.getWalls()) {
            Player owner = ctrl.getWallOwner(wall);
            paintWall(gc, wall, owner == Player.ONE ? P1_COLOR : P2_COLOR, 1.0);
        }

        Wall preview = ctrl.getPreviewWall();
        if (preview != null) {
            Color base = state.getCurrentPlayer() == Player.ONE ? P1_COLOR : P2_COLOR;
            paintWall(gc, preview, base, PREVIEW_OPACITY);
        }

        paintPawn(gc, state.getPawnPosition(Player.ONE), P1_COLOR);
        paintPawn(gc, state.getPawnPosition(Player.TWO), P2_COLOR);

        if (ctrl.isGameOver()) paintWinOverlay(gc, ctrl.getStatusText());
    }

    private void drawCell(GraphicsContext gc, int r, int c, List<PawnMove> legal) {
        double x = c * (double) STEP;
        double y = r * (double) STEP;
        final int fr = r;
        final int fc = c;
        boolean isLegal   = legal.stream().anyMatch(m -> m.target().row() == fr && m.target().col() == fc);
        boolean isHovered = (r == hoverRow && c == hoverCol);

        gc.setFill(CELL_CLR);
        gc.fillRect(x, y, CELL, CELL);

        if (r == Player.ONE.goalRow()) {
            gc.setFill(P1_STRIP);
            gc.fillRect(x, y, CELL, GOAL_STRIP_H);
        } else if (r == Player.TWO.goalRow()) {
            gc.setFill(P2_STRIP);
            gc.fillRect(x, y + CELL - GOAL_STRIP_H, CELL, GOAL_STRIP_H);
        }

        if (isLegal) {
            if (isHovered) {
                gc.setFill(HOVER_BG);
                gc.fillRect(x, y, CELL, CELL);
                double d = CELL * HOVER_DOT_RATIO;
                gc.setFill(HOVER_DOT);
                gc.fillOval(x + (CELL - d) / 2, y + (CELL - d) / 2, d, d);
            } else {
                double d = CELL * LEGAL_DOT_RATIO;
                gc.setFill(LEGAL_DOT);
                gc.fillOval(x + (CELL - d) / 2, y + (CELL - d) / 2, d, d);
            }
        }
    }

    private void paintWall(GraphicsContext gc, Wall wall, Color color, double opacity) {
        gc.setFill(color.deriveColor(0, 1, 1, opacity));
        double x = (double) wall.col() * STEP;
        double y = (double) wall.row() * STEP;
        if (wall.orientation() == HORIZONTAL) gc.fillRect(x, y + CELL, WALL_LENGTH, GAP);
        else                                  gc.fillRect(x + CELL, y, GAP, WALL_LENGTH);
    }

    private void paintPawn(GraphicsContext gc, Position pos, Color color) {
        double pad = CELL * PAWN_PAD_RATIO;
        double x   = pos.col() * STEP + pad;
        double y   = pos.row() * STEP + pad;
        double d   = CELL - 2 * pad;
        gc.setFill(color);
        gc.fillOval(x, y, d, d);
    }

    private void paintWinOverlay(GraphicsContext gc, String winText) {
        gc.setFill(Color.rgb(0, 0, 0, OVERLAY_OPACITY));
        gc.fillRect(0, 0, SIZE, SIZE);

        double cx  = SIZE / 2.0;
        double cy  = SIZE / 2.0;
        double ox  = cx - OVERLAY_W / 2.0;
        double oy  = cy - OVERLAY_H / 2.0;

        gc.setFill(Color.web("#13151F", OVERLAY_PANEL_ALPHA));
        gc.fillRoundRect(ox, oy, OVERLAY_W, OVERLAY_H, OVERLAY_RADIUS, OVERLAY_RADIUS);
        gc.setStroke(Color.web("#2E3250"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(ox, oy, OVERLAY_W, OVERLAY_H, OVERLAY_RADIUS, OVERLAY_RADIUS);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font(FONT_NAME, FontWeight.BOLD, OVERLAY_TITLE_FONT_SZ));
        gc.setFill(Color.WHITE);
        gc.fillText(winText, cx, oy + OVERLAY_H * OVERLAY_TITLE_Y_FRAC);

        gc.setFont(Font.font(FONT_NAME, FontWeight.NORMAL, OVERLAY_HINT_FONT_SZ));
        gc.setFill(Color.web("#606880"));
        gc.fillText("Press New Game to play again", cx, oy + OVERLAY_H * OVERLAY_HINT_Y_FRAC);
    }

    private boolean updateHoverCell(double x, double y) {
        int c = (int)(x / STEP);
        int r = (int)(y / STEP);
        if (c >= GameState.BOARD_SIZE || r >= GameState.BOARD_SIZE) return setHover(-1, -1);
        double offX = x - c * STEP;
        double offY = y - r * STEP;
        boolean inCell = offX < CELL && offY < CELL;
        return setHover(inCell ? r : -1, inCell ? c : -1);
    }

    private boolean setHover(int r, int c) {
        if (r == hoverRow && c == hoverCol) return false;
        hoverRow = r;
        hoverCol = c;
        return true;
    }

    private void handleClick(double x, double y) {
        int col = (int)(x / STEP);
        int row = (int)(y / STEP);
        if (col >= GameState.BOARD_SIZE || row >= GameState.BOARD_SIZE) return;
        double offX = x - col * STEP;
        double offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < GameState.BOARD_SIZE - 1;
        boolean inHGap = offY >= CELL && row < GameState.BOARD_SIZE - 1;
        if (!inHGap && !inVGap) ctrl.clickCell(row, col);
        else {
            ctrl.updatePreviewWall(wallCandidate(x, y));
            ctrl.clickWall();
        }
    }

    private Wall wallCandidate(double x, double y) {
        int col = (int)(x / STEP);
        int row = (int)(y / STEP);
        if (col >= GameState.BOARD_SIZE || row >= GameState.BOARD_SIZE) return null;
        double offX = x - col * STEP;
        double offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < GameState.BOARD_SIZE - 1;
        boolean inHGap = offY >= CELL && row < GameState.BOARD_SIZE - 1;
        if (inHGap && !inVGap) return new Wall(HORIZONTAL, row, Math.min(col, MAX_ANCHOR));
        if (inVGap && !inHGap) return new Wall(VERTICAL,   Math.min(row, MAX_ANCHOR), col);
        return null;
    }
}

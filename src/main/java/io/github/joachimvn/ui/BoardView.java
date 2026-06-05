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

    // Design-size constants (the board at scale 1.0)
    private static final int    CELL = 54;
    private static final int    GAP  = 10;
    private static final int    STEP = CELL + GAP;
    private static final int    SIZE = GameState.BOARD_SIZE * CELL + (GameState.BOARD_SIZE - 1) * GAP;
    private static final int    MAX_ANCHOR  = GameState.BOARD_SIZE - 2;
    private static final int    GOAL_STRIP_H = 3;

    // Ratios (applied to scaled cell size)
    private static final double PAWN_PAD_RATIO  = 0.16;
    private static final double LEGAL_DOT_RATIO = 0.26;
    private static final double HOVER_DOT_RATIO = 0.36;
    private static final double PREVIEW_OPACITY = 0.45;
    private static final double STRIP_OPACITY   = 0.70;

    // Win overlay (at scale 1.0)
    private static final double OVERLAY_OPACITY      = 0.60;
    private static final double OVERLAY_PANEL_ALPHA  = 0.97;
    private static final int    OVERLAY_W            = 340;
    private static final int    OVERLAY_H            = 120;
    private static final int    OVERLAY_RADIUS       = 12;
    private static final int    OVERLAY_TITLE_SZ     = 28;
    private static final int    OVERLAY_HINT_SZ      = 14;
    private static final double OVERLAY_TITLE_Y_FRAC = 0.42;
    private static final double OVERLAY_HINT_Y_FRAC  = 0.72;
    private static final String FONT_NAME            = "System";
    private static final String WHITE_HEX            = "#FFFFFF";

    // Public colours — App uses these as the single source of truth
    public static final Color P1_COLOR = Color.web("#9E4A40");
    public static final Color P2_COLOR = Color.web("#3E68A8");

    private static final Color BG        = Color.web("#0F1117");
    private static final Color CELL_CLR  = Color.web("#191C2A");
    private static final Color P1_STRIP  = P1_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    private static final Color P2_STRIP  = P2_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    private static final Color LEGAL_DOT = Color.web(WHITE_HEX, 0.18);
    private static final Color HOVER_DOT = Color.web(WHITE_HEX, 0.38);
    private static final Color HOVER_BG  = Color.web(WHITE_HEX, 0.06);

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

    // ── Resize contract ──────────────────────────────────────────────────────

    @Override public boolean isResizable()            { return true; }
    @Override public double  prefWidth(double h)      { return SIZE; }
    @Override public double  prefHeight(double w)     { return SIZE; }
    @Override public double  minWidth(double h)       { return SIZE / 4.0; }
    @Override public double  minHeight(double w)      { return SIZE / 4.0; }
    @Override public double  maxWidth(double h)       { return Double.MAX_VALUE; }
    @Override public double  maxHeight(double w)      { return Double.MAX_VALUE; }

    @Override
    public void resize(double width, double height) {
        double side = Math.min(width, height);
        setWidth(side);
        setHeight(side);
        refresh();
    }

    /** Scale factor: current side / design side. */
    private double sc() { return getWidth() / SIZE; }

    // ── Rendering ────────────────────────────────────────────────────────────

    public void refresh() {
        double s    = sc();
        double cell = CELL * s;
        double gap  = GAP  * s;
        double step = STEP * s;
        double size = getWidth();

        GraphicsContext gc   = getGraphicsContext2D();
        GameState state      = ctrl.getState();
        List<PawnMove> legal = ctrl.getLegalPawnMoves();

        gc.setFill(BG);
        gc.fillRect(0, 0, size, size);

        for (int r = 0; r < GameState.BOARD_SIZE; r++) {
            for (int c = 0; c < GameState.BOARD_SIZE; c++) {
                drawCell(gc, r, c, legal, step, cell);
            }
        }

        for (Wall wall : state.getWalls()) {
            Player owner = ctrl.getWallOwner(wall);
            paintWall(gc, wall, owner == Player.ONE ? P1_COLOR : P2_COLOR, 1.0, step, cell, gap);
        }

        Wall preview = ctrl.getPreviewWall();
        if (preview != null) {
            Color base = state.getCurrentPlayer() == Player.ONE ? P1_COLOR : P2_COLOR;
            paintWall(gc, preview, base, PREVIEW_OPACITY, step, cell, gap);
        }

        paintPawn(gc, state.getPawnPosition(Player.ONE), P1_COLOR, step, cell);
        paintPawn(gc, state.getPawnPosition(Player.TWO), P2_COLOR, step, cell);

        if (ctrl.isGameOver()) paintWinOverlay(gc, ctrl.getStatusText(), s);
    }

    private void drawCell(GraphicsContext gc, int r, int c, List<PawnMove> legal,
                          double step, double cell) {
        double x = c * step;
        double y = r * step;
        final int fr = r;
        final int  fc = c;
        boolean isLegal   = legal.stream().anyMatch(m -> m.target().row() == fr && m.target().col() == fc);
        boolean isHovered = (r == hoverRow && c == hoverCol);

        gc.setFill(CELL_CLR);
        gc.fillRect(x, y, cell, cell);

        double stripH = GOAL_STRIP_H * cell / CELL;
        if (r == Player.ONE.goalRow()) {
            gc.setFill(P1_STRIP);
            gc.fillRect(x, y, cell, stripH);
        } else if (r == Player.TWO.goalRow()) {
            gc.setFill(P2_STRIP);
            gc.fillRect(x, y + cell - stripH, cell, stripH);
        }

        if (isLegal) {
            if (isHovered) {
                gc.setFill(HOVER_BG);
                gc.fillRect(x, y, cell, cell);
                double d = cell * HOVER_DOT_RATIO;
                gc.setFill(HOVER_DOT);
                gc.fillOval(x + (cell - d) / 2, y + (cell - d) / 2, d, d);
            } else {
                double d = cell * LEGAL_DOT_RATIO;
                gc.setFill(LEGAL_DOT);
                gc.fillOval(x + (cell - d) / 2, y + (cell - d) / 2, d, d);
            }
        }
    }

    private void paintWall(GraphicsContext gc, Wall wall, Color color, double opacity,
                           double step, double cell, double gap) {
        gc.setFill(color.deriveColor(0, 1, 1, opacity));
        double x = wall.col() * step;
        double y = wall.row() * step;
        double wallLen = 2 * cell + gap;
        if (wall.orientation() == HORIZONTAL) gc.fillRect(x, y + cell, wallLen, gap);
        else                                  gc.fillRect(x + cell, y, gap, wallLen);
    }

    private void paintPawn(GraphicsContext gc, Position pos, Color color,
                           double step, double cell) {
        double pad = cell * PAWN_PAD_RATIO;
        double x   = pos.col() * step + pad;
        double y   = pos.row() * step + pad;
        double d   = cell - 2 * pad;
        gc.setFill(color);
        gc.fillOval(x, y, d, d);
    }

    private void paintWinOverlay(GraphicsContext gc, String winText, double s) {
        double size = getWidth();
        gc.setFill(Color.rgb(0, 0, 0, OVERLAY_OPACITY));
        gc.fillRect(0, 0, size, size);

        double cx  = size / 2.0;
        double cy  = size / 2.0;
        double w   = OVERLAY_W * s;
        double h   = OVERLAY_H * s;
        double r   = OVERLAY_RADIUS * s;
        double ox  = cx - w / 2.0;
        double oy  = cy - h / 2.0;

        gc.setFill(Color.web("#13151F", OVERLAY_PANEL_ALPHA));
        gc.fillRoundRect(ox, oy, w, h, r, r);
        gc.setStroke(Color.web("#2E3250"));
        gc.setLineWidth(s);
        gc.strokeRoundRect(ox, oy, w, h, r, r);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font(FONT_NAME, FontWeight.BOLD, OVERLAY_TITLE_SZ * s));
        gc.setFill(Color.WHITE);
        gc.fillText(winText, cx, oy + h * OVERLAY_TITLE_Y_FRAC);

        gc.setFont(Font.font(FONT_NAME, FontWeight.NORMAL, OVERLAY_HINT_SZ * s));
        gc.setFill(Color.web("#606880"));
        gc.fillText("Press New Game to play again", cx, oy + h * OVERLAY_HINT_Y_FRAC);
    }

    // ── Hit testing ──────────────────────────────────────────────────────────

    private boolean updateHoverCell(double x, double y) {
        double step = STEP * sc();
        double cell = CELL * sc();
        int c = (int)(x / step);
        int r = (int)(y / step);
        if (c >= GameState.BOARD_SIZE || r >= GameState.BOARD_SIZE) return setHover(-1, -1);
        double offX = x - c * step;
        double offY = y - r * step;
        boolean inCell = offX < cell && offY < cell;
        return setHover(inCell ? r : -1, inCell ? c : -1);
    }

    private boolean setHover(int r, int c) {
        if (r == hoverRow && c == hoverCol) return false;
        hoverRow = r;
        hoverCol = c;
        return true;
    }

    private void handleClick(double x, double y) {
        double step = STEP * sc();
        double cell = CELL * sc();
        int col = (int)(x / step);
        int row = (int)(y / step);
        if (col >= GameState.BOARD_SIZE || row >= GameState.BOARD_SIZE) return;
        double offX = x - col * step;
        double offY = y - row * step;
        boolean inVGap = offX >= cell && col < GameState.BOARD_SIZE - 1;
        boolean inHGap = offY >= cell && row < GameState.BOARD_SIZE - 1;
        if (!inHGap && !inVGap) ctrl.clickCell(row, col);
        else {
            ctrl.updatePreviewWall(wallCandidate(x, y));
            ctrl.clickWall();
        }
    }

    private Wall wallCandidate(double x, double y) {
        double step = STEP * sc();
        double cell = CELL * sc();
        int col = (int)(x / step);
        int row = (int)(y / step);
        if (col >= GameState.BOARD_SIZE || row >= GameState.BOARD_SIZE) return null;
        double offX = x - col * step;
        double offY = y - row * step;
        boolean inVGap = offX >= cell && col < GameState.BOARD_SIZE - 1;
        boolean inHGap = offY >= cell && row < GameState.BOARD_SIZE - 1;
        if (inHGap && !inVGap) return new Wall(HORIZONTAL, row, Math.min(col, MAX_ANCHOR));
        if (inVGap && !inHGap) return new Wall(VERTICAL,   Math.min(row, MAX_ANCHOR), col);
        return null;
    }
}

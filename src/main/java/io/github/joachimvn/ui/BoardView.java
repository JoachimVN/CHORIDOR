package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.*;
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

    private static final int CELL = 54;
    private static final int GAP  = 10;
    private static final int STEP = CELL + GAP;
    private static final int SIZE = 9 * CELL + 8 * GAP;  // 566

    private static final int MAX_ANCHOR = GameState.BOARD_SIZE - 2;

    private static final Color BG       = Color.web("#0F1117");
    private static final Color CELL_CLR = Color.web("#191C2A");

    // Desaturated, darker — not cartoony; public so App can reference them directly
    public static final Color P1_COLOR = Color.web("#9E4A40");
    public static final Color P2_COLOR = Color.web("#3E68A8");

    private static final Color P1_STRIP  = Color.web("#9E4A40", 0.7);
    private static final Color P2_STRIP  = Color.web("#3E68A8", 0.7);
    private static final Color LEGAL_DOT = Color.web("#FFFFFF", 0.18);
    private static final Color HOVER_DOT = Color.web("#FFFFFF", 0.38);
    private static final Color HOVER_BG  = Color.web("#FFFFFF", 0.06);

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
        GraphicsContext gc = getGraphicsContext2D();
        GameState state     = ctrl.getState();
        List<PawnMove> legal = ctrl.getLegalPawnMoves();

        gc.setFill(BG);
        gc.fillRect(0, 0, SIZE, SIZE);

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                double x = c * STEP, y = r * STEP;
                final int fr = r, fc = c;
                boolean isLegal = legal.stream()
                    .anyMatch(m -> m.target().row() == fr && m.target().col() == fc);
                boolean isHovered = (r == hoverRow && c == hoverCol);

                gc.setFill(CELL_CLR);
                gc.fillRect(x, y, CELL, CELL);

                // Goal edge strips
                if (r == Player.ONE.goalRow()) {
                    gc.setFill(P1_STRIP);
                    gc.fillRect(x, y, CELL, 3);
                } else if (r == Player.TWO.goalRow()) {
                    gc.setFill(P2_STRIP);
                    gc.fillRect(x, y + CELL - 3, CELL, 3);
                }

                if (isLegal) {
                    if (isHovered) {
                        gc.setFill(HOVER_BG);
                        gc.fillRect(x, y, CELL, CELL);
                        double d = CELL * 0.36;
                        gc.setFill(HOVER_DOT);
                        gc.fillOval(x + (CELL - d) / 2, y + (CELL - d) / 2, d, d);
                    } else {
                        double d = CELL * 0.26;
                        gc.setFill(LEGAL_DOT);
                        gc.fillOval(x + (CELL - d) / 2, y + (CELL - d) / 2, d, d);
                    }
                }
            }
        }

        for (Wall wall : state.getWalls()) {
            Player owner = ctrl.getWallOwner(wall);
            paintWall(gc, wall, owner == Player.ONE ? P1_COLOR : P2_COLOR, 1.0);
        }

        Wall preview = ctrl.getPreviewWall();
        if (preview != null) {
            Color base = state.getCurrentPlayer() == Player.ONE ? P1_COLOR : P2_COLOR;
            paintWall(gc, preview, base, 0.45);
        }

        paintPawn(gc, state.getPawnPosition(Player.ONE), P1_COLOR);
        paintPawn(gc, state.getPawnPosition(Player.TWO), P2_COLOR);

        if (ctrl.isGameOver()) paintWinOverlay(gc, ctrl.getStatusText());
    }

    private void paintWall(GraphicsContext gc, Wall wall, Color color, double opacity) {
        gc.setFill(color.deriveColor(0, 1, 1, opacity));
        double x = wall.col() * STEP, y = wall.row() * STEP;
        if (wall.orientation() == HORIZONTAL) gc.fillRect(x, y + CELL, 2 * CELL + GAP, GAP);
        else                                  gc.fillRect(x + CELL, y, GAP, 2 * CELL + GAP);
    }

    private void paintPawn(GraphicsContext gc, Position pos, Color color) {
        double pad = CELL * 0.16;
        double x = pos.col() * STEP + pad, y = pos.row() * STEP + pad;
        double d = CELL - 2 * pad;
        gc.setFill(color);
        gc.fillOval(x, y, d, d);
    }

    private void paintWinOverlay(GraphicsContext gc, String winText) {
        gc.setFill(Color.rgb(0, 0, 0, 0.60));
        gc.fillRect(0, 0, SIZE, SIZE);
        double cx = SIZE / 2.0, cy = SIZE / 2.0;
        gc.setFill(Color.web("#13151F", 0.97));
        gc.fillRoundRect(cx - 170, cy - 60, 340, 120, 12, 12);
        gc.setStroke(Color.web("#2E3250"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(cx - 170, cy - 60, 340, 120, 12, 12);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("System", FontWeight.BOLD, 28));
        gc.setFill(Color.WHITE);
        gc.fillText(winText, cx, cy - 6);
        gc.setFont(Font.font("System", FontWeight.NORMAL, 14));
        gc.setFill(Color.web("#606880"));
        gc.fillText("Press New Game to play again", cx, cy + 26);
    }

    /** Returns true if the hovered cell changed. */
    private boolean updateHoverCell(double x, double y) {
        int c = (int)(x / STEP), r = (int)(y / STEP);
        if (c > 8 || r > 8) { return setHover(-1, -1); }
        double offX = x - c * STEP, offY = y - r * STEP;
        boolean inCell = offX < CELL && offY < CELL;
        return setHover(inCell ? r : -1, inCell ? c : -1);
    }

    private boolean setHover(int r, int c) {
        if (r == hoverRow && c == hoverCol) return false;
        hoverRow = r; hoverCol = c;
        return true;
    }

    private void handleClick(double x, double y) {
        int col = (int)(x / STEP), row = (int)(y / STEP);
        if (col > 8 || row > 8) return;
        double offX = x - col * STEP, offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < 8;
        boolean inHGap = offY >= CELL && row < 8;
        if (!inHGap && !inVGap) ctrl.clickCell(row, col);
        else ctrl.clickWall();
    }

    private Wall wallCandidate(double x, double y) {
        int col = (int)(x / STEP), row = (int)(y / STEP);
        if (col > 8 || row > 8) return null;
        double offX = x - col * STEP, offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < 8;
        boolean inHGap = offY >= CELL && row < 8;
        if (inHGap && !inVGap) return new Wall(HORIZONTAL, row, Math.min(col, MAX_ANCHOR));
        if (inVGap && !inHGap) return new Wall(VERTICAL, Math.min(row, MAX_ANCHOR), col);
        return null;
    }
}

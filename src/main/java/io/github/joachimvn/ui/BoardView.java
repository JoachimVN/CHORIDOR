package io.github.joachimvn.ui;

import io.github.joachimvn.core.model.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

import static io.github.joachimvn.core.model.Wall.Orientation.HORIZONTAL;
import static io.github.joachimvn.core.model.Wall.Orientation.VERTICAL;

public class BoardView extends Canvas {

    private static final int CELL = 52;
    private static final int GAP  = 8;
    private static final int STEP = CELL + GAP;
    static final int SIZE = 9 * CELL + 8 * GAP;  // 532

    private static final int MAX_ANCHOR = GameState.BOARD_SIZE - 2;  // 7

    private static final Color BOARD_BG      = Color.web("#6B3F1A");
    private static final Color CELL_COLOR    = Color.web("#F0D9B5");
    private static final Color LEGAL_TINT    = Color.rgb(50, 200, 50, 0.38);
    private static final Color WALL_COLOR    = Color.web("#1C0A00");
    private static final Color PREVIEW_COLOR = Color.web("#1C0A00", 0.45);
    private static final Color P1_COLOR      = Color.web("#D93020");
    private static final Color P2_COLOR      = Color.web("#2050D0");

    private final GameController ctrl;

    public BoardView(GameController ctrl) {
        super(SIZE, SIZE);
        this.ctrl = ctrl;
        setOnMouseMoved( e -> ctrl.updatePreviewWall(wallCandidate(e.getX(), e.getY())));
        setOnMouseExited(e -> ctrl.updatePreviewWall(null));
        setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));
    }

    public void refresh() {
        GraphicsContext gc = getGraphicsContext2D();
        GameState state    = ctrl.getState();
        List<PawnMove> legal = ctrl.getLegalPawnMoves();

        gc.setFill(BOARD_BG);
        gc.fillRect(0, 0, SIZE, SIZE);

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                double x = c * STEP, y = r * STEP;
                gc.setFill(CELL_COLOR);
                gc.fillRect(x, y, CELL, CELL);
                final int fr = r, fc = c;
                if (legal.stream().anyMatch(m -> m.target().row() == fr && m.target().col() == fc)) {
                    gc.setFill(LEGAL_TINT);
                    gc.fillRect(x, y, CELL, CELL);
                }
            }
        }

        for (Wall wall : state.getWalls()) paintWall(gc, wall, WALL_COLOR);

        Wall preview = ctrl.getPreviewWall();
        if (preview != null) paintWall(gc, preview, PREVIEW_COLOR);

        paintPawn(gc, state.getPawnPosition(Player.ONE), P1_COLOR);
        paintPawn(gc, state.getPawnPosition(Player.TWO), P2_COLOR);
    }

    private void paintWall(GraphicsContext gc, Wall wall, Color color) {
        gc.setFill(color);
        double x = wall.col() * STEP;
        double y = wall.row() * STEP;
        if (wall.orientation() == HORIZONTAL) {
            gc.fillRect(x, y + CELL, 2 * CELL + GAP, GAP);
        } else {
            gc.fillRect(x + CELL, y, GAP, 2 * CELL + GAP);
        }
    }

    private void paintPawn(GraphicsContext gc, Position pos, Color color) {
        double pad = CELL * 0.15;
        double x   = pos.col() * STEP + pad;
        double y   = pos.row() * STEP + pad;
        double d   = CELL - 2 * pad;
        gc.setFill(color);
        gc.fillOval(x, y, d, d);
        gc.setStroke(color.darker().darker());
        gc.setLineWidth(2);
        gc.strokeOval(x, y, d, d);
    }

    private void handleClick(double x, double y) {
        int col  = (int)(x / STEP);
        int row  = (int)(y / STEP);
        if (col > 8 || row > 8) return;
        double offX = x - col * STEP;
        double offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < 8;
        boolean inHGap = offY >= CELL && row < 8;

        if (!inHGap && !inVGap) {
            ctrl.clickCell(row, col);
        } else {
            ctrl.clickWall();
        }
    }

    private Wall wallCandidate(double x, double y) {
        int col  = (int)(x / STEP);
        int row  = (int)(y / STEP);
        if (col > 8 || row > 8) return null;
        double offX = x - col * STEP;
        double offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < 8;
        boolean inHGap = offY >= CELL && row < 8;

        if (inHGap && !inVGap) return new Wall(HORIZONTAL, row, Math.min(col, MAX_ANCHOR));
        if (inVGap && !inHGap) return new Wall(VERTICAL, Math.min(row, MAX_ANCHOR), col);
        return null;
    }
}

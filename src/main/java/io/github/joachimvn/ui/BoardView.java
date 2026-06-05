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
    static final int SIZE = 9 * CELL + 8 * GAP;  // 566

    private static final int MAX_ANCHOR = GameState.BOARD_SIZE - 2;

    // Board palette — dark slate
    private static final Color BG        = Color.web("#0F1117");  // near-black, slight blue
    private static final Color CELL_A    = Color.web("#1E2130");  // dark slate
    private static final Color CELL_B    = Color.web("#191C2A");  // slightly darker
    private static final Color GOAL_EDGE = Color.web("#0F1117");  // separator color

    // Player palette — muted, sophisticated
    static final Color P1_COLOR = Color.web("#C0433A");  // deep muted red
    static final Color P2_COLOR = Color.web("#3A72C0");  // deep muted blue

    // Goal row accent strips
    private static final Color P1_STRIP  = Color.web("#C0433A", 0.55);
    private static final Color P2_STRIP  = Color.web("#3A72C0", 0.55);

    // Legal move dot
    private static final Color LEGAL_DOT = Color.web("#FFFFFF", 0.20);

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
        GameState state     = ctrl.getState();
        List<PawnMove> legal = ctrl.getLegalPawnMoves();

        gc.setFill(BG);
        gc.fillRect(0, 0, SIZE, SIZE);

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                double x = c * STEP, y = r * STEP;

                // Cell background — subtle alternating
                gc.setFill((r + c) % 2 == 0 ? CELL_A : CELL_B);
                gc.fillRect(x, y, CELL, CELL);

                // Goal strip — 3px bar at the edge players must cross into
                if (r == Player.ONE.goalRow()) {
                    gc.setFill(P1_STRIP);
                    gc.fillRect(x, y, CELL, 3);
                } else if (r == Player.TWO.goalRow()) {
                    gc.setFill(P2_STRIP);
                    gc.fillRect(x, y + CELL - 3, CELL, 3);
                }

                // Legal move indicator — small centred dot
                final int fr = r, fc = c;
                if (legal.stream().anyMatch(m -> m.target().row() == fr && m.target().col() == fc)) {
                    double d = CELL * 0.28;
                    gc.setFill(LEGAL_DOT);
                    gc.fillOval(x + (CELL - d) / 2, y + (CELL - d) / 2, d, d);
                }
            }
        }

        // Placed walls
        for (Wall wall : state.getWalls()) {
            Player owner = ctrl.getWallOwner(wall);
            paintWall(gc, wall, owner == Player.ONE ? P1_COLOR : P2_COLOR, 1.0);
        }

        // Preview wall
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
        double x = wall.col() * STEP;
        double y = wall.row() * STEP;
        if (wall.orientation() == HORIZONTAL) {
            gc.fillRect(x, y + CELL, 2 * CELL + GAP, GAP);
        } else {
            gc.fillRect(x + CELL, y, GAP, 2 * CELL + GAP);
        }
    }

    private void paintPawn(GraphicsContext gc, Position pos, Color color) {
        double pad = CELL * 0.16;
        double x   = pos.col() * STEP + pad;
        double y   = pos.row() * STEP + pad;
        double d   = CELL - 2 * pad;

        gc.setFill(color);
        gc.fillOval(x, y, d, d);

        // Thin inner ring for depth without cartoony effects
        gc.setStroke(color.brighter().deriveColor(0, 1, 1, 0.5));
        gc.setLineWidth(1.5);
        double inset = d * 0.18;
        gc.strokeOval(x + inset, y + inset, d - 2 * inset, d - 2 * inset);
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

    private void handleClick(double x, double y) {
        int col  = (int)(x / STEP);
        int row  = (int)(y / STEP);
        if (col > 8 || row > 8) return;
        double offX = x - col * STEP;
        double offY = y - row * STEP;
        boolean inVGap = offX >= CELL && col < 8;
        boolean inHGap = offY >= CELL && row < 8;

        if (!inHGap && !inVGap) ctrl.clickCell(row, col);
        else ctrl.clickWall();
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

package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.core.model.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Map;

/** Static board-rendering utility shared by MiniBoard and TournamentSummary. */
final class BoardRenderer {

    private static final double DESIGN_CELL = 54;
    private static final double DESIGN_GAP  = 10;
    private static final double DESIGN_STEP = DESIGN_CELL + DESIGN_GAP;
    static final double DESIGN_SIZE = GameState.BOARD_SIZE * DESIGN_CELL
                                    + (GameState.BOARD_SIZE - 1) * DESIGN_GAP;

    private static final double GOAL_STRIP_RATIO = 3.0 / 54;
    private static final double PAWN_PAD_RATIO   = 0.16;
    private static final double STRIP_OPACITY    = 0.70;

    static final Color P1_COLOR = Color.web("#9E4A40");
    static final Color P2_COLOR = Color.web("#3E68A8");
    private static final Color BG_COLOR = Color.web("#0F1117");
    private static final Color CELL_CLR = Color.web("#191C2A");
    static final Color P1_STRIP = P1_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);
    static final Color P2_STRIP = P2_COLOR.deriveColor(0, 1, 1, STRIP_OPACITY);

    private BoardRenderer() {}

    static void draw(Canvas canvas, GameState state, Map<Wall, Player> wallOwners) {
        double boardPx = canvas.getWidth();
        GraphicsContext g = canvas.getGraphicsContext2D();
        double scale  = boardPx / DESIGN_SIZE;
        double cell   = DESIGN_CELL * scale;
        double gap    = DESIGN_GAP  * scale;
        double step   = DESIGN_STEP * scale;
        double stripH = GOAL_STRIP_RATIO * cell;
        int    n      = GameState.BOARD_SIZE;

        g.setFill(BG_COLOR); g.fillRect(0, 0, boardPx, boardPx);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                double x = c * step, y = r * step;
                g.setFill(CELL_CLR); g.fillRect(x, y, cell, cell);
                if (r == Player.ONE.goalRow()) {
                    g.setFill(P1_STRIP); g.fillRect(x, y, cell, stripH);
                } else if (r == Player.TWO.goalRow()) {
                    g.setFill(P2_STRIP); g.fillRect(x, y + cell - stripH, cell, stripH);
                }
            }
        }
        if (state != null) {
            for (Wall w : state.getWalls()) {
                Player owner = wallOwners != null ? wallOwners.get(w) : null;
                g.setFill(owner == Player.TWO ? P2_COLOR : P1_COLOR);
                double wx = w.col() * step, wy = w.row() * step, len = 2 * cell + gap;
                if (w.orientation() == Wall.Orientation.HORIZONTAL) g.fillRect(wx, wy + cell, len, gap);
                else                                                 g.fillRect(wx + cell, wy, gap, len);
            }
            double pad = cell * PAWN_PAD_RATIO;
            Position pp1 = state.getPawnPosition(Player.ONE);
            Position pp2 = state.getPawnPosition(Player.TWO);
            g.setFill(P1_COLOR);
            g.fillOval(pp1.col()*step+pad, pp1.row()*step+pad, cell-2*pad, cell-2*pad);
            g.setFill(P2_COLOR);
            g.fillOval(pp2.col()*step+pad, pp2.row()*step+pad, cell-2*pad, cell-2*pad);
        }
    }
}

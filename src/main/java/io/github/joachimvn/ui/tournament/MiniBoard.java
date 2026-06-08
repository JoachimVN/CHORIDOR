package io.github.joachimvn.ui.tournament;

import io.github.joachimvn.ai.Difficulty;
import io.github.joachimvn.core.model.*;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

/** Live-game mini board card displayed during the tournament. */
final class MiniBoard {

    static final double BOARD_PX = 370;

    private static final String BORDER_P1   = "-fx-border-color: #9E4A40; -fx-border-width: 2;";
    private static final String BORDER_P2   = "-fx-border-color: #3E68A8; -fx-border-width: 2;";
    private static final String BORDER_BOTH = "-fx-border-color: #D4AC0D; -fx-border-width: 2;";

    final Difficulty d1, d2;
    final VBox       card;
    final Canvas     canvas  = new Canvas(BOARD_PX, BOARD_PX);
    final FontIcon   pinIcon;

    MiniBoard(Difficulty d1, Difficulty d2) {
        this.d1 = d1; this.d2 = d2;

        Label p1lbl = new Label(abbrev12(d1.sample().displayName()));
        p1lbl.getStyleClass().add("mini-p1");
        Label vs = new Label("vs");
        vs.getStyleClass().add("mini-vs");
        Label p2lbl = new Label(abbrev12(d2.sample().displayName()));
        p2lbl.getStyleClass().add("mini-p2");

        pinIcon = new FontIcon(FontAwesomeSolid.THUMBTACK);
        pinIcon.setIconSize(10);
        pinIcon.setIconColor(Color.web("#D4AC0D"));
        pinIcon.setVisible(false);

        HBox names = new HBox(6, p1lbl, vs, p2lbl, pinIcon);
        names.setAlignment(Pos.CENTER);

        card = new VBox(6, names, canvas);
        card.getStyleClass().add("mini-board-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10));
    }

    void setPinned(boolean pinned) { pinIcon.setVisible(pinned); }

    void updateSelection(boolean d1Sel, boolean d2Sel) {
        if (d1Sel && d2Sel) card.setStyle(BORDER_BOTH);
        else if (d1Sel)     card.setStyle(BORDER_P1);
        else if (d2Sel)     card.setStyle(BORDER_P2);
        else                card.setStyle("");
    }

    void draw(GameState state, Map<Wall, Player> wallOwners) {
        BoardRenderer.draw(canvas, state, wallOwners);
    }

    void drawWinnerOverlay(String winnerName, Color winnerColor) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.color(0, 0, 0, 0.72));
        g.fillRect(0, 0, BOARD_PX, BOARD_PX);
        if (winnerName == null) return;
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(winnerColor.brighter());
        g.setFont(Font.font("System", FontWeight.BOLD, BOARD_PX / 15));
        g.fillText(winnerName, BOARD_PX / 2, BOARD_PX / 2 - BOARD_PX / 12);
        g.setFill(Color.web("#8890A8"));
        g.setFont(Font.font("System", BOARD_PX / 22));
        g.fillText("wins!", BOARD_PX / 2, BOARD_PX / 2 + BOARD_PX / 14);
    }

    private static String abbrev12(String name) {
        return name.length() > 12 ? name.substring(0, 11) + "…" : name;
    }
}

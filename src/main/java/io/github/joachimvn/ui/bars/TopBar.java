package io.github.joachimvn.ui.bars;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.common.LogoFactory;
import io.github.joachimvn.ui.common.UiConstants;
import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;

import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Top chrome bar: each player's colour dot, name and remaining-wall pips, with the logo centred over them. */
public final class TopBar {

    private final StackPane root;
    private final List<Rectangle> p1WallBoxes;
    private final List<Rectangle> p2WallBoxes;
    private final Label p1Name = new Label("Player 1");
    private final Label p2Name = new Label("Player 2");

    public TopBar(DoubleBinding scaleB) {
        p1WallBoxes = buildWallBoxes(BoardView.P1_COLOR, scaleB);
        p2WallBoxes = buildWallBoxes(BoardView.P2_COLOR, scaleB);

        HBox p1Side = playerSide(Player.ONE, p1WallBoxes, Pos.CENTER_LEFT,  scaleB, p1Name);
        HBox p2Side = playerSide(Player.TWO, p2WallBoxes, Pos.CENTER_RIGHT, scaleB, p2Name);

        Pane logo = LogoFactory.scaling(scaleB);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox sidesRow = new HBox(p1Side, topSpacer, p2Side);
        sidesRow.setMaxWidth(Double.MAX_VALUE);
        sidesRow.setAlignment(Pos.CENTER);

        root = new StackPane(sidesRow, logo);
        StackPane.setAlignment(logo, Pos.CENTER);
        root.getStyleClass().addAll("chrome-bar", "chrome-bar-top");
        scaleB.addListener((obs, old, nw) -> {
            double s = nw.doubleValue();
            root.setStyle(String.format(Locale.ROOT, UiConstants.PADDING_FMT, 10*s, 14*s, 10*s, 14*s));
            sidesRow.setStyle(String.format(Locale.ROOT, "-fx-spacing: %.1f;", 12*s));
        });
    }

    public StackPane getRoot() { return root; }

    /** Refresh wall pip counts and player names from the controller's current state. */
    public void update(GameController ctrl) {
        updateWallBoxes(p1WallBoxes, ctrl.getState(), Player.ONE);
        updateWallBoxes(p2WallBoxes, ctrl.getState(), Player.TWO);
        p1Name.setText(ctrl.getPlayerName(Player.ONE));
        p2Name.setText(ctrl.getPlayerName(Player.TWO));
    }

    private static List<Rectangle> buildWallBoxes(Color color, DoubleBinding scaleB) {
        List<Rectangle> boxes = new ArrayList<>();
        for (int i = 0; i < GameState.WALLS_PER_PLAYER; i++) {
            Rectangle r = new Rectangle();
            r.widthProperty().bind(scaleB.multiply(7));
            r.heightProperty().bind(scaleB.multiply(18));
            r.arcWidthProperty().bind(scaleB.multiply(2));
            r.arcHeightProperty().bind(scaleB.multiply(2));
            r.setFill(color);
            boxes.add(r);
        }
        return boxes;
    }

    private static HBox playerSide(Player player, List<Rectangle> boxes, Pos alignment,
                                   DoubleBinding scaleB, Label name) {
        Color color = player == Player.ONE ? BoardView.P1_COLOR : BoardView.P2_COLOR;

        Rectangle dot = new Rectangle();
        dot.widthProperty().bind(scaleB.multiply(8));
        dot.heightProperty().bind(scaleB.multiply(8));
        dot.arcWidthProperty().bind(scaleB.multiply(8));
        dot.arcHeightProperty().bind(scaleB.multiply(8));
        dot.setFill(color);

        name.getStyleClass().add("player-label");
        scaleB.addListener((obs, old, nw) ->
            name.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.1fpx;", 12 * nw.doubleValue())));

        HBox wallRow = new HBox();
        wallRow.setAlignment(Pos.CENTER);
        wallRow.spacingProperty().bind(scaleB.multiply(3));
        wallRow.getChildren().addAll(boxes);

        HBox side = new HBox();
        side.setAlignment(alignment);
        side.spacingProperty().bind(scaleB.multiply(8));
        scaleB.addListener((obs, old, nw) ->
            side.setPadding(new Insets(0, 4 * nw.doubleValue(), 0, 4 * nw.doubleValue())));
        side.setPadding(new Insets(0, 4, 0, 4));
        side.getChildren().addAll(dot, name, wallRow);
        return side;
    }

    private static void updateWallBoxes(List<Rectangle> boxes, GameState state, Player player) {
        int remaining = state.getWallCount(player);
        Color activeColor = player == Player.ONE ? BoardView.P1_COLOR : BoardView.P2_COLOR;
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).setFill(i < remaining ? activeColor : UiConstants.WALL_USED_COLOR);
        }
    }
}

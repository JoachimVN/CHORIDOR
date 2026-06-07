package io.github.joachimvn.ui.overlays;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.common.UiConstants;
import io.github.joachimvn.core.model.Player;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/** Modal shown when a game ends: announces the winner and offers New Game / Change Mode / Look Back. */
public final class GameOverOverlay {

    private final StackPane root;
    private final Label winLabel = new Label();
    private final GameController ctrl;

    public GameOverOverlay(GameController ctrl, StackPane setupOverlay) {
        this.ctrl = ctrl;
        winLabel.getStyleClass().add("game-over-title");

        root = new StackPane();
        root.getStyleClass().add("game-over-overlay");
        root.setVisible(false);

        Button newGameBtn = iconActionButton(FontAwesomeSolid.REDO, "Play Again",
            ctrl::replay);
        Button changeBtn  = iconActionButton(FontAwesomeSolid.SLIDERS_H, "Change Mode",
            () -> { root.setVisible(false); setupOverlay.setVisible(true); });
        Button lookBtn    = iconActionButton(FontAwesomeSolid.HISTORY, "Look Back",
            () -> { root.setVisible(false); ctrl.enterReview(); });

        // Play Again sits in the middle as the default action (also bound to Enter/Space in App).
        HBox buttons = new HBox(18, changeBtn, newGameBtn, lookBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox card = new VBox(26, winLabel, buttons);
        card.getStyleClass().add("game-over-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        root.getChildren().add(card);
    }

    public StackPane getRoot() { return root; }

    /** Show the overlay (with the winner's name and colour) once a game ends and nothing else is up. */
    public void update(boolean setupVisible) {
        boolean over = ctrl.isGameOver();
        if (over) {
            winLabel.setText(ctrl.getStatusText());
            winLabel.getStyleClass().removeAll(UiConstants.CSS_PLAYER1, UiConstants.CSS_PLAYER2);
            winLabel.getStyleClass().add(ctrl.getWinner() == Player.ONE ? UiConstants.CSS_PLAYER1 : UiConstants.CSS_PLAYER2);
        }
        root.setVisible(over && !setupVisible && !ctrl.isReviewing());
    }

    /** Icon-on-top "card" button for the game-over overlay. */
    private Button iconActionButton(FontAwesomeSolid icon, String caption, Runnable action) {
        FontIcon fi = new FontIcon(icon);
        fi.getStyleClass().add("game-over-icon");
        fi.setIconSize(34);

        Button btn = new Button(caption, fi);
        btn.setContentDisplay(ContentDisplay.TOP);
        btn.setGraphicTextGap(14);
        btn.getStyleClass().add("game-over-button");
        btn.setOnAction(e -> {
            ctrl.playSelect();
            if (action != null) action.run();
        });
        return btn;
    }
}

package io.github.joachimvn.ui.bars;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.common.UiConstants;
import io.github.joachimvn.core.model.Player;

import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

/** Bottom chrome bar during live play: turn status on the left, flip / mute / change-mode / play-again on the right. */
public final class BottomBar {

    private final HBox root;
    private final Label statusLabel = new Label();
    private final GameController ctrl;
    private final ToggleButton flipButton;
    private Runnable onChangeMode = () -> {};

    public BottomBar(GameController ctrl, BoardView board, DoubleBinding scaleB) {
        this.ctrl = ctrl;
        statusLabel.getStyleClass().add("status-label");

        FontIcon flipIcon = new FontIcon(FontAwesomeSolid.SYNC_ALT);
        flipIcon.getStyleClass().add("bar-icon");
        flipButton = new ToggleButton();
        flipButton.setGraphic(flipIcon);
        flipButton.getStyleClass().add("mute-button");
        flipButton.setOnAction(e -> board.setFlipped(flipButton.isSelected()));

        FontIcon muteIcon = new FontIcon(FontAwesomeSolid.VOLUME_UP);
        muteIcon.getStyleClass().add("bar-icon");
        Button muteButton = new Button();
        muteButton.setGraphic(muteIcon);
        muteButton.getStyleClass().add("mute-button");
        muteButton.setOnAction(e -> {
            ctrl.toggleMute();
            muteIcon.setIconCode(ctrl.isMuted() ? FontAwesomeSolid.VOLUME_MUTE : FontAwesomeSolid.VOLUME_UP);
        });

        Button newGame = new Button("Play Again");
        newGame.getStyleClass().add("new-game-button");
        newGame.setOnAction(e -> ctrl.replay());

        Button changeMode = new Button("Change Mode");
        changeMode.getStyleClass().add("ai-toggle-button");
        changeMode.setOnAction(e -> { ctrl.playSelect(); onChangeMode.run(); });

        Region botSpacer = new Region();
        HBox.setHgrow(botSpacer, Priority.ALWAYS);

        root = new HBox(statusLabel, botSpacer, flipButton, muteButton, changeMode, newGame);
        root.getStyleClass().add("chrome-bar");
        root.setAlignment(Pos.CENTER_LEFT);
        scaleB.addListener((obs, old, nw) -> {
            double s = nw.doubleValue();
            root.setStyle(String.format(Locale.ROOT,
                UiConstants.PADDING_FMT + " -fx-spacing: %.1f;", 10*s, 14*s, 10*s, 14*s, 12*s));
            statusLabel.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.1fpx;", 13*s));
            newGame.setStyle(String.format(Locale.ROOT,
                UiConstants.FONTSIZE_FMT + UiConstants.PADDING_FMT, 12*s, 5*s, 16*s, 5*s, 16*s));
            changeMode.setStyle(String.format(Locale.ROOT,
                UiConstants.FONTSIZE_FMT + UiConstants.PADDING_FMT, 12*s, 5*s, 16*s, 5*s, 16*s));
            muteIcon.setIconSize((int)(13 * s));
            muteButton.setStyle(String.format(Locale.ROOT, UiConstants.PADDING_FMT, 5*s, 9*s, 5*s, 9*s));
            flipIcon.setIconSize((int)(13 * s));
            flipButton.setStyle(String.format(Locale.ROOT, UiConstants.PADDING_FMT, 5*s, 9*s, 5*s, 9*s));
        });
    }

    public HBox getRoot() { return root; }

    /** Reflect the board's flip state on the flip toggle (driven from the setup overlay's Start). */
    public void setFlipSelected(boolean flipped) { flipButton.setSelected(flipped); }

    /** What "Change Mode" does after playing its click sound (App wires it to show the setup overlay). */
    public void setOnChangeMode(Runnable r) { onChangeMode = r; }

    /** Update the turn / win text and its player colour from the controller. */
    public void updateStatus() {
        statusLabel.getStyleClass().removeAll(UiConstants.CSS_PLAYER1, UiConstants.CSS_PLAYER2);
        if (ctrl.isAiThinking()) {
            Player current = ctrl.getState().getCurrentPlayer();
            String thinkText = ctrl.isAiVsAi()
                ? ctrl.getPlayerName(current) + " is thinking..."
                : "AI is thinking...";
            statusLabel.setText(thinkText);
            statusLabel.getStyleClass().add(current == Player.ONE ? UiConstants.CSS_PLAYER1 : UiConstants.CSS_PLAYER2);
        } else if (ctrl.isGameOver()) {
            Player winner = ctrl.getWinner();
            statusLabel.setText(ctrl.getStatusText());
            statusLabel.getStyleClass().add(winner == Player.ONE ? UiConstants.CSS_PLAYER1 : UiConstants.CSS_PLAYER2);
        } else {
            Player p = ctrl.getState().getCurrentPlayer();
            statusLabel.setText(ctrl.getStatusText());
            statusLabel.getStyleClass().add(p == Player.ONE ? UiConstants.CSS_PLAYER1 : UiConstants.CSS_PLAYER2);
        }
    }
}

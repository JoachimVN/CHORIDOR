package io.github.joachimvn.ui.bars;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.common.UiConstants;

import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bottom bar shown while looking back at a finished game: first/prev/next/last nav plus an Exit Review button. */
public final class ReviewBar {

    private final StackPane root;
    private final Label reviewLabel = new Label();
    private final GameController ctrl;
    private final List<Button>   navButtons = new ArrayList<>();
    private final List<FontIcon> navIcons   = new ArrayList<>();

    public ReviewBar(GameController ctrl, DoubleBinding scaleB) {
        this.ctrl = ctrl;

        Button revFirst = iconButton(FontAwesomeSolid.ANGLE_DOUBLE_LEFT,  ctrl::reviewFirst);
        Button revPrev  = iconButton(FontAwesomeSolid.ANGLE_LEFT,         ctrl::reviewPrev);
        Button revNext  = iconButton(FontAwesomeSolid.ANGLE_RIGHT,        ctrl::reviewNext);
        Button revLast  = iconButton(FontAwesomeSolid.ANGLE_DOUBLE_RIGHT, ctrl::reviewLast);

        reviewLabel.getStyleClass().add("status-label");
        reviewLabel.setAlignment(Pos.CENTER);
        reviewLabel.setMinWidth(130);

        HBox reviewNav = new HBox(8, revFirst, revPrev, reviewLabel, revNext, revLast);
        reviewNav.setAlignment(Pos.CENTER);

        Button exitReview = new Button("Exit Review");
        exitReview.getStyleClass().add("new-game-button");
        exitReview.setOnAction(e -> { ctrl.playSelect(); ctrl.exitReview(); });

        // Overlay the nav and the Exit button so the nav sits at true 50% of the whole
        // bar (not 50% of the space left over by Exit) — same trick the logo uses up top.
        root = new StackPane(reviewNav, exitReview);
        StackPane.setAlignment(reviewNav, Pos.CENTER);
        StackPane.setAlignment(exitReview, Pos.CENTER_RIGHT);
        root.getStyleClass().add("chrome-bar");
        scaleB.addListener((obs, old, nw) -> {
            double s = nw.doubleValue();
            root.setStyle(String.format(Locale.ROOT, UiConstants.PADDING_FMT, 10*s, 14*s, 10*s, 14*s));
            reviewNav.setStyle(String.format(Locale.ROOT, "-fx-spacing: %.1f;", 8*s));
            reviewLabel.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.1fpx;", 13*s));
            exitReview.setStyle(String.format(Locale.ROOT,
                UiConstants.FONTSIZE_FMT + UiConstants.PADDING_FMT, 12*s, 5*s, 16*s, 5*s, 16*s));
            for (FontIcon fi : navIcons) fi.setIconSize((int)(13 * s));
            for (Button b : navButtons)
                b.setStyle(String.format(Locale.ROOT, UiConstants.PADDING_FMT, 5*s, 9*s, 5*s, 9*s));
            // The review bar is added to the scene only while reviewing; when added that way,
            // JavaFX does not propagate CSS to the nested nav HBox's children, so these inline
            // styles (and the .mute-button/.status-label classes) silently fail to apply.
            // Forcing applyCss() while it is on-screen makes them take effect at every size.
            if (root.getScene() != null) root.applyCss();
        });
    }

    public StackPane getRoot() { return root; }

    /** Refresh the "Move n / total" caption and force CSS onto the freshly-added bar (see scale listener). */
    public void update() {
        reviewLabel.setText(labelText(ctrl));
        root.applyCss();
    }

    /** Square icon button for the review bar; the action runs on click (it plays its own sound). */
    private Button iconButton(FontAwesomeSolid icon, Runnable action) {
        FontIcon fi = new FontIcon(icon);
        // Set the colour explicitly instead of via the .bar-icon CSS class. The scale
        // listener calls setIconSize() on these icons while the review bar is still hidden
        // (e.g. when the window is maximised mid-game); at that point the bar's CSS has not
        // been applied, so an icon left to default colour would render (and cache) as black
        // and vanish on the dark bar. A fixed colour re-renders correctly at any size.
        fi.setIconColor(UiConstants.BAR_ICON_COLOR);
        Button btn = new Button();
        btn.setGraphic(fi);
        btn.getStyleClass().add("mute-button");
        btn.setOnAction(e -> action.run());
        navButtons.add(btn);
        navIcons.add(fi);
        return btn;
    }

    private static String labelText(GameController ctrl) {
        int cursor = ctrl.getReviewCursor();
        return cursor == 0
            ? "Start position"
            : "Move " + cursor + " / " + ctrl.getMoveCount();
    }
}

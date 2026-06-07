package io.github.joachimvn.ui.common;

import javafx.scene.paint.Color;

/** Shared colours, sizes and inline-style format strings used across the chrome bars and overlays. */
public final class UiConstants {

    private UiConstants() {}

    public static final Color  WALL_USED_COLOR    = Color.web("#252838");
    public static final double LOGO_TARGET_HEIGHT = 30;
    public static final double SVG_WIDTH          = 2048;
    public static final double SVG_HEIGHT         = 460;
    public static final Color  LOGO_RED           = Color.web("#9d493f");
    public static final Color  LOGO_BLUE          = Color.web("#3e67a7");
    public static final Color  BAR_ICON_COLOR     = Color.web("#8890A8"); // matches .bar-icon in app.css
    public static final String CSS_PLAYER1        = "player1";
    public static final String CSS_PLAYER2        = "player2";
    public static final String PADDING_FMT        = "-fx-padding: %.1f %.1f %.1f %.1f;";
    public static final String FONTSIZE_FMT       = "-fx-font-size: %.1fpx; ";
    public static final String SECTION_LABEL_CSS  = "setup-section-label";
}

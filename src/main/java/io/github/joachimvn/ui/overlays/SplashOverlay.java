package io.github.joachimvn.ui.overlays;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Full-screen black splash shown on launch; fades out automatically. */
public final class SplashOverlay {

    private final StackPane root;

    public SplashOverlay() {
        root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        Image img = new Image(getClass().getResourceAsStream(
            "/images/logos/CHORIDOR_Logo_Square_Black.png"));
        ImageView logo = new ImageView(img);
        logo.setFitWidth(240);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        ColorAdjust brighten = new ColorAdjust();
        brighten.setBrightness(0.125);
        logo.setEffect(brighten);

        root.getChildren().add(logo);
    }

    public StackPane getRoot() { return root; }

    /** Hold for 1.3 s then fade to transparent over 0.5 s. */
    public void playAndExit() {
        Timeline tl = new Timeline(
            new KeyFrame(Duration.millis(1300)),
            new KeyFrame(Duration.millis(1800),
                new KeyValue(root.opacityProperty(), 0.0, Interpolator.EASE_IN)));
        tl.setOnFinished(e -> {
            root.setVisible(false);
            root.setOpacity(1.0);
        });
        tl.play();
    }
}

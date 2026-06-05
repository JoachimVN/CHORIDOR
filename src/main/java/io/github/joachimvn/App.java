package io.github.joachimvn;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class App extends Application {

    private static final Color WALL_USED_COLOR = Color.web("#252838");
    private static final Color LOGO_COLOR      = Color.web("#C8CCDC");

    private static final double SVG_WIDTH  = 2048;
    private static final double SVG_HEIGHT = 460;
    private static final double LOGO_TARGET_HEIGHT = 30;

    @Override
    public void start(Stage stage) {
        GameController ctrl = new GameController();
        BoardView board = new BoardView(ctrl);

        // ── Top bar ──────────────────────────────────────────────────────────
        List<Rectangle> p1WallBoxes = buildWallBoxes(BoardView.P1_COLOR);
        List<Rectangle> p2WallBoxes = buildWallBoxes(BoardView.P2_COLOR);

        HBox p1Side = playerSide(Player.ONE, p1WallBoxes, Pos.CENTER_LEFT);
        HBox p2Side = playerSide(Player.TWO, p2WallBoxes, Pos.CENTER_RIGHT);

        Pane logo = buildLogo();

        Region topSpacerLeft  = new Region();
        Region topSpacerRight = new Region();
        HBox.setHgrow(topSpacerLeft,  Priority.ALWAYS);
        HBox.setHgrow(topSpacerRight, Priority.ALWAYS);

        HBox topBar = new HBox(p1Side, topSpacerLeft, logo, topSpacerRight, p2Side);
        topBar.getStyleClass().addAll("chrome-bar", "chrome-bar-top");
        topBar.setAlignment(Pos.CENTER);

        // ── Bottom bar ───────────────────────────────────────────────────────
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        Button newGame = new Button("New Game");
        newGame.getStyleClass().add("new-game-button");
        newGame.setOnAction(e -> ctrl.reset());

        Region botSpacer = new Region();
        HBox.setHgrow(botSpacer, Priority.ALWAYS);

        HBox bottomBar = new HBox(statusLabel, botSpacer, newGame);
        bottomBar.getStyleClass().add("chrome-bar");
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        // ── Wiring ───────────────────────────────────────────────────────────
        ctrl.addListener(() -> {
            board.refresh();
            updateWallBoxes(p1WallBoxes, ctrl.getState(), Player.ONE);
            updateWallBoxes(p2WallBoxes, ctrl.getState(), Player.TWO);
            updateStatus(statusLabel, ctrl);
        });

        board.refresh();
        updateWallBoxes(p1WallBoxes, ctrl.getState(), Player.ONE);
        updateWallBoxes(p2WallBoxes, ctrl.getState(), Player.TWO);
        updateStatus(statusLabel, ctrl);

        BorderPane root = new BorderPane(board);
        root.setTop(topBar);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
            getClass().getResource("/css/app.css").toExternalForm()
        );

        stage.getIcons().add(new Image(getClass().getResourceAsStream(
            "/images/logos/Choridor_Logo_Square_White.png")));
        stage.setTitle("CHORIDOR");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    // ── Logo ─────────────────────────────────────────────────────────────────

    private static Pane buildLogo() {
        // Path data extracted from Choridor_Logo_White.svg (viewBox 0 0 2048 460)
        String[] pathData = {
            // Letterforms (CHORIDOR)
            "m128.5 321.2q14 0 21.8-2.9 8-2.9 12.4-7.1 4.4-4.2 7.7-8.4 3-3.8 4.9-5.7 2.1-2 5.8-2.7 3-1 9.7-1.2 6.9-0.3 13.6-0.3 6.9 0 9.5 0.3 4 0.2 5.6 1.6 1.5 1.3 1.9 5.7 0.5 6.1 0.5 14.5 0.2 8.4-0.1 14.6-0.2 1.9-0.8 3.2-0.4 1.4-1.9 4-1.9 3.1-7.5 9-5.5 5.8-16.2 12.1-10.7 6.1-27.7 10.5-16.8 4.4-41.4 4.4-26.4 0-49.3-6.7-22.9-6.9-40.3-23-17.2-16-27-43.4-9.5-27.3-9.5-68.2 0-45.5 15.7-77.3 15.8-31.9 44.8-48.5 29.1-16.7 68.8-16.7 27.9 0 44.7 5.8 17 5.7 25.8 13.5 8.8 7.7 12.4 13.4 1.3 2.1 1.9 3.8 0.6 1.6 0.8 5 0.5 4.6 0.2 13.4-0.2 8.8-1.4 15.5-0.7 4.2-2.5 5.5-0.7 0.8-2.6 1.4-1.9 0.5-5.8 0.5-6.1 0-13.7-0.3-7.7-0.6-11.5-1.2-4.6-0.7-6.7-2.1-2.1-1.5-4.2-4.6-3.4-4.9-11.8-11.5-8.4-6.5-27.7-6.5-27.1 0-44.7 21.1-17.6 20.8-17.6 67.7 0 46.6 18.4 69.2 18.5 22.6 45 22.6zm311.2-118v-78.8q0-5.5 1.9-8.4 8.6-12.6 24.1-26.6 2.9-2.3 5-2.3 2.2 0 4.7 2.3 8.4 7.3 14.2 14 5.7 6.5 9.9 12.6 1.9 3 1.9 8.4v233.5q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.7 0-8.2-2.1-2.3-2.3-2.3-8v-101.6h-113.4v101.6q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.8 0-8.2-2.1-2.3-2.3-2.3-8v-254q0-5.7 2.3-7.8 2.4-2.3 8.2-2.3h40.7q5.7 0 8 2.3 2.5 2.1 2.5 7.8v99.3zm240.3 169.6q-39.8 0-69.2-15.3-29.4-15.3-45.6-46.1-16.1-30.8-16.1-76.9 0-73.2 35.8-109.1 35.7-36.2 99.5-36.2 59.7 0 94.5 35.4 34.8 35.2 34.8 104.4 0 48.9-16.3 80.9-16.2 31.7-46.2 47.4-30 15.5-71.2 15.5zm1.1-51.6q34.4 0 51-24.7 16.6-24.7 16.6-64.1 0-45.7-15.6-68.6-15.7-23-50.5-23-68.5 0-68.5 90.5 0 40.3 16.4 65.2 16.6 24.7 50.6 24.7zm242.1-156.3v193q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.7 0-8.2-2.1-2.3-2.3-2.3-8v-212.9q0-19.1 3.4-30.2 3.7-11.2 13.8-16 10.3-5 30-5h50.6q99.7 0 99.7 76.7 0 23.3-7.8 38.2-7.9 14.9-19.5 23.3-11.7 8.3-22.9 12.5v0.9q12.8 8.4 24.2 22.2 11.7 13.6 21 29.7 9.4 15.8 15.9 31.5 6.5 15.5 9 27.7 1.1 5.8 0 8.6-1 2.9-6.5 2.9h-44.1q-5 0-8.8-2.1-3.7-2.1-6.7-9.8-14.5-36.5-33.4-59.4-19-23.2-36.5-37.1-11.3-9-11.3-12.8 0-3.1 3.6-8.4 2.3-3.3 6.7-7.9 4.4-4.6 7.7-6.9 3.2-2.3 5.1-2.8 2.1-0.6 6.3-0.6 14.2 0 23.5-9.8 9.6-9.7 9.6-30.7 0-19.9-10.2-29.1-9.9-9.4-27.5-9.4h-11.4q-12.6 0-17.2 5.4-4.6 5.3-4.6 18.3zm255-40.5v233.5q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.7 0-8.2-2.1-2.3-2.3-2.3-8v-233.5q0-5.5 1.9-8.4 8.6-12.6 24.1-26.6 2.8-2.3 4.9-2.3 2.3 0 4.8 2.3 8.4 7.3 14.2 14 5.7 6.5 9.9 12.6 1.9 3 1.9 8.4zm167.1 243.6h-58.6q-19.7 0-30-5-10.1-4.9-13.8-16.2-3.4-11.3-3.4-30v-171.8q0-19.1 3.4-30.2 3.7-11.2 13.8-16 10.3-5 30-5h58.6q41.1 0 71.8 12.2 31 12.3 48.2 41.5 17.2 29.1 17.2 80 0 50.8-17.2 81.8-17.2 30.8-48.2 44.7-30.7 14-71.8 14zm-13.9-223.9h-8.4q-12.6 0-17.2 5.3-4.6 5.4-4.6 18.4v126.2q0 12.8 4.6 18.4 4.6 5.5 17.2 5.5h8.8q40.8 0 63.2-20.1 22.5-20.1 22.5-69.6 0-33.1-10.3-51.2-10.1-18.4-29.4-25.6-19.3-7.3-46.4-7.3zm315.6 228.7q-39.8 0-69.2-15.3-29.4-15.3-45.6-46.1-16.1-30.8-16.1-76.9 0-73.2 35.8-109.1 35.7-36.2 99.5-36.2 59.7 0 94.5 35.4 34.8 35.2 34.8 104.4 0 48.9-16.3 80.9-16.2 31.7-46.2 47.4-30 15.5-71.2 15.5zm1.1-51.6q34.4 0 51-24.7 16.6-24.7 16.6-64.1 0-45.7-15.6-68.6-15.7-23-50.5-23-68.5 0-68.5 90.5 0 40.3 16.4 65.2 16.6 24.7 50.6 24.7zm242.1-156.3v193q0 5.7-2.5 8-2.3 2.1-8 2.1h-40.7q-5.7 0-8.2-2.1-2.3-2.3-2.3-8v-212.9q0-19.1 3.4-30.2 3.7-11.2 13.8-16 10.3-5 30-5h50.6q99.7 0 99.7 76.7 0 23.3-7.8 38.2-7.9 14.9-19.5 23.3-11.7 8.3-22.9 12.5v0.9q12.8 8.4 24.2 22.2 11.7 13.6 21 29.7 9.4 15.8 15.9 31.5 6.5 15.5 9 27.7 1.1 5.8 0 8.6-1 2.9-6.5 2.9h-44.1q-5 0-8.8-2.1-3.7-2.1-6.7-9.8-14.5-36.5-33.4-59.4-19-23.2-36.5-37.1-11.3-9-11.3-12.8 0-3.1 3.6-8.4 2.3-3.3 6.7-7.9 4.4-4.6 7.7-6.9 3.2-2.3 5.1-2.8 2.1-0.6 6.3-0.6 14.2 0 23.5-9.8 9.6-9.7 9.6-30.7 0-19.9-10.2-29.1-9.9-9.4-27.5-9.4h-11.4q-12.6 0-17.2 5.4-4.6 5.3-4.6 18.3z",
            // Decorative bars / connector shapes (all even-odd)
            "m1153.83 411.84v34.16c0 7.73-6.26 14-14 14h-1125.83c-7.73 0-14-6.27-14-14v-34.16c0-7.73 6.27-14 14-14h1125.83c7.74 0 14 6.27 14 14z",
            "m2048 7v48.16c0 3.87-3.13 7-7 7h-901.62c-3.86 0-7-3.13-7-7v-48.16c0-3.87 3.14-7 7-7h901.62c3.87 0 7 3.13 7 7z",
            "m1164 14v34.16c0 7.73-6.27 14-14 14h-19.42c-7.73 0-14-6.27-14-14v-34.16c0-7.73 6.27-14 14-14h19.42c7.73 0 14 6.27 14 14z",
            "m1178.67 411.84v34.16c0 7.73-6.27 14-14 14h-19.42c-7.73 0-14-6.27-14-14v-34.16c0-7.73 6.27-14 14-14h19.42c7.73 0 14 6.27 14 14z",
            "m1171.67 441.92h-48.09c-3.87 0-7-3.14-7-7v-397.4c0-3.87 3.13-7 7-7h48.09c3.87 0 7 3.13 7 7v397.4c0 3.86-3.13 7-7 7z"
        };

        Group group = new Group();
        for (int i = 0; i < pathData.length; i++) {
            SVGPath path = new SVGPath();
            path.setContent(pathData[i]);
            path.setFill(LOGO_COLOR);
            if (i > 0) path.setFillRule(FillRule.EVEN_ODD);
            group.getChildren().add(path);
        }

        double scale      = LOGO_TARGET_HEIGHT / SVG_HEIGHT;
        double logoWidth  = SVG_WIDTH * scale;
        group.getTransforms().add(new Scale(scale, scale, 0, 0));

        // Wrap in a Pane sized to the scaled dimensions so the HBox layout
        // reserves the correct space (the Scale transform alone doesn't shrink bounds).
        Pane pane = new Pane(group);
        pane.setPrefSize(logoWidth, LOGO_TARGET_HEIGHT);
        pane.setMinSize(logoWidth, LOGO_TARGET_HEIGHT);
        pane.setMaxSize(logoWidth, LOGO_TARGET_HEIGHT);
        return pane;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Rectangle> buildWallBoxes(Color color) {
        List<Rectangle> boxes = new ArrayList<>();
        for (int i = 0; i < GameState.WALLS_PER_PLAYER; i++) {
            Rectangle r = new Rectangle(7, 18);
            r.setArcWidth(2);
            r.setArcHeight(2);
            r.setFill(color);
            boxes.add(r);
        }
        return boxes;
    }

    private HBox playerSide(Player player, List<Rectangle> boxes, Pos alignment) {
        Color color = player == Player.ONE ? BoardView.P1_COLOR : BoardView.P2_COLOR;
        String label = "Player " + (player == Player.ONE ? "1" : "2");

        Rectangle dot = new Rectangle(8, 8);
        dot.setArcWidth(8);
        dot.setArcHeight(8);
        dot.setFill(color);

        Label name = new Label(label);
        name.getStyleClass().add("player-label");

        HBox wallRow = new HBox(3);
        wallRow.setAlignment(Pos.CENTER);
        wallRow.getChildren().addAll(boxes);

        HBox side = new HBox(8, dot, name, wallRow);
        side.setAlignment(alignment);
        side.setPadding(new Insets(0, 4, 0, 4));
        return side;
    }

    private void updateWallBoxes(List<Rectangle> boxes, GameState state, Player player) {
        int remaining = state.getWallCount(player);
        Color activeColor = player == Player.ONE ? BoardView.P1_COLOR : BoardView.P2_COLOR;
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).setFill(i < remaining ? activeColor : WALL_USED_COLOR);
        }
    }

    private void updateStatus(Label label, GameController ctrl) {
        label.getStyleClass().removeAll("player1", "player2", "gameover");
        if (ctrl.isGameOver()) {
            label.setText(ctrl.getStatusText());
            label.getStyleClass().add("gameover");
        } else {
            Player p = ctrl.getState().getCurrentPlayer();
            label.setText("Player " + (p == Player.ONE ? "1" : "2") + "'s turn");
            label.getStyleClass().add(p == Player.ONE ? "player1" : "player2");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

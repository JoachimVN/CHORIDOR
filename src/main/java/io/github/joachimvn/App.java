package io.github.joachimvn;

import io.github.joachimvn.core.model.GameState;
import io.github.joachimvn.core.model.Player;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class App extends Application {

    private static final Color  WALL_USED_COLOR    = Color.web("#252838");
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

        ImageView logo = buildLogo();

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

    private static ImageView buildLogo() {
        Image image = new Image(App.class.getResourceAsStream(
            "/images/logos/Choridor_Logo.png"));
        ImageView view = new ImageView(image);
        view.setFitHeight(LOGO_TARGET_HEIGHT);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
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

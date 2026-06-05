package io.github.joachimvn;

import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        GameController ctrl = new GameController();
        BoardView board = new BoardView(ctrl);

        Label status = new Label();
        status.getStyleClass().add("status-label");

        Button newGame = new Button("New Game");
        newGame.getStyleClass().add("new-game-button");
        newGame.setOnAction(e -> ctrl.reset());

        HBox bar = new HBox(status, newGame);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.ALWAYS);

        ctrl.addListener(() -> {
            board.refresh();
            status.setText(ctrl.getStatusText());
        });

        BorderPane root = new BorderPane(board);
        root.setBottom(bar);

        board.refresh();
        status.setText(ctrl.getStatusText());

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
            getClass().getResource("/io/github/joachimvn/app.css").toExternalForm()
        );

        stage.setTitle("Choridor");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.joachimvn;

import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.GameController;
import javafx.application.Application;
import javafx.geometry.Insets;
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
        status.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f0d9b5;");

        Button newGame = new Button("New Game");
        newGame.setOnAction(e -> ctrl.reset());

        HBox bar = new HBox(status, newGame);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(16);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: #3a1f0a;");
        HBox.setHgrow(status, Priority.ALWAYS);

        ctrl.addListener(() -> {
            board.refresh();
            status.setText(ctrl.getStatusText());
        });

        BorderPane root = new BorderPane(board);
        root.setBottom(bar);

        board.refresh();
        status.setText(ctrl.getStatusText());

        stage.setTitle("Choridor");
        stage.setScene(new Scene(root));
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

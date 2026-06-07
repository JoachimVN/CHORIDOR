package io.github.joachimvn;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.board.BoardView;
import io.github.joachimvn.ui.bars.BottomBar;
import io.github.joachimvn.ui.bars.ReviewBar;
import io.github.joachimvn.ui.bars.TopBar;
import io.github.joachimvn.ui.common.UiScale;
import io.github.joachimvn.ui.overlays.GameOverOverlay;
import io.github.joachimvn.ui.overlays.SetupOverlay;

import javafx.application.Application;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Application entry point and UI orchestrator. Each chrome bar and overlay lives in its own class
 * in {@code io.github.joachimvn.ui}; this class wires them together around a shared
 * {@link GameController} and the board-derived {@code scaleB} that all of them scale against.
 */
public class App extends Application {

    /**
     * Dead-band for the chrome scale, in scale units (~{@value}×board-design-width of slack).
     * Large enough to swallow the 1–2px board↔chrome layout limit cycle; small enough that a
     * deliberate resize still scales smoothly. See {@link UiScale#hysteretic}.
     */
    private static final double UI_SCALE_BAND = 0.01;

    @Override
    public void start(Stage stage) {
        GameController ctrl = new GameController();
        BoardView board = new BoardView(ctrl);

        // Raw scale tracks the board width pixel-for-pixel; the shared scaleB the chrome uses is
        // dead-banded so a sub-threshold wobble can't feed back through the bars (the "vibration").
        DoubleBinding rawScale = board.widthProperty().divide(board.getWidth());
        DoubleBinding scaleB   = UiScale.hysteretic(rawScale, UI_SCALE_BAND);

        // ── Sections ───────────────────────────────────────────────────────────
        TopBar    topBar    = new TopBar(scaleB);
        BottomBar bottomBar = new BottomBar(ctrl, board, scaleB);
        ReviewBar reviewBar = new ReviewBar(ctrl, scaleB);
        SetupOverlay  setup    = new SetupOverlay(ctrl, board, bottomBar::setFlipSelected);
        GameOverOverlay gameOver = new GameOverOverlay(ctrl, setup.getRoot());

        // "Change Mode" on the bottom bar dismisses any game-over modal and reopens setup.
        bottomBar.setOnChangeMode(() -> {
            gameOver.getRoot().setVisible(false);
            setup.getRoot().setVisible(true);
        });

        // The bottom of the window is the bottom bar during play and the review bar during review.
        // Only one is ever in the scene at a time — swapping (rather than stacking both in a
        // StackPane) keeps the live bottom bar identical to the released layout.
        BorderPane gamePane = new BorderPane(board);
        gamePane.setTop(topBar.getRoot());
        gamePane.setBottom(bottomBar.getRoot());

        // ── Wiring ───────────────────────────────────────────────────────────
        ctrl.addListener(() -> {
            board.refresh();
            topBar.update(ctrl);
            bottomBar.updateStatus();

            boolean reviewing = ctrl.isReviewing();
            gamePane.setBottom(reviewing ? reviewBar.getRoot() : bottomBar.getRoot());
            if (reviewing) reviewBar.update();

            gameOver.update(setup.getRoot().isVisible());
        });

        board.refresh();
        topBar.update(ctrl);
        bottomBar.updateStatus();

        StackPane sceneRoot = new StackPane(gamePane, gameOver.getRoot(), setup.getRoot());

        Scene scene = new Scene(sceneRoot);
        scene.getStylesheets().add(
            getClass().getResource("/css/app.css").toExternalForm()
        );
        scene.addEventFilter(KeyEvent.KEY_PRESSED,
            e -> handleKey(e, stage, ctrl, gameOver, setup));

        stage.getIcons().add(new Image(getClass().getResourceAsStream(
            "/images/logos/Choridor_Logo_Square_White.png")));
        stage.setTitle("CHORIDOR");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(300);
        stage.setMinHeight(360);
        stage.show();
    }

    private static void handleKey(KeyEvent e, Stage stage, GameController ctrl,
                                  GameOverOverlay gameOver, SetupOverlay setup) {
        KeyCode code = e.getCode();
        if (code == KeyCode.D && e.isControlDown()) {
            System.err.println(ctrl.boardStateText()); // intentional diagnostic output to stderr
            e.consume();
        } else if (code == KeyCode.F11) {
            stage.setFullScreen(!stage.isFullScreen());
            e.consume();
        } else if (code == KeyCode.ENTER && gameOver.getRoot().isVisible()) {
            ctrl.replay();
            e.consume();
        } else if (ctrl.isReviewing()) {
            handleReviewKey(e, ctrl);
        }
    }

    private static void handleReviewKey(KeyEvent e, GameController ctrl) {
        switch (e.getCode()) {
            case LEFT  -> { if (e.isShiftDown()) ctrl.reviewFirst(); else ctrl.reviewPrev(); }
            case RIGHT -> { if (e.isShiftDown()) ctrl.reviewLast();  else ctrl.reviewNext(); }
            case HOME  -> ctrl.reviewFirst();
            case END   -> ctrl.reviewLast();
            default    -> { return; }
        }
        e.consume();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

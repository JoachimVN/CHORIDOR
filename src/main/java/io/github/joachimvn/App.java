package io.github.joachimvn;

import io.github.joachimvn.ui.GameController;
import io.github.joachimvn.ui.BoardView;
import io.github.joachimvn.ui.bars.BottomBar;
import io.github.joachimvn.ui.bars.ReviewBar;
import io.github.joachimvn.ui.bars.TopBar;
import io.github.joachimvn.ui.common.UiScale;
import io.github.joachimvn.ui.overlays.GameOverOverlay;
import io.github.joachimvn.ui.overlays.LandingOverlay;
import io.github.joachimvn.ui.overlays.SplashOverlay;
import io.github.joachimvn.ui.tournament.TournamentView;

import javafx.application.Application;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.logging.Logger;

/**
 * Application entry point and UI orchestrator. Each chrome bar and overlay lives in its own class
 * in {@code io.github.joachimvn.ui}; this class wires them together around a shared
 * {@link GameController} and the board-derived {@code scaleB} that all of them scale against.
 */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

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
        // Tournament overlay is created first so setup can reference it; the start
        // callback is wired after setup is ready via a one-element array (lambda capture).
        Runnable[] startTournament = {null};
        LandingOverlay landing = new LandingOverlay(ctrl, board, bottomBar::setFlipSelected,
            () -> { if (startTournament[0] != null) startTournament[0].run(); });
        TournamentView tournament = new TournamentView(() -> landing.getRoot().setVisible(true), ctrl);
        startTournament[0] = tournament::start;

        GameOverOverlay gameOver = new GameOverOverlay(ctrl, landing.getRoot());

        bottomBar.setOnChangeMode(() -> {
            gameOver.getRoot().setVisible(false);
            landing.getRoot().setVisible(true);
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

            gameOver.update(landing.getRoot().isVisible());
        });

        // Prime the board with a default game so it's visible through the landing overlay
        ctrl.startGame(null, null, "Player 1", "Player 2");
        board.refresh();
        topBar.update(ctrl);
        bottomBar.updateStatus();

        SplashOverlay splash = new SplashOverlay();
        StackPane sceneRoot = new StackPane(gamePane, gameOver.getRoot(), landing.getRoot(),
                                            tournament.getRoot(), splash.getRoot());

        Scene scene = new Scene(sceneRoot);
        for (String sheet : new String[]{"base", "chrome", "setup", "landing", "game-over", "tournament"}) {
            scene.getStylesheets().add(getClass().getResource("/css/" + sheet + ".css").toExternalForm());
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED,
            e -> handleKey(e, stage, ctrl, gameOver));

        stage.getIcons().add(new Image(getClass().getResourceAsStream(
            "/images/logos/CHORIDOR_Logo_Square.png")));
        stage.setTitle("CHORIDOR");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(300);
        stage.setMinHeight(360);
        stage.setMaximized(true);
        stage.show();
        splash.playAndExit();
    }

    private static void handleKey(KeyEvent e, Stage stage, GameController ctrl,
                                  GameOverOverlay gameOver) {
        KeyCode code = e.getCode();
        if (code == KeyCode.D && e.isControlDown()) {
            LOG.info(ctrl::boardStateText);
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

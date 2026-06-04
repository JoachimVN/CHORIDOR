package io.github.joachimvn;

import java.util.logging.Logger;

public class App {
    private App() {}

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    public static void run() {
        LOGGER.info("Hello World!");
    }
}

package io.github.joachimvn;

// Separate entry point required for fat-JAR execution.
// App extends javafx.application.Application, which cannot be the manifest
// Main-Class when running from an unnamed-module classpath JAR.
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}

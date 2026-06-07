package io.github.joachimvn.ui.common;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.DoubleExpression;

/** Factory for the shared chrome scale factor. */
public final class UiScale {

    private UiScale() {}

    /**
     * A scale that follows {@code raw} but ignores changes smaller than {@code band}.
     *
     * <p>The chrome bars scale their padding by this factor, and the factor is derived from the
     * board width. When the window is height-constrained the square board's width is in turn set
     * by how much vertical space the bars leave behind — so without a dead-band a 1–2px wobble
     * feeds back on itself forever (the "vibration": board 1163↔1165 ⇄ bar height 100↔101).
     * Quantising the scale so tiny deltas don't re-emit a value breaks that limit cycle while
     * staying visually indistinguishable during a real resize.
     */
    public static DoubleBinding hysteretic(DoubleExpression raw, double band) {
        return new DoubleBinding() {
            private double applied = Double.NaN;
            { bind(raw); }

            @Override
            protected double computeValue() {
                double v = raw.get();
                if (Double.isNaN(applied) || Math.abs(v - applied) >= band) applied = v;
                return applied;
            }
        };
    }
}

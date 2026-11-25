package org.micromanager.lightsheetmanager.gui.tabs.experiment;

/**
 * Maps a property's limits onto 0-100 %, e.g. a laser's 0-5 V analog input. The percent is of the
 * control range, not of optical power: a laser's output need not be linear in its voltage.
 */
public final class PercentScale {

    private final double lower;
    private final double upper;

    public PercentScale(final double lower, final double upper) {
        if (!(upper > lower)) {
            throw new IllegalArgumentException(
                    "limits " + lower + " to " + upper + " have no range to scale");
        }
        this.lower = lower;
        this.upper = upper;
    }

    public double toPercent(final double value) {
        return clamp((value - lower) / (upper - lower) * 100.0, 0.0, 100.0);
    }

    public double fromPercent(final double percent) {
        return lower + clamp(percent, 0.0, 100.0) / 100.0 * (upper - lower);
    }

    public double lower() {
        return lower;
    }

    public double upper() {
        return upper;
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

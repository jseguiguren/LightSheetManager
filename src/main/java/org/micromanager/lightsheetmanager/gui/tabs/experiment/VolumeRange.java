package org.micromanager.lightsheetmanager.gui.tabs.experiment;

/**
 * A volume as a range and a step, in µm, and the number of slices LSM takes for it. Both ends are
 * imaged, so the slices cover (slices - 1) steps.
 */
public final class VolumeRange {

    private VolumeRange() {
    }

    // steps within this fraction of a whole number count as whole, so 100 µm at 0.1 µm, which
    // divides to 1000.0000000000001 in doubles, is 1001 slices and not 1002
    private static final double WHOLE = 1e-6;

    /**
     * The whole slices that cover at least {@code rangeUm} at {@code stepUm}: a range between
     * two steps gets one slice more, never one less.
     */
    public static int slicesFor(final double rangeUm, final double stepUm) {
        if (!(stepUm > 0.0)) {
            throw new IllegalArgumentException("a step of " + stepUm + " µm has no slices");
        }
        final double steps = Math.max(0.0, rangeUm) / stepUm;
        return (int) Math.ceil(steps - WHOLE) + 1;
    }

    /** The range {@code slices} slices cover at {@code stepUm}. */
    public static double rangeFor(final int slices, final double stepUm) {
        return Math.max(0, slices - 1) * stepUm;
    }
}

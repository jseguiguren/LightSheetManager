package org.micromanager.lightsheetmanager.gui.tabs.experiment;

/**
 * A volume as a range and a step, in µm, and the number of slices LSM takes for it. Both ends are
 * imaged, so the slices cover (slices - 1) steps.
 */
public final class VolumeRange {

    private VolumeRange() {
    }

    /** The slices that cover {@code rangeUm} at {@code stepUm}, to the nearest slice. */
    public static int slicesFor(final double rangeUm, final double stepUm) {
        if (!(stepUm > 0.0)) {
            throw new IllegalArgumentException("a step of " + stepUm + " µm has no slices");
        }
        return (int) Math.max(1, Math.round(Math.max(0.0, rangeUm) / stepUm) + 1);
    }

    /** The range {@code slices} slices cover at {@code stepUm}. */
    public static double rangeFor(final int slices, final double stepUm) {
        return Math.max(0, slices - 1) * stepUm;
    }
}

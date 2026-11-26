package org.micromanager.lightsheetmanager.gui.tabs.experiment;

/**
 * A SCAPE volume as a scan range and a step, in µm, and the number of slices LSM takes for it.
 *
 * <p>The sheet is tilted by alpha from the vertical (LSM's first view angle), so beta = 90° -
 * alpha from the horizontal. The step is LSM's slice step, the perpendicular distance between
 * sheets; along the scan it is step / sin(beta). A tilted sheet reaches {@code g = h sin(alpha)}
 * beyond its centre at each end, h being half the cropped image height along the sheet: a range
 * covered at every depth needs a scan of range + 2g.
 */
public final class VolumeRange {

    private VolumeRange() {
    }

    // steps within this fraction of a whole number count as whole, so 100 µm at 0.1 µm, which
    // divides to 1000.0000000000001 in doubles, is 1001 slices and not 1003
    private static final double WHOLE = 1e-6;

    /**
     * An odd number of slices for {@code scanUm} at {@code lateralStepUm}, so the centre slice is
     * the plane Live shows: ceil(steps / 2 - 0.5) * 2 + 1, at most one step short at the border.
     */
    public static int slicesFor(final double scanUm, final double lateralStepUm) {
        if (!(lateralStepUm > 0.0)) {
            throw new IllegalArgumentException("a step of " + lateralStepUm + " µm has no slices");
        }
        final double steps = Math.max(0.0, scanUm) / lateralStepUm;
        return (int) Math.max(0.0, Math.ceil(steps / 2.0 - 0.5 - WHOLE)) * 2 + 1;
    }

    /** The scan {@code slices} slices cover at {@code lateralStepUm}. */
    public static double rangeFor(final int slices, final double lateralStepUm) {
        return Math.max(0, slices - 1) * lateralStepUm;
    }

    /** The step along the scan: LSM's slice step over sin(90° - alpha). */
    public static double lateralStepUm(final double stepUm, final double alphaDeg) {
        return stepUm / Math.sin(Math.toRadians(90.0 - checkAngle(alphaDeg)));
    }

    /**
     * g: how far the tilted sheet reaches beyond its centre along the scan, at each end.
     *
     * @param heightPx the cropped image height (the axis along the sheet, not the flipped one)
     * @param pixelUm the binned pixel size in the sample
     */
    public static double extraPerEndUm(final int heightPx, final double pixelUm,
                                       final double alphaDeg) {
        return heightPx / 2.0 * pixelUm * Math.sin(Math.toRadians(checkAngle(alphaDeg)));
    }

    private static double checkAngle(final double alphaDeg) {
        if (!(alphaDeg > 0.0 && alphaDeg < 90.0)) {
            throw new IllegalArgumentException("the sheet angle must be between 0 and 90°, got "
                    + alphaDeg + "° (LSM Settings tab, first view angle)");
        }
        return alphaDeg;
    }
}

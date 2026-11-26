package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.awt.Rectangle;

/**
 * A camera crop centred on the frame, in binned pixels. With two cameras, one mirrored, only a
 * crop centred to the pixel frames the same region on both: the Multi Camera device sends both the
 * same coordinates (Utilities MultiCamera::SetROI), and a mirrored camera reads them from the other
 * side. So a margin that would be odd gets one pixel more of crop, never one less.
 */
public final class CenteredCrop {

    // PVCAM refuses an ROI smaller than 2 x 2 (PVCAMUniversal.cpp, Universal::SetROI)
    private static final int MIN_PX = 2;
    // a size within this of a whole pixel counts as whole, so 97.75 µm at 0.391 µm/px is 250 px
    private static final double WHOLE = 1e-6;

    private CenteredCrop() {
    }

    /** The centred crop of about {@code width} x {@code height} on a {@code frame} in px. */
    public static Rectangle centered(final int frameWidth, final int frameHeight,
                                     final int width, final int height) {
        final int w = side(frameWidth, width);
        final int h = side(frameHeight, height);
        return new Rectangle((frameWidth - w) / 2, (frameHeight - h) / 2, w, h);
    }

    private static int side(final int frame, final int wanted) {
        int s = Math.max(MIN_PX, Math.min(frame, wanted));
        if ((frame - s) % 2 != 0) {
            s += 1; // never past the frame: an odd margin is at least 1
        }
        return s;
    }

    /**
     * The whole pixels that cover at least {@code um} at {@code pixelSizeUm} µm per binned pixel
     * (MMCore's getPixelSizeUm, which already includes the binning).
     */
    public static int pixelsFor(final double um, final double pixelSizeUm) {
        if (!(pixelSizeUm > 0)) {
            throw new IllegalArgumentException("no pixel size: define one for this camera and "
                    + "binning in Micro-Manager's Pixel Size Calibration");
        }
        return (int) Math.ceil(um / pixelSizeUm - WHOLE);
    }
}

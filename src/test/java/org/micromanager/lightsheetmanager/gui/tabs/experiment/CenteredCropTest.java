package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import static org.junit.Assert.assertEquals;

import java.awt.Rectangle;
import org.junit.Test;

public class CenteredCropTest {

    @Test
    public void aCropIsCenteredOnTheFrame() {
        assertEquals(new Rectangle(400, 600, 800, 400), CenteredCrop.centered(1600, 1600, 800, 400));
    }

    @Test
    public void anOddMarginGetsOnePixelMoreNeverOneLess() {
        // 1600 - 801 = 799 is odd: 802 keeps the crop centred to the pixel
        assertEquals(new Rectangle(399, 399, 802, 802), CenteredCrop.centered(1600, 1600, 801, 801));
    }

    @Test
    public void aMirroredCameraFramesTheSameRegion() {
        // with one camera flipped left-right, a centred crop lands on the same pixels of the sample
        final Rectangle r = CenteredCrop.centered(1600, 1200, 333, 517);
        assertEquals(r.x, 1600 - r.x - r.width);
        assertEquals(r.y, 1200 - r.y - r.height);
    }

    @Test
    public void aCropLargerThanTheFrameIsTheFrame() {
        assertEquals(new Rectangle(0, 0, 1600, 1600), CenteredCrop.centered(1600, 1600, 5000, 2000));
    }

    @Test
    public void aCropIsAtLeastTwoByTwo() {
        // PVCAM refuses smaller (PVCAMUniversal.cpp SetROI)
        assertEquals(new Rectangle(799, 799, 2, 2), CenteredCrop.centered(1600, 1600, 0, 1));
    }

    @Test
    public void micronsBecomeWholePixelsRoundedUp() {
        assertEquals(256, CenteredCrop.pixelsFor(100.0, 0.391)); // 255.75 px
        assertEquals(250, CenteredCrop.pixelsFor(97.75, 0.391)); // exactly 250, not 251
    }

    @Test(expected = IllegalArgumentException.class)
    public void micronsNeedAPixelSize() {
        CenteredCrop.pixelsFor(100.0, 0.0); // MMCore's 0: no pixel size preset matches
    }
}

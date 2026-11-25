package org.micromanager.lightsheetmanager.liveview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import org.micromanager.PropertyMaps;
import org.micromanager.lightsheetmanager.model.PluginSettings;

public class LiveViewOrientationTest {

    // 3 x 2 image, row-major:  1 2 3
    //                          4 5 6
    private static ShortProcessor image() {
        return new ShortProcessor(3, 2, new short[] {1, 2, 3, 4, 5, 6}, null);
    }

    private static short[] pixels(final ImageProcessor proc) {
        return (short[]) proc.getPixels();
    }

    @Test
    public void noneFlipsNothing() {
        final LiveViewOrientation none = LiveViewOrientation.none();
        assertTrue(none.isEmpty());
        assertFalse(none.flipsHorizontally("Camera"));
        assertFalse(none.flipsVertically("Camera"));
    }

    @Test
    public void flipsAreSetPerCamera() {
        final LiveViewOrientation o = LiveViewOrientation.none()
                .with("CamA", true, false)
                .with("CamB", false, true);
        assertTrue(o.flipsHorizontally("CamA"));
        assertFalse(o.flipsVertically("CamA"));
        assertFalse(o.flipsHorizontally("CamB"));
        assertTrue(o.flipsVertically("CamB"));
        assertFalse(o.flipsHorizontally("CamC"));
        assertFalse(o.isEmpty());
    }

    @Test
    public void withIsAValueChangeAndClearsWhenBothOff() {
        final LiveViewOrientation flipped = LiveViewOrientation.none().with("CamA", true, true);
        final LiveViewOrientation cleared = flipped.with("CamA", false, false);
        assertTrue(flipped.flipsHorizontally("CamA")); // unchanged by the second call
        assertTrue(cleared.isEmpty());
    }

    @Test
    public void horizontalReversesEachRow() {
        final ShortProcessor proc = image();
        LiveViewOrientation.none().with("CamA", true, false).apply(proc, "CamA");
        assertArrayEquals(new short[] {3, 2, 1, 6, 5, 4}, pixels(proc));
    }

    @Test
    public void verticalReversesTheRows() {
        final ShortProcessor proc = image();
        LiveViewOrientation.none().with("CamA", false, true).apply(proc, "CamA");
        assertArrayEquals(new short[] {4, 5, 6, 1, 2, 3}, pixels(proc));
    }

    @Test
    public void bothIsARotationBy180() {
        final ShortProcessor proc = image();
        LiveViewOrientation.none().with("CamA", true, true).apply(proc, "CamA");
        assertArrayEquals(new short[] {6, 5, 4, 3, 2, 1}, pixels(proc));
    }

    @Test
    public void otherCamerasAndUnnamedImagesAreLeftAlone() {
        final LiveViewOrientation o = LiveViewOrientation.none().with("CamA", true, true);
        final ShortProcessor other = image();
        o.apply(other, "CamB");
        assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6}, pixels(other));
        final ShortProcessor unnamed = image();
        o.apply(unnamed, null);
        assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6}, pixels(unnamed));
    }

    @Test
    public void eightBitImagesFlipToo() {
        final ByteProcessor proc = new ByteProcessor(3, 1, new byte[] {1, 2, 3}, null);
        LiveViewOrientation.none().with("CamA", true, false).apply(proc, "CamA");
        assertArrayEquals(new byte[] {3, 2, 1}, (byte[]) proc.getPixels());
    }

    @Test
    public void propertyMapRoundTrip() {
        final LiveViewOrientation o = LiveViewOrientation.none()
                .with("CamA", true, false)
                .with("CamB", true, true);
        assertEquals(o, LiveViewOrientation.fromPropertyMap(o.toPropertyMap()));
    }

    @Test
    public void emptyPropertyMapMeansNoFlip() {
        assertTrue(LiveViewOrientation.fromPropertyMap(PropertyMaps.emptyPropertyMap()).isEmpty());
    }

    @Test
    public void savedWithThePluginSettings() {
        final PluginSettings settings = new PluginSettings();
        final LiveViewOrientation o = LiveViewOrientation.none().with("CamA", false, true);
        settings.liveViewOrientation(o);
        assertEquals(o, PluginSettings.fromJson(settings.toJson()).liveViewOrientation());
    }

    @Test
    public void settingsSavedBeforeThisFeatureLoadWithNoFlip() {
        final PluginSettings old = PluginSettings.fromJson("{\"isPollingPositions\":true}");
        assertTrue(old.liveViewOrientation().isEmpty());
    }
}

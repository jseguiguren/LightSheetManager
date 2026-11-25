package org.micromanager.lightsheetmanager.liveview;

import ij.process.ImageProcessor;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

/**
 * Which cameras' Live images are mirrored, and on which axis. Immutable: {@link #with} returns a
 * changed copy. Only the Live view uses it; acquired images are never flipped.
 */
public final class LiveViewOrientation {

    // keys of the pipeline step's settings, each a list of camera device names
    private static final String FLIP_HORIZONTAL = "FlipHorizontal";
    private static final String FLIP_VERTICAL = "FlipVertical";

    // camera device names; sorted once built by with() or fromPropertyMap()
    private final Set<String> horizontal;
    private final Set<String> vertical;

    // for Gson, which fills the fields from the saved settings
    private LiveViewOrientation() {
        this(new TreeSet<>(), new TreeSet<>());
    }

    private LiveViewOrientation(final Set<String> horizontal, final Set<String> vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public static LiveViewOrientation none() {
        return new LiveViewOrientation();
    }

    /**
     * Returns a copy in which {@code camera} is mirrored as given.
     *
     * @param camera the camera's device name, as images carry it in their metadata
     * @param flipHorizontal mirror left-right (reverse the x axis)
     * @param flipVertical mirror top-bottom (reverse the y axis)
     */
    public LiveViewOrientation with(final String camera, final boolean flipHorizontal,
                                    final boolean flipVertical) {
        final Set<String> h = new TreeSet<>(horizontal);
        final Set<String> v = new TreeSet<>(vertical);
        if (flipHorizontal) {
            h.add(camera);
        } else {
            h.remove(camera);
        }
        if (flipVertical) {
            v.add(camera);
        } else {
            v.remove(camera);
        }
        return new LiveViewOrientation(h, v);
    }

    public boolean flipsHorizontally(final String camera) {
        return horizontal.contains(camera);
    }

    public boolean flipsVertically(final String camera) {
        return vertical.contains(camera);
    }

    public boolean isEmpty() {
        return horizontal.isEmpty() && vertical.isEmpty();
    }

    /**
     * Mirrors {@code proc} in place as set for {@code camera}; leaves it alone for any other
     * camera, or when the image does not name its camera.
     */
    public void apply(final ImageProcessor proc, final String camera) {
        if (camera == null) {
            return;
        }
        if (horizontal.contains(camera)) {
            proc.flipHorizontal();
        }
        if (vertical.contains(camera)) {
            proc.flipVertical();
        }
    }

    public PropertyMap toPropertyMap() {
        return PropertyMaps.builder()
                .putStringList(FLIP_HORIZONTAL, horizontal)
                .putStringList(FLIP_VERTICAL, vertical)
                .build();
    }

    public static LiveViewOrientation fromPropertyMap(final PropertyMap settings) {
        return new LiveViewOrientation(
                new TreeSet<>(settings.getStringList(FLIP_HORIZONTAL, Collections.emptyList())),
                new TreeSet<>(settings.getStringList(FLIP_VERTICAL, Collections.emptyList())));
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof LiveViewOrientation)) {
            return false;
        }
        final LiveViewOrientation o = (LiveViewOrientation) other;
        return horizontal.equals(o.horizontal) && vertical.equals(o.vertical);
    }

    @Override
    public int hashCode() {
        return 31 * horizontal.hashCode() + vertical.hashCode();
    }

    @Override
    public String toString() {
        return "flip horizontal " + horizontal + ", flip vertical " + vertical;
    }
}

package org.micromanager.lightsheetmanager.liveview;

import ij.process.ImageProcessor;
import org.micromanager.data.Image;
import org.micromanager.data.ImageJConverter;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;

/**
 * Mirrors each image as its camera is set in a {@link LiveViewOrientation}; images of other
 * cameras pass through untouched.
 */
public class LiveViewOrientationProcessor implements Processor {

    private final ImageJConverter ij_;
    private final LiveViewOrientation orientation_;

    public LiveViewOrientationProcessor(final ImageJConverter ij,
                                        final LiveViewOrientation orientation) {
        ij_ = ij;
        orientation_ = orientation;
    }

    @Override
    public void processImage(final Image image, final ProcessorContext context) {
        final String camera = image.getMetadata().getCamera();
        if (camera == null || !(orientation_.flipsHorizontally(camera)
                || orientation_.flipsVertically(camera))) {
            context.outputImage(image);
            return;
        }
        // createProcessor copies the pixels (DefaultImageJConverter), so the original stays as is
        final ImageProcessor proc = ij_.createProcessor(image);
        orientation_.apply(proc, camera);
        context.outputImage(ij_.createImage(proc, image.getCoords(), image.getMetadata()));
    }
}

package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import ij.ImagePlus;
import ij.gui.Roi;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Objects;
import javax.swing.JLabel;
import org.micromanager.Studio;
import org.micromanager.display.DisplayWindow;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

/**
 * Crops every active imaging camera to the same centred region (see {@link CenteredCrop}), sized
 * in pixels or µm, or from a rectangle drawn on the Live window. Cropping is applied as
 * Micro-Manager's own Set ROI does it for several cameras: Live suspended, never closed.
 */
public class CropPanel extends Panel {

    private static final String PX = "px";
    private static final String UM = "µm";

    private final LightSheetManager model_;
    private final JLabel lblFrame_ = new JLabel();
    private final JLabel lblNow_ = new JLabel();
    private final JLabel lblResult_ = new JLabel(" ");
    private final ComboBox<String> cmbUnit_;
    private final Spinner spnWidth_;
    private final Spinner spnHeight_;

    public CropPanel(final LightSheetManager model) {
        super();
        model_ = Objects.requireNonNull(model);
        cmbUnit_ = new ComboBox<>(new String[] {PX, UM}, PX, 60, 22);
        spnWidth_ = Spinner.createDoubleSpinner(512.0, 0.0, 100000.0, 1.0, 5);
        spnHeight_ = Spinner.createDoubleSpinner(512.0, 0.0, 100000.0, 1.0, 5);
        spnWidth_.setToolTipText("Width (X) of the crop; it is always centred.");
        spnHeight_.setToolTipText("Height (Y) of the crop; it is always centred.");
        final Button btnApply = new Button("Apply centred", 115, 26);
        btnApply.setToolTipText("Crop every active imaging camera to this size, centred, so "
                + "both views frame the same region even with one camera mirrored.");
        final Button btnFull = new Button("Full frame", 90, 26);
        final Button btnFromLive = new Button("From Live rectangle", 150, 26);
        btnFromLive.setToolTipText("Take the size of the rectangle drawn on the Live window "
                + "(ImageJ rectangle tool); Apply centred then crops to it, centred.");

        // six rows: the Cameras / Crop tabs take the taller of the two, and the column is full
        add(lblFrame_, "span, wrap");
        add(lblNow_, "span, wrap");
        add(new JLabel("X x Y:"), "split 4, span");
        add(spnWidth_, "");
        add(spnHeight_, "");
        add(cmbUnit_, "wrap");
        add(btnApply, "split 2, span");
        add(btnFull, "wrap");
        add(btnFromLive, "span, wrap");
        add(lblResult_, "span, w 230!");

        cmbUnit_.registerListener(this::convertFields);
        btnApply.registerListener(() -> apply(false));
        btnFull.registerListener(() -> apply(true));
        btnFromLive.registerListener(this::fromLive);
        refresh();
    }

    /** Shows the frame, the pixel size and the crop now set; the camera can change outside. */
    public void refresh() {
        final CameraBase camera = firstCamera();
        if (camera == null) {
            lblFrame_.setText("No active imaging camera");
            lblNow_.setText(" ");
            return;
        }
        final Rectangle frame = frame(camera);
        final double um = pixelSizeUm();
        lblFrame_.setText(String.format("Frame %d x %d px (%dx%d)%s", frame.width, frame.height,
                camera.getBinning(), camera.getBinning(),
                um > 0 ? String.format(", %.4f µm/px", um) : ", no pixel size"));
        final Rectangle roi = camera.getROI();
        lblNow_.setText(String.format("Now %d x %d px at (%d, %d)", roi.width, roi.height,
                roi.x, roi.y));
    }

    private CameraBase firstCamera() {
        final Collection<CameraBase> cameras = model_.devices().activeImagingCameras().values();
        return cameras.isEmpty() ? null : cameras.iterator().next();
    }

    private static Rectangle frame(final CameraBase camera) {
        final Rectangle sensor = camera.getResolution();
        final int binning = Math.max(1, camera.getBinning());
        return new Rectangle(0, 0, sensor.width / binning, sensor.height / binning);
    }

    private double pixelSizeUm() {
        try {
            return model_.studio().core().getPixelSizeUm(); // includes binning (MMCore.cpp)
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Converts the two fields when the unit changes, so the size stays the same. */
    private void convertFields() {
        final double um = pixelSizeUm();
        if (um <= 0) {
            cmbUnit_.setSelected(PX);
            lblResult_.setText("No pixel size for this camera and binning: px only.");
            return;
        }
        final boolean toUm = UM.equals(cmbUnit_.getSelected());
        final double factor = toUm ? um : 1.0 / um;
        spnWidth_.setDouble(Math.round(spnWidth_.getDouble() * factor * 100.0) / 100.0);
        spnHeight_.setDouble(Math.round(spnHeight_.getDouble() * factor * 100.0) / 100.0);
    }

    private int[] sizePx() {
        if (UM.equals(cmbUnit_.getSelected())) {
            final double um = pixelSizeUm();
            return new int[] {CenteredCrop.pixelsFor(spnWidth_.getDouble(), um),
                    CenteredCrop.pixelsFor(spnHeight_.getDouble(), um)};
        }
        return new int[] {(int) Math.ceil(spnWidth_.getDouble() - 1e-6),
                (int) Math.ceil(spnHeight_.getDouble() - 1e-6)};
    }

    private void fromLive() {
        final DisplayWindow display = model_.studio().live().getDisplay();
        final ImagePlus image = display == null ? null : display.getImagePlus();
        final Roi roi = image == null ? null : image.getRoi();
        if (roi == null || roi.getType() != Roi.RECTANGLE) {
            lblResult_.setText("Draw a rectangle on the Live window first (ImageJ rectangle tool).");
            return;
        }
        final Rectangle r = roi.getBounds();
        cmbUnit_.setSelected(PX);
        spnWidth_.setDouble(r.width);
        spnHeight_.setDouble(r.height);
        lblResult_.setText(String.format("From Live: %d x %d px; Apply centred to crop.",
                r.width, r.height));
    }

    private void apply(final boolean fullFrame) {
        if (model_.acquisitions().isRunning()) {
            lblResult_.setText("Not during an acquisition.");
            return;
        }
        final Collection<CameraBase> cameras = model_.devices().activeImagingCameras().values();
        if (cameras.isEmpty()) {
            lblResult_.setText("No active imaging camera.");
            return;
        }
        // one crop for all: the cameras must have the same frame
        final Rectangle frame = frame(cameras.iterator().next());
        for (final CameraBase camera : cameras) {
            if (!frame(camera).equals(frame)) {
                lblResult_.setText("The cameras differ in sensor size or binning: set the same "
                        + "binning on both first.");
                return;
            }
        }
        final Rectangle crop;
        try {
            if (fullFrame) {
                crop = frame;
            } else {
                final int[] px = sizePx();
                crop = CenteredCrop.centered(frame.width, frame.height, px[0], px[1]);
            }
        } catch (IllegalArgumentException e) {
            lblResult_.setText(e.getMessage());
            return;
        }
        final Studio studio = model_.studio();
        // as Micro-Manager's Set ROI does for several cameras (MMROIManager.setROI)
        studio.live().setSuspended(true);
        final StringBuilder failed = new StringBuilder();
        try {
            for (final CameraBase camera : cameras) {
                try {
                    studio.core().setROI(camera.getDeviceName(), crop.x, crop.y,
                            crop.width, crop.height);
                } catch (Exception e) {
                    failed.append(camera.getDeviceName()).append(": ").append(e.getMessage())
                            .append(". ");
                }
            }
        } finally {
            studio.live().setSuspended(false);
            studio.app().refreshGUI(); // Micro-Manager's window reads the new ROI
        }
        model_.acquisitions().updateDurationLabels(); // the slice timing depends on the ROI
        refresh();
        final double um = pixelSizeUm();
        lblResult_.setText(failed.length() > 0
                ? "Not set on " + failed
                : String.format("Cropped to %d x %d px%s, centred.", crop.width, crop.height,
                        um > 0 ? String.format(" (%.1f x %.1f µm)", crop.width * um,
                                crop.height * um) : ""));
    }
}

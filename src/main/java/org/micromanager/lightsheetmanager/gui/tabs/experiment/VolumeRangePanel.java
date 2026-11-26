package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

/**
 * The volume as a range along the scan and a step in µm; LSM's slices per volume follow from them
 * ({@link VolumeRange}), with the sheet angle from LSM's Settings tab (first view angle). With
 * Full coverage, the scan adds the tilted sheet's reach at both ends, from the cropped image height
 * and the binned pixel size, so the range is imaged at every depth. The Acquisition tab shows the
 * same volume as slices and step.
 */
public class VolumeRangePanel extends Panel implements SettingsListener {

    // how often the crop, binning, pixel size and angle are checked for a change
    private static final int GEOMETRY_POLL_MS = 1000;

    private final LightSheetManager model_;
    private final Spinner spnRange_;
    private final Spinner spnStep_;
    private final CheckBox cbxFull_;
    private final JLabel lblSlices_;
    private final Timer geometryPoll_;
    private Geometry applied_;
    private boolean refreshing_;

    /** What the slices depend on besides the two fields. */
    private static final class Geometry {
        final double alphaDeg;
        final int heightPx;
        final double pixelUm;
        final boolean full;

        Geometry(final double alphaDeg, final int heightPx, final double pixelUm,
                 final boolean full) {
            this.alphaDeg = alphaDeg;
            this.heightPx = heightPx;
            this.pixelUm = pixelUm;
            this.full = full;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Geometry)) {
                return false;
            }
            final Geometry g = (Geometry) o;
            return alphaDeg == g.alphaDeg && heightPx == g.heightPx && pixelUm == g.pixelUm
                    && full == g.full;
        }

        @Override
        public int hashCode() {
            return Objects.hash(alphaDeg, heightPx, pixelUm, full);
        }

        /** g, or 0 without Full coverage; NaN when it cannot be computed. */
        double extraPerEndUm() {
            if (!full) {
                return 0.0;
            }
            return heightPx > 0 && pixelUm > 0
                    ? VolumeRange.extraPerEndUm(heightPx, pixelUm, alphaDeg) : Double.NaN;
        }
    }

    public VolumeRangePanel(final LightSheetManager model) {
        super("Volume");
        model_ = Objects.requireNonNull(model);
        final ScapeAcquisitionSettings settings = model_.acquisitions().settings();
        spnStep_ = Spinner.createDoubleSpinner(
                settings.volume().sliceStepSize(), 0.01, 100.0, 0.1, 6);
        spnRange_ = Spinner.createDoubleSpinner(0.0, 0.0, 100000.0, 1.0, 6);
        cbxFull_ = new CheckBox("Full coverage at every depth",
                model_.pluginSettings().isVolumeFullCoverage());
        lblSlices_ = new JLabel();
        spnRange_.setToolTipText("Length of the volume along the scan, centred on the Imaging "
                + "Center.");
        spnStep_.setToolTipText("Distance between sheets, perpendicular to them (LSM's slice "
                + "step); along the scan it is step / sin(90° - angle).");
        cbxFull_.setToolTipText("Scan the range plus the tilted sheet's reach at both ends "
                + "(half the cropped image height x pixel size x sin(angle)), so the range is "
                + "imaged at every depth. Off: the range is the scan itself.");

        add(new JLabel("Range [µm]:"), "");
        add(spnRange_, "wrap");
        add(new JLabel("Step [µm]:"), "");
        add(spnStep_, "wrap");
        add(cbxFull_, "span 2, wrap");
        add(lblSlices_, "span 2, w 250!");

        applied_ = geometry();
        showRangeFor(settings.volume().slicesPerView(), settings.volume().sliceStepSize(),
                applied_);

        spnRange_.registerListener(this::apply);
        spnStep_.registerListener(this::apply);
        cbxFull_.registerListener(() -> {
            model_.pluginSettings().setVolumeFullCoverage(cbxFull_.isSelected());
            apply();
        });
        model_.userSettings().addChangeListener(this);
        // a crop, a binning or an angle set elsewhere changes the scan the range needs
        geometryPoll_ = new Timer(GEOMETRY_POLL_MS, e -> {
            if (isShowing() && !model_.acquisitions().isRunning() && !geometry().equals(applied_)) {
                apply();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        geometryPoll_.start();
    }

    @Override
    public void removeNotify() {
        geometryPoll_.stop();
        super.removeNotify();
    }

    private Geometry geometry() {
        final double alpha = model_.acquisitions().settings().stageScan().firstViewAngle();
        int height = 0;
        final Collection<CameraBase> cameras =
                model_.devices().activeImagingCameras().values();
        if (!cameras.isEmpty()) {
            // the image's top-bottom axis lies along the sheet; both cameras share the crop.
            // Binned pixels. From the Core, not CameraBase.getROI: that shows an error dialog,
            // once a second here while a camera cannot answer
            try {
                final Rectangle roi = model_.studio().core().getROI(
                        cameras.iterator().next().getDeviceName());
                height = roi == null ? 0 : roi.height;
            } catch (Exception e) {
                height = 0;
            }
        }
        double pixel;
        try {
            pixel = model_.studio().core().getPixelSizeUm(); // binned (MMCore.cpp)
        } catch (Exception e) {
            pixel = 0.0;
        }
        return new Geometry(alpha, height, pixel, cbxFull_.isSelected());
    }

    private void apply() {
        if (refreshing_) {
            return;
        }
        if (model_.acquisitions().isRunning()) {
            return; // the tab is locked; a late event changes nothing
        }
        final Geometry geometry = geometry();
        applied_ = geometry;
        final double step = spnStep_.getDouble();
        final double lateral;
        try {
            lateral = VolumeRange.lateralStepUm(step, geometry.alphaDeg);
        } catch (IllegalArgumentException e) {
            showProblem(e.getMessage());
            return;
        }
        final double g = geometry.extraPerEndUm();
        if (Double.isNaN(g)) {
            showProblem("Full coverage needs a cropped height and a pixel size for this "
                    + "camera and binning: slices not changed.");
            return;
        }
        final double range = spnRange_.getDouble();
        final int slices = VolumeRange.slicesFor(range + 2 * g, lateral);
        model_.acquisitions().settingsBuilder().volumeBuilder()
                .sliceStepSize(step)
                .slicesPerView(slices);
        model_.acquisitions().updateDurationLabels();
        showSlices(slices, lateral, range, g, geometry);
    }

    /** Sets the range field to what {@code slices} cover, unless the typed range gives them. */
    private void showRangeFor(final int slices, final double step, final Geometry geometry) {
        final double lateral;
        try {
            lateral = VolumeRange.lateralStepUm(step, geometry.alphaDeg);
        } catch (IllegalArgumentException e) {
            showProblem(e.getMessage());
            return;
        }
        final double g = geometry.extraPerEndUm();
        final double extra = Double.isNaN(g) ? 0.0 : 2 * g;
        refreshing_ = true;
        spnStep_.setDouble(step);
        if (VolumeRange.slicesFor(spnRange_.getDouble() + extra, lateral) != slices) {
            spnRange_.setDouble(Math.round(Math.max(0.0,
                    VolumeRange.rangeFor(slices, lateral) - extra) * 100.0) / 100.0);
        }
        refreshing_ = false;
        showSlices(slices, lateral, spnRange_.getDouble(), Double.isNaN(g) ? 0.0 : g, geometry);
    }

    private void showSlices(final int slices, final double lateral, final double range,
                            final double g, final Geometry geometry) {
        final double scan = VolumeRange.rangeFor(slices, lateral);
        final String how = geometry.full
                ? String.format(" (%.1f + 2 x %.1f)", range, g) : "";
        lblSlices_.setText(String.format("<html>%d slices, scan %.1f µm%s<br>"
                        + "angle %.1f° (Settings tab), %.3f µm along the scan</html>",
                slices, scan, how, geometry.alphaDeg, lateral));
    }

    private void showProblem(final String text) {
        lblSlices_.setText("<html>" + text.replace("<", "&lt;") + "</html>");
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (!(settings instanceof ScapeAcquisitionSettings)) {
            return;
        }
        final ScapeAcquisitionSettings scape = (ScapeAcquisitionSettings) settings;
        showRangeFor(scape.volume().slicesPerView(), scape.volume().sliceStepSize(), geometry());
    }
}

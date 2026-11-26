package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

/**
 * The volume as a range along the scan and a step in µm; LSM's slices per volume follow from them
 * ({@link VolumeRange}), with the sheet angle from LSM's Settings tab (first view angle). The
 * value typed is either the effective range, the region covered by every image row, so the scan
 * adds the tilted sheet's reach g at both ends (from the cropped image height and the binned pixel
 * size), or the actual range, the scan itself, g included. The Acquisition tab shows the same
 * volume as slices and step.
 */
public class VolumeRangePanel extends Panel implements SettingsListener {

    private static final String EFFECTIVE = "Effective range";
    private static final String ACTUAL = "Actual range";

    // how often the crop, binning, pixel size and angle are checked for a change
    private static final int GEOMETRY_POLL_MS = 1000;

    private final LightSheetManager model_;
    private final Spinner spnRange_;
    private final Spinner spnStep_;
    private final ComboBox<String> cmbMeaning_;
    private final JLabel lblSlices_;
    private final Timer geometryPoll_;
    private Geometry applied_;
    private boolean refreshing_;

    /** What the slices depend on besides the two fields. */
    private static final class Geometry {
        final double alphaDeg;
        final int heightPx;
        final double pixelUm;
        // the value typed is the effective range: the scan adds 2g
        final boolean effective;

        Geometry(final double alphaDeg, final int heightPx, final double pixelUm,
                 final boolean effective) {
            this.alphaDeg = alphaDeg;
            this.heightPx = heightPx;
            this.pixelUm = pixelUm;
            this.effective = effective;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Geometry)) {
                return false;
            }
            final Geometry g = (Geometry) o;
            return alphaDeg == g.alphaDeg && heightPx == g.heightPx && pixelUm == g.pixelUm
                    && effective == g.effective;
        }

        @Override
        public int hashCode() {
            return Objects.hash(alphaDeg, heightPx, pixelUm, effective);
        }

        /** g; NaN when there is no cropped height or pixel size to compute it from. */
        double gUm() {
            return heightPx > 0 && pixelUm > 0
                    ? VolumeRange.extraPerEndUm(heightPx, pixelUm, alphaDeg) : Double.NaN;
        }

        /** What the scan adds to the value typed: 2g for the effective range, else 0. */
        double addedUm() {
            return effective ? 2 * gUm() : 0.0;
        }
    }

    public VolumeRangePanel(final LightSheetManager model) {
        super("Volume");
        model_ = Objects.requireNonNull(model);
        final ScapeAcquisitionSettings settings = model_.acquisitions().settings();
        spnStep_ = Spinner.createDoubleSpinner(
                settings.volume().sliceStepSize(), 0.01, 100.0, 0.1, 6);
        spnRange_ = Spinner.createDoubleSpinner(0.0, 0.0, 100000.0, 1.0, 6);
        cmbMeaning_ = new ComboBox<>(new String[] {EFFECTIVE, ACTUAL},
                model_.pluginSettings().isVolumeFullCoverage() ? EFFECTIVE : ACTUAL, 125, 22);
        lblSlices_ = new JLabel();
        spnRange_.setToolTipText("Length along the scan, centred on the Imaging Center.");
        spnStep_.setToolTipText("Distance between sheets, perpendicular to them (LSM's slice "
                + "step); along the scan it is step / sin(90° - angle).");
        cmbMeaning_.setToolTipText("Effective range: the region every image row covers; the "
                + "scan adds g = half the cropped height x pixel size x sin(angle) at both ends. "
                + "Actual range: the scan itself, 2g included.");

        add(cmbMeaning_, "");
        add(spnRange_, "split 2");
        add(new JLabel("µm"), "wrap");
        add(new JLabel("Step [µm]:"), "");
        add(spnStep_, "wrap");
        add(lblSlices_, "span 2, w 250!");

        applied_ = geometry();
        showRangeFor(settings.volume().slicesPerView(), settings.volume().sliceStepSize(),
                applied_);

        spnRange_.registerListener(this::apply);
        spnStep_.registerListener(this::apply);
        cmbMeaning_.registerListener(() -> {
            model_.pluginSettings().setVolumeFullCoverage(EFFECTIVE.equals(cmbMeaning_.getSelected()));
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
        return new Geometry(alpha, height, pixel, EFFECTIVE.equals(cmbMeaning_.getSelected()));
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
        final double added = geometry.addedUm();
        if (Double.isNaN(added)) {
            showProblem("The effective range needs a cropped height and a pixel size for this "
                    + "camera and binning: slices not changed.");
            return;
        }
        final int slices = VolumeRange.slicesFor(spnRange_.getDouble() + added, lateral);
        model_.acquisitions().settingsBuilder().volumeBuilder()
                .sliceStepSize(step)
                .slicesPerView(slices);
        model_.acquisitions().updateDurationLabels();
        showSlices(slices, lateral, geometry);
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
        final double added = Double.isNaN(geometry.addedUm()) ? 0.0 : geometry.addedUm();
        refreshing_ = true;
        spnStep_.setDouble(step);
        if (VolumeRange.slicesFor(spnRange_.getDouble() + added, lateral) != slices) {
            spnRange_.setDouble(Math.round(Math.max(0.0,
                    VolumeRange.rangeFor(slices, lateral) - added) * 100.0) / 100.0);
        }
        refreshing_ = false;
        showSlices(slices, lateral, geometry);
    }

    /** Both ranges the slices give, whichever one is typed: the actual one is the scan. */
    private void showSlices(final int slices, final double lateral, final Geometry geometry) {
        final double actual = VolumeRange.rangeFor(slices, lateral);
        final double g = geometry.gUm();
        final String effective = Double.isNaN(g) ? "effective: no crop or pixel size"
                : actual - 2 * g > 0 ? String.format("effective %.1f µm", actual - 2 * g)
                : "no effective range";
        lblSlices_.setText(String.format("<html>%d slices: actual %.1f µm, %s<br>"
                        + "%sangle %.1f° (Settings tab), %.3f µm along the scan</html>",
                slices, actual, effective,
                Double.isNaN(g) ? "" : String.format("g %.1f µm, ", g),
                geometry.alphaDeg, lateral));
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

package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.util.Objects;
import javax.swing.JLabel;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;

/**
 * The volume as a range and a step in µm; LSM's slices per volume follow from them
 * ({@link VolumeRange}). The Acquisition tab shows the same volume as slices and step.
 */
public class VolumeRangePanel extends Panel implements SettingsListener {

    private final LightSheetManager model_;
    private final Spinner spnRange_;
    private final Spinner spnStep_;
    private final JLabel lblSlices_;
    private boolean refreshing_;

    public VolumeRangePanel(final LightSheetManager model) {
        super("Volume");
        model_ = Objects.requireNonNull(model);
        final ScapeAcquisitionSettings settings = model_.acquisitions().settings();
        final double step = settings.volume().sliceStepSize();
        spnStep_ = Spinner.createDoubleSpinner(step, 0.01, 100.0, 0.1, 6);
        spnRange_ = Spinner.createDoubleSpinner(
                VolumeRange.rangeFor(settings.volume().slicesPerView(), step),
                0.0, 100000.0, 1.0, 6);
        lblSlices_ = new JLabel();
        spnRange_.setToolTipText("Depth of the volume, from the first slice to the last.");
        spnStep_.setToolTipText("Distance between slices.");

        add(new JLabel("Range [µm]:"), "");
        add(spnRange_, "wrap");
        add(new JLabel("Step [µm]:"), "");
        add(spnStep_, "wrap");
        add(lblSlices_, "span 2");
        showSlices(settings.volume().slicesPerView(), step);

        spnRange_.registerListener(this::apply);
        spnStep_.registerListener(this::apply);
        model_.userSettings().addChangeListener(this);
    }

    private void apply() {
        if (refreshing_) {
            return;
        }
        if (model_.acquisitions().isRunning()) {
            return; // the tab is locked; a late event changes nothing
        }
        final double step = spnStep_.getDouble();
        final int slices = VolumeRange.slicesFor(spnRange_.getDouble(), step);
        model_.acquisitions().settingsBuilder().volumeBuilder()
                .sliceStepSize(step)
                .slicesPerView(slices);
        model_.acquisitions().updateDurationLabels();
        showSlices(slices, step);
    }

    private void showSlices(final int slices, final double step) {
        lblSlices_.setText(slices + " slices, " + String.format("%.2f", VolumeRange.rangeFor(slices, step))
                + " µm");
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (!(settings instanceof ScapeAcquisitionSettings)) {
            return;
        }
        final ScapeAcquisitionSettings scape = (ScapeAcquisitionSettings) settings;
        final int slices = scape.volume().slicesPerView();
        final double step = scape.volume().sliceStepSize();
        refreshing_ = true;
        spnStep_.setDouble(step);
        // keep the range typed if it gives the same slices; otherwise show the slices' range
        if (VolumeRange.slicesFor(spnRange_.getDouble(), step) != slices) {
            spnRange_.setDouble(VolumeRange.rangeFor(slices, step));
        }
        refreshing_ = false;
        showSlices(slices, step);
    }
}

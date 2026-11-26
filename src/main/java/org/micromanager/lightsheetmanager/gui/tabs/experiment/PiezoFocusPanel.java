package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.model.ImagingCenter;
import org.micromanager.lightsheetmanager.model.autofocus.FocusSearch;
import org.micromanager.lightsheetmanager.model.autofocus.FocusSetup;
import org.micromanager.lightsheetmanager.model.autofocus.TestAutofocus;
import org.micromanager.lightsheetmanager.model.devices.DeviceBase;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

/**
 * The O3 piezo and its autofocus (scapecontrol ADR-0037): the piezo's position, the reference set
 * with Update Offset and the distance from it, the search, and "Test autofocus", which finds the
 * correction scapecontrol's autofocus would and changes nothing. Autofocus never moves the
 * reference; scapecontrol applies a correction per position, for that position's run only.
 */
public class PiezoFocusPanel extends Panel {

    private static final String AS_NOW = "(as now)";

    private final LightSheetManager model_;
    private final Consumer<Boolean> busy_;
    private final JLabel lblPiezo_ = new JLabel("-");
    private final JLabel lblReference_ = new JLabel("-");
    private final JLabel lblFromReference_ = new JLabel("-");
    private final JLabel lblResult_ = new JLabel(" ");
    private final Spinner spnRange_;
    private final Spinner spnCoarse_;
    private final Spinner spnFine_;
    private final ComboBox<String> cmbMeasure_;
    private final ComboBox<String> cmbCamera_;
    private final ComboBox<String> cmbChannel_;
    private final Spinner spnExposure_;
    private final Button btnTest_;
    private final Timer poll_;
    private volatile boolean testing_;
    private volatile boolean cancel_;

    /**
     * @param busy told true while the test runs and false after, so the tab locks its other
     *     controls meanwhile
     */
    public PiezoFocusPanel(final LightSheetManager model, final Consumer<Boolean> busy) {
        super("Piezo and Autofocus");
        model_ = Objects.requireNonNull(model);
        busy_ = Objects.requireNonNull(busy);
        final FocusSetup setup = model_.pluginSettings().focusSetup();

        final Button btnSetReference = new Button("Set reference (Update Offset)", 220, 26);
        btnSetReference.setToolTipText("What the Setup Path tab's Update Offset does: the imaging "
                + "center and the piezo offset from the current piezo and slice positions. Kept "
                + "as the autofocus reference, which autofocus never moves. The sheet must be on.");
        spnRange_ = Spinner.createDoubleSpinner(setup.rangeUm(), 0.05, 20.0, 0.05, 5);
        spnCoarse_ = Spinner.createDoubleSpinner(setup.coarseUm(), 0.01, 5.0, 0.05, 5);
        spnFine_ = Spinner.createDoubleSpinner(setup.fineUm(), 0.005, 1.0, 0.01, 5);
        spnRange_.setToolTipText("Autofocus searches the reference ± this, never further.");
        cmbMeasure_ = new ComboBox<>(FocusSetup.MEASURES.toArray(new String[0]),
                setup.measure(), 100, 22);
        cmbMeasure_.setToolTipText("tenengrad: edge strength, for any sample; "
                + "dcts: for dense samples, it misranks sparse beads.");
        cmbCamera_ = new ComboBox<>(cameraChoices(), orAsNow(setup.camera()), 150, 22);
        cmbChannel_ = new ComboBox<>(channelChoices(), orAsNow(setup.channelPreset()), 150, 22);
        spnExposure_ = Spinner.createDoubleSpinner(setup.exposureMs(), 0.01, 10000.0, 1.0, 5);
        btnTest_ = new Button("Test autofocus", 140, 26);
        btnTest_.setToolTipText("Search as scapecontrol will and show the correction; the piezo "
                + "goes back, nothing is saved or changed.");

        add(new JLabel("Piezo:"), "");
        add(lblPiezo_, "wrap");
        add(new JLabel("Reference:"), "");
        add(lblReference_, "wrap");
        add(new JLabel("From reference:"), "");
        add(lblFromReference_, "wrap");
        add(btnSetReference, "span 2, wrap");
        add(new JLabel("Search ± [µm]:"), "");
        add(spnRange_, "wrap");
        add(new JLabel("Coarse / fine step [µm]:"), "");
        add(spnCoarse_, "split 2");
        add(spnFine_, "wrap");
        add(new JLabel("Measure:"), "");
        add(cmbMeasure_, "wrap");
        add(new JLabel("Camera:"), "");
        add(cmbCamera_, "wrap");
        add(new JLabel("Channel:"), "");
        add(cmbChannel_, "wrap");
        add(new JLabel("Exposure [ms]:"), "");
        add(spnExposure_, "wrap");
        add(btnTest_, "span 2, wrap");
        add(lblResult_, "span 2");

        btnSetReference.registerListener(this::setReference);
        final Runnable saveSearch = this::saveSearch;
        spnRange_.registerListener(saveSearch);
        spnCoarse_.registerListener(saveSearch);
        spnFine_.registerListener(saveSearch);
        cmbMeasure_.registerListener(saveSearch);
        final Runnable saveImaging = () -> {
            try {
                setup.setImaging(fromAsNow(cmbCamera_.getSelected()),
                        fromAsNow(cmbChannel_.getSelected()), spnExposure_.getDouble());
            } catch (IllegalArgumentException e) {
                lblResult_.setText("Not saved: " + e.getMessage());
            }
        };
        cmbCamera_.registerListener(saveImaging);
        cmbChannel_.registerListener(saveImaging);
        spnExposure_.registerListener(saveImaging);
        btnTest_.registerListener(this::runTest);
        poll_ = new Timer(500, e -> showPositions());
        showPositions();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        poll_.start();
    }

    @Override
    public void removeNotify() {
        poll_.stop();
        super.removeNotify();
    }

    private void showPositions() {
        final FocusSetup setup = model_.pluginSettings().focusSetup();
        lblReference_.setText(setup.hasReference()
                ? String.format("%.3f µm (set %s)", setup.referenceImagingCenterUm(),
                        setup.referenceSetAt())
                : "not set");
        // no serial traffic while LSM runs or the test drives the piezo
        if (testing_ || model_.acquisitions().isRunning() || !isShowing()) {
            return;
        }
        final DeviceBase piezo = model_.devices().device("ImagingFocus");
        if (piezo == null) {
            lblPiezo_.setText("no ImagingFocus device");
            return;
        }
        final double z;
        try {
            z = model_.studio().core().getPosition(piezo.getDeviceName());
        } catch (Exception e) {
            lblPiezo_.setText("cannot read");
            return;
        }
        lblPiezo_.setText(String.format("%.3f µm", z));
        if (setup.hasReference()) {
            final double d = z - setup.referenceImagingCenterUm();
            final boolean outside = Math.abs(d) > setup.rangeUm();
            lblFromReference_.setText(String.format("%+.3f µm%s", d,
                    outside ? " (outside ± " + setup.rangeUm() + ")" : ""));
        } else {
            lblFromReference_.setText("-");
        }
    }

    private void setReference() {
        if (model_.acquisitions().isRunning()) {
            model_.studio().logs().showError("The reference cannot be set during an acquisition.");
            return;
        }
        try {
            final double offset = ImagingCenter.updateFromCurrentPosition(model_);
            final double center = model_.acquisitions().settingsBuilder().build()
                    .sheetCalibration().imagingCenter();
            model_.pluginSettings().focusSetup().setReference(center, offset,
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            model_.userSettings().notifyListeners(model_.acquisitions().settingsBuilder().build());
            showPositions();
        } catch (ImagingCenter.SheetOff e) {
            model_.studio().logs().showError("Turn the sheet on to set the reference.");
        } catch (Exception e) {
            model_.studio().logs().showError(e, "The reference was not set.");
        }
    }

    private void saveSearch() {
        try {
            model_.pluginSettings().focusSetup().setSearch(spnRange_.getDouble(),
                    spnCoarse_.getDouble(), spnFine_.getDouble(), cmbMeasure_.getSelected());
            lblResult_.setText(" ");
        } catch (IllegalArgumentException e) {
            lblResult_.setText("Not saved: " + e.getMessage());
        }
    }

    /** Stops a running test after the current frame; the piezo and the rest are put back. */
    public void cancel() {
        cancel_ = true;
    }

    private void runTest() {
        cancel_ = false;
        testing_ = true;
        busy_.accept(true);
        lblResult_.setText("Searching...");
        final FocusSetup setup = model_.pluginSettings().focusSetup();
        final Thread worker = new Thread(() -> {
            String text;
            try {
                final FocusSearch.Result r = TestAutofocus.run(model_, setup,
                        line -> SwingUtilities.invokeLater(() -> lblResult_.setText(line)),
                        () -> cancel_);
                text = r.ok
                        ? String.format("Focus at %+.3f µm from the reference (%d frames). "
                                + "Nothing was changed.", r.deltaUm, r.samples.size())
                        : "Not found: " + r.reason + " (" + r.samples.size() + " frames).";
            } catch (Exception e) {
                text = "Not run: " + e.getMessage();
            }
            final String result = text;
            SwingUtilities.invokeLater(() -> {
                lblResult_.setText(result);
                testing_ = false;
                busy_.accept(false);
            });
        }, "Test autofocus");
        // not a daemon: Micro-Manager closing waits for the test to put the piezo back
        worker.start();
    }

    private String[] cameraChoices() {
        final List<String> names = new ArrayList<>();
        names.add(AS_NOW);
        for (final String role : model_.devices().imagingCameraNames()) {
            model_.devices().device2(role, CameraBase.class)
                    .ifPresent(c -> names.add(c.getDeviceName()));
        }
        return names.toArray(new String[0]);
    }

    private String[] channelChoices() {
        final List<String> names = new ArrayList<>();
        names.add(AS_NOW);
        final String group = model_.acquisitions().settings().channels().group();
        if (!group.isEmpty()) {
            for (final String preset : model_.studio().core().getAvailableConfigs(group)) {
                names.add(preset);
            }
        }
        return names.toArray(new String[0]);
    }

    private static String orAsNow(final String value) {
        return value.isEmpty() ? AS_NOW : value;
    }

    private static String fromAsNow(final String value) {
        return value == null || AS_NOW.equals(value) ? "" : value;
    }
}

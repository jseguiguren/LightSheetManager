package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
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

    private static final String AS_NOW = "(current settings)";

    private final LightSheetManager model_;
    private final Consumer<Boolean> busy_;
    private final JLabel lblPiezo_ = new JLabel("-");
    private final JLabel lblReference_ = new JLabel("-");
    private final JLabel lblResult_ = new JLabel(" ");
    private final Spinner spnRange_;
    private final CheckBox cbxExtended_;
    private final Spinner spnCoarse_;
    private final Spinner spnFine_;
    private final ComboBox<String> cmbMeasure_;
    private final ComboBox<String> cmbCamera_;
    private final ComboBox<String> cmbChannel_;
    private final Spinner spnExposure_;
    private final Button btnTest_;
    private final Button btnGoToFocus_;
    // the last focus a test found, in µm, for the reference it was found from; null for none
    private Double lastFocusUm_;
    private double lastReferenceUm_;
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
        // the limit is the default whenever LSM opens: Extended range off, a saved range clamped
        spnRange_ = Spinner.createDoubleSpinner(
                Math.min(setup.rangeUm(), FocusSearch.MAX_RANGE_UM), 0.05,
                FocusSearch.MAX_RANGE_UM, 0.05, 5);
        cbxExtended_ = new CheckBox("Extended range", false);
        cbxExtended_.setToolTipText("Allow a search beyond ± " + FocusSearch.MAX_RANGE_UM
                + " µm; off whenever Light Sheet Manager opens.");
        spnCoarse_ = Spinner.createDoubleSpinner(setup.coarseUm(), 0.01, 5.0, 0.05, 5);
        spnFine_ = Spinner.createDoubleSpinner(setup.fineUm(), 0.005, 1.0, 0.01, 5);
        spnRange_.setToolTipText("Autofocus searches the reference ± this, never further; at "
                + "most " + FocusSearch.MAX_RANGE_UM + " µm without Extended range.");
        cmbMeasure_ = new ComboBox<>(FocusSetup.MEASURES.toArray(new String[0]),
                setup.measure(), 100, 22);
        cmbMeasure_.setToolTipText("tenengrad: edge strength, for any sample; "
                + "dcts: for dense samples, it misranks sparse beads.");
        cmbCamera_ = new ComboBox<>(cameraChoices(), orAsNow(setup.camera()), 150, 22);
        cmbChannel_ = new ComboBox<>(channelChoices(), orAsNow(setup.channelPreset()), 150, 22);
        spnExposure_ = Spinner.createDoubleSpinner(setup.exposureMs(), 0.01, 10000.0, 1.0, 5);
        btnTest_ = new Button("Test autofocus", 120, 26);
        btnTest_.setToolTipText("Search as scapecontrol will and show the correction; the piezo "
                + "goes back, nothing is saved or changed.");
        btnGoToFocus_ = new Button("Go to new focus", 125, 26);
        btnGoToFocus_.setToolTipText("Move the piezo to the focus the last test found, to look at "
                + "it in Live. The reference stays as it is, and an acquisition still starts "
                + "from the Imaging Center.");
        btnGoToFocus_.setEnabled(false);
        // the end of test -> go to new focus -> update offset; the same as Set reference
        final Button btnUpdateOffset = new Button("Update offset", 110, 26);
        btnUpdateOffset.setToolTipText("Make the piezo's position the new reference, as Update "
                + "Offset does: after Go to new focus, the focus found becomes the reference. The "
                + "sheet must be on.");
        btnUpdateOffset.registerListener(this::setReference);

        add(new JLabel("Piezo:"), "");
        add(lblPiezo_, "wrap");
        add(new JLabel("Reference:"), "");
        add(lblReference_, "wrap");
        add(btnSetReference, "span 2, wrap");
        add(new JLabel("Search ± [µm]:"), "");
        add(spnRange_, "split 2");
        add(cbxExtended_, "wrap");
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
        // two rows: the three in one were wider than the sub-tab
        add(btnTest_, "span 2, wrap");
        add(btnGoToFocus_, "span 2, split 2");
        add(btnUpdateOffset, "wrap");
        add(lblResult_, "span 2, w 320!");

        btnSetReference.registerListener(this::setReference);
        final Runnable saveSearch = this::saveSearch;
        spnRange_.registerListener(saveSearch);
        cbxExtended_.registerListener(() -> {
            final SpinnerNumberModel range = (SpinnerNumberModel) spnRange_.getModel();
            range.setMaximum(cbxExtended_.isSelected() ? 20.0 : FocusSearch.MAX_RANGE_UM);
            if (spnRange_.getDouble() > FocusSearch.MAX_RANGE_UM && !cbxExtended_.isSelected()) {
                spnRange_.setDouble(FocusSearch.MAX_RANGE_UM); // its listener saves
            }
            saveSearch();
        });
        spnCoarse_.registerListener(saveSearch);
        spnFine_.registerListener(saveSearch);
        cmbMeasure_.registerListener(saveSearch);
        final Runnable saveImaging = () -> {
            try {
                setup.setImaging(fromAsNow(cmbCamera_.getSelected()),
                        fromAsNow(cmbChannel_.getSelected()), spnExposure_.getDouble());
            } catch (IllegalArgumentException e) {
                showResult("Not saved: " + e.getMessage());
            }
        };
        cmbCamera_.registerListener(saveImaging);
        cmbChannel_.registerListener(saveImaging);
        spnExposure_.registerListener(saveImaging);
        btnTest_.registerListener(this::runTest);
        saveSearch(); // the saved setup and scapecontrol see the limit as the default too
        btnGoToFocus_.registerListener(this::goToFocus);
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
                        shortTime(setup.referenceSetAt()))
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
        // one row with the distance from the reference: a row less, so the result fits the tab
        if (setup.hasReference()) {
            final double d = z - setup.referenceImagingCenterUm();
            final boolean outside = Math.abs(d) > setup.rangeUm();
            lblPiezo_.setText(String.format("%.3f µm (%+.3f%s)", z, d,
                    outside ? ", outside ± " + setup.rangeUm() : " from reference"));
        } else {
            lblPiezo_.setText(String.format("%.3f µm", z));
        }
    }

    /** Wrapped to the panel's width; a long reason no longer runs out of the window. */
    private void showResult(final String text) {
        lblResult_.setText("<html><body style='width:280px'>"
                + text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")
                + "</body></html>");
    }

    private void forgetFocus() {
        lastFocusUm_ = null;
        btnGoToFocus_.setEnabled(false);
    }

    /**
     * Moves the piezo to the last focus found, after checking it again against the window
     * around the current reference and the piezo's travel. Never changes the reference.
     */
    private void goToFocus() {
        final FocusSetup setup = model_.pluginSettings().focusSetup();
        final Double target = lastFocusUm_;
        if (target == null || model_.acquisitions().isRunning() || testing_) {
            return;
        }
        if (!setup.hasReference() || setup.referenceImagingCenterUm() != lastReferenceUm_
                || Math.abs(target - setup.referenceImagingCenterUm()) > setup.rangeUm() + 1e-9) {
            forgetFocus();
            showResult("Not moved: the reference or the range changed since the test; test again.");
            return;
        }
        final DeviceBase piezo = model_.devices().device("ImagingFocus");
        testing_ = true;
        busy_.accept(true);
        final Thread mover = new Thread(() -> {
            String text;
            try {
                final mmcorej.CMMCore core = model_.studio().core();
                final String name = piezo.getDeviceName();
                final double lower = Double.parseDouble(core.getProperty(name, "LowerLim(mm)")) * 1000.0;
                final double upper = Double.parseDouble(core.getProperty(name, "UpperLim(mm)")) * 1000.0;
                if (!(lower <= target && target <= upper)) {
                    throw new IllegalStateException(target + " µm is outside the piezo's travel");
                }
                core.setPosition(name, target);
                core.waitForDevice(name);
                text = String.format("Piezo at the focus, %+.3f µm from the reference.",
                        target - setup.referenceImagingCenterUm());
            } catch (Exception e) {
                text = "Not moved: " + e.getMessage();
            }
            final String result = text;
            SwingUtilities.invokeLater(() -> {
                showResult(result);
                testing_ = false;
                busy_.accept(false);
                showPositions();
            });
        }, "Go to new focus");
        mover.start();
    }

    /** "26 Nov 15:06", local time: the stored UTC instant ran out of the panel. */
    private static String shortTime(final String instant) {
        try {
            return DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ROOT)
                    .withZone(ZoneId.systemDefault()).format(Instant.parse(instant));
        } catch (RuntimeException e) {
            return instant;
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
            forgetFocus(); // found from the old reference
            // scapecontrol reads the reference from the profile, not from this window
            model_.userSettings().save();
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
                    spnCoarse_.getDouble(), spnFine_.getDouble(), cmbMeasure_.getSelected(),
                    cbxExtended_.isSelected());
            lblResult_.setText(" ");
        } catch (IllegalArgumentException e) {
            showResult("Not saved: " + e.getMessage());
        }
    }

    /** Stops a running test after the current frame; the piezo and the rest are put back. */
    public void cancel() {
        cancel_ = true;
    }

    private void runTest() {
        cancel_ = false;
        forgetFocus();
        testing_ = true;
        busy_.accept(true);
        showResult("Searching...");
        final FocusSetup setup = model_.pluginSettings().focusSetup();
        final Thread worker = new Thread(() -> {
            String text;
            Double focus = null;
            final double reference = setup.referenceImagingCenterUm();
            try {
                final FocusSearch.Result r = TestAutofocus.run(model_, setup,
                        line -> SwingUtilities.invokeLater(() -> showResult(line)),
                        () -> cancel_);
                text = r.ok
                        ? String.format("Focus at %+.3f µm from the reference (%d frames). "
                                + "\nNothing was changed.", r.deltaUm, r.samples.size())
                        : "Not found: " + r.reason + " (" + r.samples.size() + " frames).";
                if (r.ok) {
                    focus = reference + r.deltaUm;
                }
            } catch (Exception e) {
                text = "Not run: " + e.getMessage();
            }
            final String result = text;
            final Double found = focus;
            SwingUtilities.invokeLater(() -> {
                showResult(result);
                if (found != null) {
                    lastFocusUm_ = found;
                    lastReferenceUm_ = reference;
                    btnGoToFocus_.setEnabled(true);
                }
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

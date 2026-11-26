package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.LightSheetManagerFrame;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.ListeningPanel;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.CameraPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.DurationPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.TimePointsPanel;
import org.micromanager.lightsheetmanager.gui.tabs.channels.ChannelTablePanel;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

/**
 * Everything needed to find a sample and choose its imaging settings, on one tab: the cameras,
 * one exposure for every active camera, the volume, channels, time points, positions and the
 * devices of Micro-Manager's configuration groups, with Snap, Live and a test volume. It edits
 * the same acquisition settings as the Acquisition tab, so the two always agree.
 */
public class ExperimentSetupTab extends Panel implements ListeningPanel, SettingsListener {

    private final LightSheetManager model_;
    private final LightSheetManagerFrame frame_;
    // the cameras take the exposure once the spinner has settled, not at every step
    private final Timer exposureCommit_;

    private Spinner spnExposure_;
    private boolean refreshingExposure_;
    private CheckBox cbxUseChannels_;
    private ChannelTablePanel pnlChannels_;
    private CheckBox cbxUseTimePoints_;
    private TimePointsPanel pnlTimePoints_;
    private CheckBox cbxUseMultiplePositions_;
    private PositionsPanel pnlPositions_;
    private DevicesPanel pnlDevices_;
    private PiezoFocusPanel pnlPiezo_;
    private Panel pnlLeft_;
    private Panel pnlCenter_;

    private Button btnSnap_;
    private Button btnLive_;
    private Button btnTestVolume_;
    private Button btnStop_;
    private boolean running_;
    // a Test autofocus drives the hardware without an LSM run: lock as for one
    private boolean busy_;
    // the controls a run disabled, enabled again when it ends; the others stay as they were
    private final List<Component> lockedByRun_ = new ArrayList<>();
    // runs can also start from the Acquisition tab or from a script
    private final Timer runPoll_;

    public ExperimentSetupTab(final LightSheetManager model, final LightSheetManagerFrame frame) {
        model_ = Objects.requireNonNull(model);
        frame_ = Objects.requireNonNull(frame);
        exposureCommit_ = new Timer(400, e -> setCameraExposure(spnExposure_.getDouble()));
        exposureCommit_.setRepeats(false);
        createUserInterface();
        createEventHandlers();
        model_.userSettings().addChangeListener(this);
        runPoll_ = new Timer(200, e -> showRunning(model_.acquisitions().isRunning() || busy_));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        runPoll_.start();
    }

    @Override
    public void removeNotify() {
        // the plugin window closed: stop asking a model that is going away
        runPoll_.stop();
        exposureCommit_.stop();
        super.removeNotify();
    }

    private void createUserInterface() {
        final ScapeAcquisitionSettings settings = model_.acquisitions().settings();
        setMigLayout("", "[]10[]10[]", "[]10[]");

        pnlLeft_ = new Panel();
        pnlLeft_.setMigLayout("insets 0", "[]", "[]5[]");
        pnlCenter_ = new Panel();
        pnlCenter_.setMigLayout("insets 0", "[]", "[]5[]");
        final Panel pnlRight = new Panel();
        pnlRight.setMigLayout("insets 0", "[]", "[]5[]");

        final Panel pnlExposure = new Panel("Exposure");
        spnExposure_ = Spinner.createDoubleSpinner(
                settings.slice().sampleExposure(), 0.01, 10000.0, 0.25, 6);
        spnExposure_.setToolTipText("One exposure for every active imaging camera: the "
                + "acquisition's sample exposure, and the cameras' exposure for Snap and Live.");
        pnlExposure.add(new JLabel("Exposure [ms]:"), "");
        pnlExposure.add(spnExposure_, "");

        pnlLeft_.add(new CameraPanel(model_), "growx, wrap");
        pnlLeft_.add(pnlExposure, "growx, wrap");
        pnlLeft_.add(new VolumeRangePanel(model_), "growx, wrap");
        pnlLeft_.add(new DurationPanel(model_), "growx");

        cbxUseChannels_ = new CheckBox("Channels", settings.channels().enabled());
        pnlChannels_ = new ChannelTablePanel(model_, cbxUseChannels_);
        pnlChannels_.setItemsEnabled(settings.channels().enabled());
        pnlChannels_.setAbsoluteSize(280, 300);
        cbxUseTimePoints_ = new CheckBox("Time Points", settings.isUsingTimePoints());
        pnlTimePoints_ = new TimePointsPanel(model_, cbxUseTimePoints_);
        pnlTimePoints_.setPanelEnabled(settings.isUsingTimePoints());
        cbxUseMultiplePositions_ = new CheckBox(
                "Use multiple positions", settings.isUsingMultiplePositions());
        cbxUseMultiplePositions_.setToolTipText("Acquire at every position of the Stage "
                + "Position List.");
        pnlPositions_ = new PositionsPanel(model_);

        pnlCenter_.add(pnlChannels_, "wrap");
        pnlCenter_.add(pnlTimePoints_, "growx, wrap");
        pnlCenter_.add(cbxUseMultiplePositions_, "wrap");
        pnlCenter_.add(pnlPositions_, "growx");

        pnlDevices_ = new DevicesPanel(model_);
        final JScrollPane scrDevices = new JScrollPane(pnlDevices_);
        scrDevices.setBorder(null);
        scrDevices.getVerticalScrollBar().setUnitIncrement(16);

        final Panel pnlButtons = new Panel();
        btnSnap_ = new Button("Snap", 90, 30);
        btnLive_ = new Button("Live", 90, 30);
        btnTestVolume_ = new Button("Test volume", 110, 30);
        btnStop_ = new Button("Stop", 90, 30);
        btnSnap_.setToolTipText("Snap the active cameras, flipped as set on the Cameras tab.");
        btnLive_.setToolTipText("Start or stop Live, flipped as set on the Cameras tab.");
        btnTestVolume_.setToolTipText("Acquire one volume with these settings, without saving "
                + "it, flipped as set on the Cameras tab.");
        btnStop_.setToolTipText("Stop the acquisition or the Test autofocus in progress.");
        pnlButtons.add(btnSnap_, "");
        pnlButtons.add(btnLive_, "");
        pnlButtons.add(btnTestVolume_, "");
        pnlButtons.add(btnStop_, "");

        pnlPiezo_ = new PiezoFocusPanel(model_, busy -> {
            busy_ = busy;
            frame_.setTabsEnabled(!busy); // no run from another tab during the test
            showRunning(model_.acquisitions().isRunning() || busy);
        });
        // two sub-tabs share the right column: stacked, they did not fit the window
        final JTabbedPane tabsRight = new JTabbedPane();
        tabsRight.addTab("Piezo / Autofocus", pnlPiezo_);
        tabsRight.addTab("Devices", scrDevices);
        pnlRight.add(tabsRight, "w 360!, h 470!");

        add(pnlLeft_, "aligny top");
        add(pnlCenter_, "aligny top");
        add(pnlRight, "aligny top, wrap");
        // under all three columns, as on the Acquisition tab
        add(pnlButtons, "span 3, center");
        btnStop_.setEnabled(false);
        showRunning(model_.acquisitions().isRunning());
    }

    private void createEventHandlers() {
        spnExposure_.registerListener(() -> {
            if (!refreshingExposure_) {
                setSampleExposure(spnExposure_.getDouble());
            }
        });
        cbxUseChannels_.registerListener(() -> {
            final boolean selected = cbxUseChannels_.isSelected();
            model_.acquisitions().settingsBuilder().channelBuilder().enabled(selected);
            model_.acquisitions().updateDurationLabels();
            pnlChannels_.setItemsEnabled(selected);
        });
        cbxUseTimePoints_.registerListener(() -> {
            final boolean selected = cbxUseTimePoints_.isSelected();
            model_.acquisitions().settingsBuilder().useTimePoints(selected);
            model_.acquisitions().updateDurationLabels();
            pnlTimePoints_.setPanelEnabled(selected);
        });
        cbxUseMultiplePositions_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .useMultiplePositions(cbxUseMultiplePositions_.isSelected());
            model_.acquisitions().updateDurationLabels();
        });
        btnSnap_.registerListener(() -> model_.studio().live().snap(true));
        btnLive_.registerListener(() -> model_.studio().live().setLiveModeOn(
                !model_.studio().live().isLiveModeOn()));
        btnTestVolume_.registerListener(this::runTestVolume);
        btnStop_.registerListener(() -> {
            if (busy_) {
                pnlPiezo_.cancel(); // a Test autofocus: it puts everything back as it stops
            }
            if (model_.acquisitions().isRunning()) {
                model_.acquisitions().requestStop();
            }
        });
    }

    /**
     * Sets the acquisition's sample exposure, then, once the spinner settles, every active
     * imaging camera's, so Live shows what a run records. Refused during a run, when LSM owns
     * both.
     */
    private void setSampleExposure(final double exposureMs) {
        if (model_.acquisitions().isRunning()) {
            showSettings();
            return;
        }
        model_.acquisitions().settingsBuilder().sliceBuilder().sampleExposure(exposureMs);
        model_.acquisitions().updateDurationLabels();
        exposureCommit_.restart();
    }

    private void setCameraExposure(final double exposureMs) {
        if (model_.acquisitions().isRunning()) {
            return;
        }
        final Studio studio = model_.studio();
        // Studio's way to change the exposure under Live: suspend, change, resume
        studio.live().setSuspended(true);
        try {
            for (final CameraBase camera : model_.devices().activeImagingCameras().values()) {
                camera.setExposure(exposureMs);
            }
        } finally {
            studio.live().setSuspended(false);
        }
        studio.app().refreshGUIFromCache();
    }

    private void runTestVolume() {
        if (model_.studio().live().isLiveModeOn()) {
            model_.studio().live().setLiveModeOn(false);
        }
        if (model_.acquisitions().isRunning()) {
            return; // the lock has not caught up with a run that just started
        }
        // the cameras take a pending exposure before the run, not during it
        if (exposureCommit_.isRunning()) {
            exposureCommit_.stop();
            setCameraExposure(spnExposure_.getDouble());
        }
        // as the Acquisition tab's buttons do: no second run from another tab meanwhile
        frame_.setTabsEnabled(false);
        showRunning(true);
        final Future<?> finished = model_.acquisitions().requestTestAcquisition();
        final Thread waiter = new Thread(() -> {
            try {
                finished.get();
            } catch (Exception e) {
                model_.logging().reportError(e, "The test volume ended with an error");
            }
            SwingUtilities.invokeLater(() -> {
                frame_.setTabsEnabled(true);
                showRunning(model_.acquisitions().isRunning() || busy_);
            });
        }, "Experiment Setup test volume");
        waiter.setDaemon(true);
        waiter.start();
    }

    /** Locks every control but Stop while a run is in progress. */
    private void showRunning(final boolean running) {
        if (running == running_) {
            return;
        }
        running_ = running;
        if (running) {
            lockEnabled(this);
        } else {
            lockedByRun_.forEach(c -> c.setEnabled(true));
            lockedByRun_.clear();
        }
        btnStop_.setEnabled(running);
    }

    private void lockEnabled(final Container container) {
        for (final Component c : container.getComponents()) {
            if (c != btnStop_ && c.isEnabled()) {
                c.setEnabled(false);
                lockedByRun_.add(c);
            }
            if (c instanceof Container) {
                lockEnabled((Container) c);
            }
        }
    }

    /** Shows the acquisition settings as they are, undoing an edit that was refused. */
    private void showSettings() {
        model_.userSettings().notifyListeners(model_.acquisitions().settingsBuilder().build());
    }

    /** The settings the Acquisition tab edited, shown here, and the other way round. */
    private void shareSettings() {
        // not during a run: updateSettings would publish the builder over the run's settings
        if (model_.acquisitions().isRunning()) {
            return;
        }
        model_.acquisitions().updateSettings();
        model_.userSettings().notifyListeners(model_.acquisitions().settings());
    }

    @Override
    public void selected() {
        shareSettings();
        pnlPositions_.refresh();
        if (!model_.acquisitions().isRunning()) {
            pnlDevices_.refresh();
        }
    }

    @Override
    public void unselected() {
        shareSettings();
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (!(settings instanceof ScapeAcquisitionSettings)) {
            return;
        }
        final ScapeAcquisitionSettings scape = (ScapeAcquisitionSettings) settings;
        refreshingExposure_ = true;
        spnExposure_.setDouble(scape.slice().sampleExposure());
        refreshingExposure_ = false;
        cbxUseChannels_.setSelected(scape.channels().enabled(), false);
        cbxUseTimePoints_.setSelected(scape.isUsingTimePoints(), false);
        cbxUseMultiplePositions_.setSelected(scape.isUsingMultiplePositions(), false);
        if (!running_) {
            pnlChannels_.setItemsEnabled(scape.channels().enabled());
            pnlTimePoints_.setPanelEnabled(scape.isUsingTimePoints());
        }
    }
}

package org.micromanager.lightsheetmanager.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Objects;
import org.micromanager.lightsheetmanager.liveview.LiveViewOrientation;
import org.micromanager.lightsheetmanager.model.autofocus.FocusSetup;

/**
 * Plugin settings that are not a part of the acquisition settings.
 */
public class PluginSettings {

    private boolean isPollingPositions = true;

    // volatile: written from the EDT (Settings tab) and read from the acquisition thread
    private volatile boolean acquireFailQuietly = false;

    private final JoystickData joystick = new JoystickData();

    private final XYZGrid xyzGrid = new XYZGrid();

    // volatile: replaced from the EDT (Camera tab), read when the Live pipeline is updated
    private volatile LiveViewOrientation liveViewOrientation = LiveViewOrientation.none();

    // the O3 autofocus reference and search (scapecontrol ADR-0037)
    private FocusSetup focusSetup = new FocusSetup();

    // Experiment Setup's Volume: the value typed is the effective range (scan = it + 2g), not the
    // actual range (the scan itself)
    private volatile boolean volumeFullCoverage = false;

    public JoystickData joystickPanel() {
        return joystick;
    }

    public XYZGrid xyzGrid() {
        return xyzGrid;
    }

    public void setPollingPositions(final boolean state) {
        isPollingPositions = state;
    }

    public boolean isPollingPositions() {
        return isPollingPositions;
    }

    public void setAcquireFailQuietly(final boolean state) {
        acquireFailQuietly = state;
    }

    public boolean isAcquireFailQuietly() {
        return acquireFailQuietly;
    }

    public LiveViewOrientation liveViewOrientation() {
        // null only from settings JSON that holds an explicit null
        return liveViewOrientation != null ? liveViewOrientation : LiveViewOrientation.none();
    }

    public void liveViewOrientation(final LiveViewOrientation orientation) {
        liveViewOrientation = Objects.requireNonNull(orientation);
    }

    public FocusSetup focusSetup() {
        if (focusSetup == null) { // settings JSON that holds an explicit null
            focusSetup = new FocusSetup();
        }
        return focusSetup;
    }

    public boolean isVolumeFullCoverage() {
        return volumeFullCoverage;
    }

    public void setVolumeFullCoverage(final boolean state) {
        volumeFullCoverage = state;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toPrettyJson() {
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public static PluginSettings fromJson(final String json) {
        return new Gson().fromJson(json, PluginSettings.class);
    }

    public static class JoystickData {

        private String joystick;
        private String leftWheel;
        private String rightWheel;

        JoystickData() {
            joystick = "None";
            leftWheel = "None";
            rightWheel = "None";
        }

        public String joystick() {
            return joystick;
        }

        public String leftWheel() {
            return leftWheel;
        }

        public String rightWheel() {
            return rightWheel;
        }

        public void joystick(final String joystick) {
            this.joystick = joystick;
        }

        public void leftWheel(final String leftWheel) {
            this.leftWheel = leftWheel;
        }

        public void rightWheel(final String rightWheel) {
            this.rightWheel = rightWheel;
        }
    }
}

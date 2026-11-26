package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.util.List;

/** How the Experiment Setup tab shows a Micro-Manager configuration group. */
public enum ConfigGroupKind {
    /** Named presets: a dropdown (filters, channels). */
    PRESETS,
    /** One preset holding one property, which Micro-Manager's own window shows as a field
     * (a laser's Voltage, an LED's intensity). */
    PROPERTY,
    /** Not shown, left to Micro-Manager's window: raw serial commands, the Core camera and focus,
     * the laser channel alone, and an empty group. */
    HIDDEN;

    /**
     * @param presets the group's preset names
     * @param settingsInFirstPreset how many properties the first preset sets
     * @param settings every {device, property} any preset sets, the first preset's first
     */
    public static ConfigGroupKind of(final List<String> presets, final int settingsInFirstPreset,
                                     final List<String[]> settings) {
        if (presets.isEmpty()) {
            return HIDDEN;
        }
        boolean onlyOutputChannel = !settings.isEmpty();
        for (final String[] s : settings) {
            // SerialCommand / SerialResponse send raw controller commands
            if (s[1].startsWith("Serial")) {
                return HIDDEN;
            }
            // the Core camera or focus: changed by mistake they redirect Live and the acquisition
            if ("Core".equals(s[0]) && ("Camera".equals(s[1]) || "Focus".equals(s[1]))) {
                return HIDDEN;
            }
            onlyOutputChannel &= "OutputChannel".equals(s[1]);
        }
        // the laser channel alone: LSM sets it per channel during a run
        if (onlyOutputChannel) {
            return HIDDEN;
        }
        if (presets.size() == 1 && settingsInFirstPreset == 1) {
            return PROPERTY;
        }
        return PRESETS;
    }
}

package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.util.List;

/** How the Experiment Setup tab shows a Micro-Manager configuration group. */
public enum ConfigGroupKind {
    /** Named presets: a dropdown (filters, channels). */
    PRESETS,
    /** One preset holding one property, which Micro-Manager's own window shows as a field
     * (a laser's Voltage, an LED's intensity). */
    PROPERTY,
    /** Not shown: raw serial access stays in Micro-Manager's window, and an empty group has
     * nothing to set. */
    HIDDEN;

    /**
     * @param presets the group's preset names
     * @param settingsInFirstPreset how many properties the first preset sets
     * @param propertyNames every property any preset sets, the first preset's first
     */
    public static ConfigGroupKind of(final List<String> presets, final int settingsInFirstPreset,
                                     final List<String> propertyNames) {
        if (presets.isEmpty()) {
            return HIDDEN;
        }
        // SerialCommand / SerialResponse send raw controller commands
        if (propertyNames.stream().anyMatch(name -> name.startsWith("Serial"))) {
            return HIDDEN;
        }
        if (presets.size() == 1 && settingsInFirstPreset == 1) {
            return PROPERTY;
        }
        return PRESETS;
    }
}

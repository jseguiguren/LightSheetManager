package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.PropertyType;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.gui.components.TextField;

/**
 * Micro-Manager's configuration groups, as in its main window: a group of named presets is a
 * dropdown, and a group of one property is a field. A numeric property with limits is set in
 * percent of its range, so a laser's 0-5 V input reads 0-100 %.
 *
 * <p>Writes run one at a time off the EDT (a filter move can take seconds) and are refused while
 * an acquisition runs, when LSM owns the channel and the lasers' gating.
 */
public class DevicesPanel extends Panel {

    private final LightSheetManager model_;
    private final CMMCore core_;
    private final List<Runnable> refreshers_ = new ArrayList<>();
    // groups that set the Core camera: switching it under Live needs Live suspended
    private final Set<String> cameraGroups_ = new HashSet<>();
    private final Map<String, List<String>> presets_ = new HashMap<>();
    private final ExecutorService writer_ = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "Experiment Setup devices");
        t.setDaemon(true);
        return t;
    });

    public DevicesPanel(final LightSheetManager model) {
        super("Devices");
        model_ = Objects.requireNonNull(model);
        core_ = model_.studio().core();
        for (final String group : core_.getAvailableConfigGroups()) {
            try {
                addGroup(group);
            } catch (Exception e) {
                model_.studio().logs().logError(e, "Experiment Setup: could not show the "
                        + "configuration group \"" + group + "\".");
            }
        }
    }

    /**
     * Shows the values Micro-Manager last read; its window and scripts can change them. Reads
     * the cache, so it sends nothing to the devices.
     */
    public void refresh() {
        refreshers_.forEach(Runnable::run);
    }

    @Override
    public void removeNotify() {
        writer_.shutdown();
        super.removeNotify();
    }

    /**
     * A second dropdown for the group that switches the Core camera, for the tab's button row,
     * or null if there is none. It writes as the one in this panel does, under suspended Live.
     */
    public ComboBox<String> liveCameraControl() {
        for (final String group : cameraGroups_) {
            return presetCombo(group, presets_.get(group));
        }
        return null;
    }

    private void addGroup(final String group) throws Exception {
        final List<String> presets = new ArrayList<>();
        final List<String> properties = new ArrayList<>();
        for (final String preset : core_.getAvailableConfigs(group)) {
            presets.add(preset);
            final Configuration data = core_.getConfigData(group, preset);
            for (long i = 0; i < data.size(); i++) {
                final PropertySetting s = data.getSetting(i);
                properties.add(s.getPropertyName());
                if ("Core".equals(s.getDeviceLabel()) && "Camera".equals(s.getPropertyName())) {
                    cameraGroups_.add(group);
                }
            }
        }
        final Configuration first = presets.isEmpty()
                ? null : core_.getConfigData(group, presets.get(0));
        final int settings = first == null ? 0 : (int) first.size();
        switch (ConfigGroupKind.of(presets, settings, properties)) {
            case PRESETS:
                addPresets(group, presets);
                break;
            case PROPERTY:
                final PropertySetting setting = first.getSetting(0);
                addProperty(group, setting.getDeviceLabel(), setting.getPropertyName());
                break;
            default:
                break;
        }
    }

    private void addPresets(final String group, final List<String> presets) {
        presets_.put(group, presets);
        addRow(group, presetCombo(group, presets), null);
    }

    private ComboBox<String> presetCombo(final String group, final List<String> presets) {
        final ComboBox<String> cmb = new ComboBox<>(presets.toArray(new String[0]), null, 140, 22);
        final boolean[] refreshing = {false};
        final Runnable show = () -> {
            refreshing[0] = true;
            final String current = currentConfig(group);
            // no matching preset: show none rather than the first one
            if (presets.contains(current)) {
                cmb.setSelectedItem(current);
            } else {
                cmb.setSelectedIndex(-1);
            }
            refreshing[0] = false;
        };
        cmb.registerListener(() -> {
            if (refreshing[0] || cmb.getSelected() == null) {
                return;
            }
            final String preset = cmb.getSelected();
            final boolean camera = cameraGroups_.contains(group);
            write(group + " to " + preset, () -> {
                // a camera switched under a running Live: suspend it, as Studio does
                if (camera) {
                    model_.studio().live().setSuspended(true);
                }
                try {
                    core_.setConfig(group, preset);
                    core_.waitForConfig(group, preset);
                } finally {
                    if (camera) {
                        model_.studio().live().setSuspended(false);
                    }
                }
            }, show);
        });
        refreshers_.add(show);
        show.run();
        return cmb;
    }

    private void addProperty(final String group, final String device, final String property)
            throws Exception {
        final PropertyType type = core_.getPropertyType(device, property);
        final boolean numeric = type == PropertyType.Float || type == PropertyType.Integer;
        if (numeric && core_.hasPropertyLimits(device, property)
                && core_.getPropertyUpperLimit(device, property)
                > core_.getPropertyLowerLimit(device, property)) {
            addPercent(group, device, property, type == PropertyType.Integer);
        } else {
            addText(group, device, property);
        }
    }

    private void addPercent(final String group, final String device, final String property,
                            final boolean integer) throws Exception {
        final PercentScale scale = new PercentScale(
                core_.getPropertyLowerLimit(device, property),
                core_.getPropertyUpperLimit(device, property));
        final Spinner spn = Spinner.createDoubleSpinner(0.0, 0.0, 100.0, 1.0, 5);
        final boolean[] refreshing = {false};
        spn.setToolTipText(group + ": " + device + " " + property + ", in % of its range "
                + format(scale.lower()) + " to " + format(scale.upper()));
        final Runnable show = () -> {
            refreshing[0] = true;
            try {
                final double value = Double.parseDouble(
                        core_.getPropertyFromCache(device, property));
                spn.setDouble(scale.toPercent(value));
            } catch (Exception e) {
                model_.studio().logs().logError(e);
            }
            refreshing[0] = false;
        };
        spn.registerListener(() -> {
            if (refreshing[0]) {
                return;
            }
            final double target = scale.fromPercent(spn.getDouble());
            final String value = integer
                    ? Long.toString(Math.round(target)) : Double.toString(target);
            write(device + " " + property + " to " + value,
                    () -> core_.setProperty(device, property, value), show);
        });
        refreshers_.add(show);
        show.run();
        addRow(group, spn, new JLabel("%"));
    }

    private void addText(final String group, final String device, final String property)
            throws Exception {
        final TextField txt = new TextField(10);
        final boolean readOnly = core_.isPropertyReadOnly(device, property)
                || core_.isPropertyPreInit(device, property);
        txt.setEnabled(!readOnly);
        txt.setToolTipText(group + ": " + device + " " + property
                + (readOnly ? " (read only here)" : "; press Enter to set."));
        final Runnable show = () -> {
            try {
                txt.setText(core_.getPropertyFromCache(device, property));
            } catch (Exception e) {
                model_.studio().logs().logError(e);
            }
        };
        txt.addActionListener(e -> {
            final String value = txt.getText();
            write(device + " " + property + " to " + value,
                    () -> core_.setProperty(device, property, value), show);
        });
        refreshers_.add(show);
        show.run();
        addRow(group, txt, null);
    }

    private interface DeviceWrite {
        void run() throws Exception;
    }

    /**
     * Runs {@code write} off the EDT, unless an acquisition is running, then shows what the
     * device holds. A refused or failed write shows the old value again.
     */
    private void write(final String what, final DeviceWrite write, final Runnable show) {
        if (model_.acquisitions().isRunning()) {
            model_.studio().logs().showError("Not set during an acquisition: " + what + ".");
            show.run();
            return;
        }
        writer_.submit(() -> {
            try {
                write.run();
            } catch (Exception e) {
                model_.studio().logs().showError(e, "Could not set " + what + ".");
            }
            SwingUtilities.invokeLater(() -> {
                show.run();
                model_.studio().app().refreshGUIFromCache();
            });
        });
    }

    private void addRow(final String group, final Component control, final Component after) {
        add(new JLabel(group + ":"), "");
        if (after == null) {
            add(control, "span 2, wrap");
        } else {
            add(control, "");
            add(after, "wrap");
        }
    }

    private String currentConfig(final String group) {
        try {
            return core_.getCurrentConfigFromCache(group);
        } catch (Exception e) {
            return "";
        }
    }

    private static String format(final double v) {
        return String.format("%.3g", v);
    }
}

package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import java.util.Objects;
import javax.swing.JLabel;
import mmcorej.CMMCore;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.TextField;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;
import org.micromanager.lightsheetmanager.model.devices.DeviceBase;

/**
 * Adds the current stage position, under a name, to Micro-Manager's Stage Position List: sample
 * XY, sample Z and the imaging piezo. LSM and scapecontrol both read that list.
 */
public class PositionsPanel extends Panel {

    private final LightSheetManager model_;
    private final TextField txtName_;
    private final JLabel lblCount_;

    public PositionsPanel(final LightSheetManager model) {
        super("Positions");
        model_ = Objects.requireNonNull(model);

        txtName_ = new TextField(14);
        txtName_.setToolTipText("Name of the next position; left empty, Micro-Manager names it.");
        final Button btnAdd = new Button("Add current position", 160, 26);
        btnAdd.setToolTipText("Add the current sample XY, sample Z and imaging piezo positions "
                + "to Micro-Manager's Stage Position List, under the name above.");
        final Button btnEdit = new Button("Edit list...", 100, 26);
        btnEdit.setToolTipText("Open Micro-Manager's Stage Position List.");
        final Button btnClear = new Button("Clear", 70, 26);
        btnClear.setToolTipText("Remove every position from the Stage Position List.");
        lblCount_ = new JLabel();

        add(new JLabel("Name:"), "");
        add(txtName_, "span 2, growx, wrap");
        add(btnAdd, "span 3, wrap");
        add(btnEdit, "");
        add(btnClear, "");
        add(lblCount_, "wrap");

        btnAdd.registerListener(this::addCurrentPosition);
        btnEdit.registerListener(() -> model_.studio().app().showPositionList());
        btnClear.registerListener(this::clear);
        refresh();
    }

    /** Shows how many positions the list holds; the list can change in Micro-Manager's window. */
    public void refresh() {
        final int n = model_.studio().positions().getPositionList().getNumberOfPositions();
        lblCount_.setText(n + (n == 1 ? " position" : " positions"));
    }

    private void addCurrentPosition() {
        final Studio studio = model_.studio();
        if (model_.acquisitions().isRunning()) {
            studio.logs().showError("No position added during an acquisition.");
            return;
        }
        final String xy = deviceName("SampleXY");
        final String z = deviceName("SampleZ");
        final String piezo = deviceName("ImagingFocus");
        if (xy == null || z == null) {
            studio.logs().showError("No position added: assign SampleXY and SampleZ in the "
                    + "LightSheetDeviceManager.");
            return;
        }
        final PositionList list = studio.positions().getPositionList();
        final String label;
        try {
            label = StagePositions.labelFor(txtName_.getText(), list);
        } catch (IllegalArgumentException e) {
            studio.logs().showError(e.getMessage());
            return;
        }
        final CMMCore core = studio.core();
        try {
            list.addPosition(StagePositions.create(label,
                    xy, core.getXPosition(xy), core.getYPosition(xy),
                    z, core.getPosition(z),
                    piezo, piezo == null ? 0.0 : core.getPosition(piezo)));
        } catch (Exception e) {
            studio.logs().showError(e, "No position added: the stage position could not be read.");
            return;
        }
        studio.positions().setPositionList(list);
        txtName_.setText("");
        refresh();
    }

    private void clear() {
        if (model_.acquisitions().isRunning()) {
            model_.studio().logs().showError("The positions cannot be cleared during an acquisition.");
            return;
        }
        if (!DialogUtils.showYesNoDialog(this, "Clear Positions",
                "Remove every position from the Stage Position List?")) {
            return;
        }
        final PositionList list = model_.studio().positions().getPositionList();
        list.clearAllPositions();
        model_.studio().positions().setPositionList(list);
        refresh();
    }

    private String deviceName(final String role) {
        final DeviceBase device = model_.devices().device(role);
        return device == null ? null : device.getDeviceName();
    }
}

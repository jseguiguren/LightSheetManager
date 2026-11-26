package org.micromanager.lightsheetmanager.model;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIPiezo;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIScanner;

/**
 * The Setup Path tab's "Update Offset": the piezo offset and the imaging center from the current
 * piezo and slice positions. One place, for that button and the Experiment Setup tab's
 * "Set reference".
 */
public final class ImagingCenter {

    private ImagingCenter() {
    }

    /** The sheet is off, so the slice position does not say where the sheet is. */
    public static final class SheetOff extends IllegalStateException {
        SheetOff() {
            super("the sheet is off");
        }
    }

    /**
     * Sets the slice calibration's piezo offset and the imaging center to
     * {@code piezo - slope x slice} in the settings builder.
     *
     * @return the new offset in µm
     * @throws SheetOff if the sheet (beam) is off
     * @throws Exception if the piezo cannot be read: read through the Core, which throws, and not
     *     ASIPiezo, which returns 0 on a failed read and would make 0 the offset
     */
    public static double updateFromCurrentPosition(final LightSheetManager model)
            throws Exception {
        final ASIPiezo piezo = model.devices().device("ImagingFocus");
        final ASIScanner scanner = model.devices().device("IllumSlice");
        if (!scanner.isBeamOn()) {
            throw new SheetOff();
        }
        final double rate = model.acquisitions().settingsBuilder().build()
                .sliceCalibration().slope();
        final double piezoPosition = model.studio().core().getPosition(piezo.getDeviceName());
        final double scannerPosition = scanner.getPosition().y;
        final double channelOffset = 0.0;
        // FIXME: update channelOffset
        // was: channelOffset = ASIdiSPIM.getFrame().getAcquisitionPanel().getChannelOffset();
        final double newOffset = piezoPosition - rate * scannerPosition - channelOffset;
        model.acquisitions().settingsBuilder()
                .sheetCalibrationBuilder().imagingCenter(newOffset);
        model.acquisitions().settingsBuilder()
                .sliceCalibrationBuilder().offset(newOffset);
        model.studio().logs().logMessage("updated offset; new value is " + newOffset
                + " (with channel offset of " + channelOffset + ")");
        return newOffset;
    }
}

package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

/** Named positions for Micro-Manager's Stage Position List: sample XY, sample Z and the piezo. */
public final class StagePositions {

    private StagePositions() {
    }

    /**
     * A position at the given stage coordinates, in µm.
     *
     * @param piezo the imaging piezo's device name, or {@code null} for none
     */
    public static MultiStagePosition create(final String label,
                                            final String xyStage, final double x, final double y,
                                            final String zStage, final double z,
                                            final String piezo, final double piezoPosition) {
        final MultiStagePosition position = new MultiStagePosition(xyStage, x, y, zStage, z);
        if (piezo != null) {
            position.add(StagePosition.create1D(piezo, piezoPosition));
        }
        position.setLabel(label);
        return position;
    }

    /**
     * The label for a new position: the name typed, trimmed, or a generated one if none was.
     *
     * @throws IllegalArgumentException if a position of that name is already in the list
     */
    public static String labelFor(final String typed, final PositionList list) {
        final String name = typed == null ? "" : typed.trim();
        if (name.isEmpty()) {
            return list.generateLabel();
        }
        if (!list.isLabelUnique(name)) {
            throw new IllegalArgumentException(
                    "A position named \"" + name + "\" is already in the list.");
        }
        return name;
    }
}

package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

public class ExperimentSetupLogicTest {

    private static final double EPS = 1e-9;

    @Test
    public void percentSpansTheLimits() {
        final PercentScale scale = new PercentScale(0.0, 5.0); // a laser's Voltage, 0 to 5 V
        assertEquals(0.0, scale.toPercent(0.0), EPS);
        assertEquals(50.0, scale.toPercent(2.5), EPS);
        assertEquals(100.0, scale.toPercent(5.0), EPS);
        assertEquals(1.25, scale.fromPercent(25.0), EPS);
    }

    @Test
    public void percentIsClampedToTheLimits() {
        final PercentScale scale = new PercentScale(1.0, 3.0);
        assertEquals(1.0, scale.fromPercent(-10.0), EPS);
        assertEquals(3.0, scale.fromPercent(150.0), EPS);
        assertEquals(0.0, scale.toPercent(0.0), EPS);
        assertEquals(100.0, scale.toPercent(9.0), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anEmptyRangeHasNoPercent() {
        new PercentScale(2.0, 2.0);
    }

    @Test
    public void oneSettingInOnePresetIsAPropertyField() {
        // "488-Volts": preset NewPreset = { '488' Voltage }
        assertEquals(ConfigGroupKind.PROPERTY,
                ConfigGroupKind.of(Collections.singletonList("NewPreset"), 1,
                        Collections.singletonList("Voltage")));
    }

    @Test
    public void namedPresetsAreADropdown() {
        assertEquals(ConfigGroupKind.PRESETS,
                ConfigGroupKind.of(Arrays.asList("405 only", "488+561"), 3,
                        Arrays.asList("OutputChannel", "Label", "Label")));
        // one preset of several settings is still a preset to pick
        assertEquals(ConfigGroupKind.PRESETS,
                ConfigGroupKind.of(Collections.singletonList("Startup"), 12,
                        Collections.singletonList("Label")));
    }

    @Test
    public void rawSerialGroupsAreLeftToMicroManagersWindow() {
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Collections.singletonList("NewPreset"), 1,
                        Collections.singletonList("SerialCommand")));
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Collections.singletonList("NewPreset"), 1,
                        Collections.singletonList("SerialResponse")));
    }

    @Test
    public void presetsThatSendASerialCommandAreHiddenToo() {
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Arrays.asList("home", "reset"), 1,
                        Arrays.asList("SerialCommand", "SerialCommand")));
    }

    @Test
    public void aGroupWithoutPresetsIsHidden() {
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Collections.emptyList(), 0, Collections.emptyList()));
    }

    @Test
    public void positionHoldsXyZAndPiezoUnderItsName() {
        final MultiStagePosition p = StagePositions.create("embryo 1",
                "XYStage:XY:31", 100.0, -20.0, "ZStage:Z:32", 3.5, "PiezoStage:P:34", 12.0);
        assertEquals("embryo 1", p.getLabel());
        assertEquals("XYStage:XY:31", p.getDefaultXYStage());
        assertEquals("ZStage:Z:32", p.getDefaultZStage());
        assertEquals(100.0, p.getX(), EPS);
        assertEquals(-20.0, p.getY(), EPS);
        assertEquals(3.5, p.getZ(), EPS);
        final StagePosition piezo = p.get("PiezoStage:P:34");
        assertTrue(piezo.is1DStagePosition());
        assertEquals(12.0, piezo.get1DPosition(), EPS);
        assertEquals(3, p.size());
    }

    @Test
    public void positionWithoutAPiezoHasTwoStages() {
        final MultiStagePosition p = StagePositions.create("a",
                "XYStage:XY:31", 0.0, 0.0, "ZStage:Z:32", 0.0, null, 0.0);
        assertEquals(2, p.size());
    }

    @Test
    public void blankNameGetsAGeneratedOne() {
        final PositionList list = new PositionList();
        final String label = StagePositions.labelFor("  ", list);
        assertFalse(label.trim().isEmpty());
        assertTrue(list.isLabelUnique(label));
    }

    @Test
    public void nameIsTrimmed() {
        assertEquals("embryo 1", StagePositions.labelFor(" embryo 1 ", new PositionList()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNameAlreadyInTheListIsRefused() {
        final PositionList list = new PositionList();
        list.addPosition(StagePositions.create("embryo 1",
                "XY", 0.0, 0.0, "Z", 0.0, null, 0.0));
        StagePositions.labelFor("embryo 1", list);
    }

    @Test
    public void rangeAndStepGiveTheSlices() {
        assertEquals(101, VolumeRange.slicesFor(100.0, 1.0)); // both ends imaged
        assertEquals(201, VolumeRange.slicesFor(100.0, 0.5));
        assertEquals(1, VolumeRange.slicesFor(0.0, 1.0));
    }

    @Test
    public void aRangeBetweenStepsRoundsToTheNearestSlice() {
        assertEquals(11, VolumeRange.slicesFor(10.4, 1.0));
        assertEquals(12, VolumeRange.slicesFor(10.6, 1.0));
    }

    @Test
    public void theRangeShownIsTheOneTheSlicesCover() {
        assertEquals(100.0, VolumeRange.rangeFor(101, 1.0), EPS);
        assertEquals(0.0, VolumeRange.rangeFor(1, 1.0), EPS);
        assertEquals(10.0, VolumeRange.rangeFor(VolumeRange.slicesFor(10.4, 1.0), 1.0), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aStepOfZeroHasNoSlices() {
        VolumeRange.slicesFor(10.0, 0.0);
    }
}

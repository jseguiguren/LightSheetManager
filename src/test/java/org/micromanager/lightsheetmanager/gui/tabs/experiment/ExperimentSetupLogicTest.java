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
                        settings("488", "Voltage")));
    }

    @Test
    public void namedPresetsAreADropdown() {
        assertEquals(ConfigGroupKind.PRESETS,
                ConfigGroupKind.of(Arrays.asList("405 only", "488+561"), 3,
                        settings("PLogic:E:36", "OutputChannel", "FilterWheel:0:38", "Label",
                                "FilterWheel:1:38", "Label")));
        // one preset of several settings is still a preset to pick
        assertEquals(ConfigGroupKind.PRESETS,
                ConfigGroupKind.of(Collections.singletonList("Startup"), 12,
                        settings("FilterWheel:0:38", "Label")));
    }

    @Test
    public void rawSerialGroupsAreLeftToMicroManagersWindow() {
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Collections.singletonList("NewPreset"), 1,
                        settings("TigerCommHub", "SerialCommand")));
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Collections.singletonList("NewPreset"), 1,
                        settings("TigerCommHub", "SerialResponse")));
    }

    @Test
    public void presetsThatSendASerialCommandAreHiddenToo() {
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Arrays.asList("home", "reset"), 1,
                        settings("TigerCommHub", "SerialCommand", "TigerCommHub", "SerialCommand")));
    }

    @Test
    public void aGroupWithoutPresetsIsHidden() {
        assertEquals(ConfigGroupKind.HIDDEN,
                ConfigGroupKind.of(Collections.emptyList(), 0, settings()));
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
    public void scanAndLateralStepGiveAnOddNumberOfSlices() {
        // odd, so the centre slice is the plane Live shows (both ends imaged)
        assertEquals(101, VolumeRange.slicesFor(100.0, 1.0));
        assertEquals(201, VolumeRange.slicesFor(100.0, 0.5));
        assertEquals(1, VolumeRange.slicesFor(0.0, 1.0));
        assertEquals(11, VolumeRange.slicesFor(11.0, 1.0)); // an odd step count drops one step
        assertEquals(13, VolumeRange.slicesFor(11.01, 1.0));
    }

    @Test
    public void aScanBetweenStepsRoundsToTheNearestOddCount() {
        // ceil(steps / 2 - 0.5) * 2 + 1: at most one step short of the range at the border
        assertEquals(11, VolumeRange.slicesFor(10.4, 1.0));
        assertEquals(11, VolumeRange.slicesFor(10.6, 1.0));
        assertEquals(11, VolumeRange.slicesFor(10.01, 1.0));
    }

    @Test
    public void floatingPointNoiseDoesNotAddSlices() {
        // 100 / 0.1 is 1000.0000000000001 and 3 / 0.1 is 30.000000000000004 in doubles
        assertEquals(1001, VolumeRange.slicesFor(100.0, 0.1));
        assertEquals(31, VolumeRange.slicesFor(3.0, 0.1));
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

    @Test
    public void theLateralStepIsTheStepOverTheSineOfTheSheetToHorizontalAngle() {
        // alpha from the vertical, beta = 90 - alpha from the horizontal: step / sin(beta)
        assertEquals(1.0 / Math.sin(Math.toRadians(40.5)),
                VolumeRange.lateralStepUm(1.0, 49.5), EPS);
        assertEquals(1.5398, VolumeRange.lateralStepUm(1.0, 49.5), 1e-4);
    }

    @Test
    public void eachEndGainsHalfTheCroppedHeightTimesTheSineOfAlpha() {
        // h = 512 px / 2 * 0.195 µm/px = 49.92 µm along the sheet; g = h sin 49.5°
        assertEquals(49.92 * Math.sin(Math.toRadians(49.5)),
                VolumeRange.extraPerEndUm(512, 0.195, 49.5), EPS);
        assertEquals(0.0, VolumeRange.extraPerEndUm(0, 0.195, 49.5), EPS);
    }

    @Test
    public void theWorkedExampleOfFullCoverage() {
        // 100 µm fully covered at 1 µm steps, 512 px crop, 0.195 µm/px, alpha 49.5°
        final double g = VolumeRange.extraPerEndUm(512, 0.195, 49.5);
        assertEquals(37.96, g, 0.01);
        assertEquals(115, VolumeRange.slicesFor(100.0 + 2 * g,
                VolumeRange.lateralStepUm(1.0, 49.5)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAngleOutsideZeroToNinetyIsRefused() {
        VolumeRange.lateralStepUm(1.0, 90.0);
    }

    /** device, property, device, property, ...: a group's settings as the panel reads them. */
    private static java.util.List<String[]> settings(final String... deviceProperty) {
        final java.util.List<String[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < deviceProperty.length; i += 2) {
            out.add(new String[] {deviceProperty[i], deviceProperty[i + 1]});
        }
        return out;
    }

    @Test
    public void groupsThatSwitchTheCoreCameraOrFocusAreHidden() {
        // "Camera" (Core Camera, plus the filter slider) and "Corefocus" (Core Focus): changed by
        // mistake, they redirect Live and the acquisition
        assertEquals(ConfigGroupKind.HIDDEN, ConfigGroupKind.of(
                Arrays.asList("Multi", "LightSheet-Cam1"), 2,
                settings("Core", "Camera", "FilterSlider:S:35", "Label")));
        assertEquals(ConfigGroupKind.HIDDEN, ConfigGroupKind.of(
                Arrays.asList("ZStage:Z:32", "PiezoStage:P:34"), 1, settings("Core", "Focus")));
    }

    @Test
    public void aGroupThatOnlySetsTheLaserChannelIsHidden() {
        // "Channel": PLogic OutputChannel alone; LSM sets it per channel during a run
        assertEquals(ConfigGroupKind.HIDDEN, ConfigGroupKind.of(
                Arrays.asList("output 3 and 5", "output 3 and 6"), 1,
                settings("PLogic:E:36", "OutputChannel", "PLogic:E:36", "OutputChannel")));
    }
}

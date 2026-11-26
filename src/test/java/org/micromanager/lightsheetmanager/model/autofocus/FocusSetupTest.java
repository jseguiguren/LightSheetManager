package org.micromanager.lightsheetmanager.model.autofocus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.micromanager.lightsheetmanager.model.PluginSettings;

public class FocusSetupTest {

    @Test
    public void defaultsAreScapecontrols() {
        final FocusSetup f = new FocusSetup();
        assertFalse(f.hasReference());
        assertEquals(1.0, f.rangeUm(), 0.0);
        assertEquals(0.25, f.coarseUm(), 0.0);
        assertEquals(0.05, f.fineUm(), 0.0);
        assertEquals("tenengrad", f.measure());
    }

    @Test
    public void theReferenceIsKeptWithBothValues() {
        final FocusSetup f = new FocusSetup();
        f.setReference(12.5, 12.25, "2026-11-26T10:00:00Z");
        assertTrue(f.hasReference());
        assertEquals(12.5, f.referenceImagingCenterUm(), 0.0);
        assertEquals(12.25, f.referencePiezoOffsetUm(), 0.0);
    }

    @Test
    public void savedWithThePluginSettings() {
        final PluginSettings settings = new PluginSettings();
        settings.focusSetup().setReference(12.5, 12.25, "2026-11-26T10:00:00Z");
        settings.focusSetup().setSearch(0.8, 0.2, 0.04, "dcts", false);
        settings.focusSetup().setImaging("Kinetix22-2", "488+561", 15.0);
        final FocusSetup back = PluginSettings.fromJson(settings.toJson()).focusSetup();
        assertEquals(12.5, back.referenceImagingCenterUm(), 0.0);
        assertEquals(0.8, back.rangeUm(), 0.0);
        assertEquals("dcts", back.measure());
        assertEquals("Kinetix22-2", back.camera());
        assertEquals("488+561", back.channelPreset());
        assertEquals(15.0, back.exposureMs(), 0.0);
    }

    @Test
    public void settingsSavedBeforeThisFeatureHaveNoReference() {
        assertFalse(PluginSettings.fromJson("{\"isPollingPositions\":true}")
                .focusSetup().hasReference());
    }

    @Test(expected = IllegalArgumentException.class)
    public void searchSettingsAreChecked() {
        new FocusSetup().setSearch(1.0, 0.05, 0.25, "tenengrad", false); // fine larger than coarse
    }

    @Test(expected = IllegalArgumentException.class)
    public void aReferenceThatIsNotANumberIsRefused() {
        new FocusSetup().setReference(Double.NaN, 1.0, "now");
    }

    @Test(expected = IllegalArgumentException.class)
    public void anExposureThatIsNotANumberIsRefused() {
        new FocusSetup().setImaging("", "", Double.POSITIVE_INFINITY);
    }

    @Test
    public void searchSettingsBecomeTheSearch() {
        final FocusSearch.Settings s = new FocusSetup().searchSettings();
        assertEquals(1.0, s.rangeUm, 0.0);
        assertEquals(0.02, s.minContrast, 0.0);
    }

    @Test
    public void theRangeIsThreeMicronsAtMostByDefault() {
        final FocusSetup f = new FocusSetup();
        assertFalse(f.extendedRange());
        f.setSearch(3.0, 0.25, 0.05, "tenengrad", false); // the limit itself is allowed
    }

    @Test(expected = IllegalArgumentException.class)
    public void beyondThreeMicronsNeedsTheExtendedRange() {
        new FocusSetup().setSearch(3.5, 0.25, 0.05, "tenengrad", false);
    }

    @Test
    public void anExtendedRangeIsSavedForScapecontrol() {
        final PluginSettings settings = new PluginSettings();
        settings.focusSetup().setSearch(5.0, 0.25, 0.05, "tenengrad", true);
        final FocusSetup back = PluginSettings.fromJson(settings.toJson()).focusSetup();
        assertTrue(back.extendedRange());
        assertEquals(5.0, back.searchSettings().rangeUm, 0.0);
    }
}

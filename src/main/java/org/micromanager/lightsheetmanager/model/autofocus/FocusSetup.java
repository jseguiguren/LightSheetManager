package org.micromanager.lightsheetmanager.model.autofocus;

import java.util.Arrays;
import java.util.List;

/**
 * The O3 autofocus setup (scapecontrol ADR-0037), saved with the plugin settings: the reference,
 * set once from "Update Offset" and never moved by autofocus, the search, and what to image while
 * searching. scapecontrol reads it for its runs; the Experiment Setup tab's "Test autofocus" uses
 * it as is. Plain fields for Gson; settings saved before this existed load with the defaults.
 */
public class FocusSetup {

    public static final List<String> MEASURES = Arrays.asList("tenengrad", "dcts");

    // null until set: an autofocus without a reference refuses
    private Double referenceImagingCenterUm;
    private Double referencePiezoOffsetUm;
    private String referenceSetAt;

    private double rangeUm = 1.0;
    private double coarseUm = 0.25;
    private double fineUm = 0.05;
    private String measure = "tenengrad";
    // the window's Extended range: off whenever LSM opens (the panel resets it)
    private boolean extendedRange = false;

    // "" means as Micro-Manager has it now
    private String camera = "";
    private String channelPreset = "";
    private double exposureMs = 10.0;

    public boolean hasReference() {
        return referenceImagingCenterUm != null && referencePiezoOffsetUm != null;
    }

    public double referenceImagingCenterUm() {
        return referenceImagingCenterUm;
    }

    public double referencePiezoOffsetUm() {
        return referencePiezoOffsetUm;
    }

    public String referenceSetAt() {
        return referenceSetAt == null ? "" : referenceSetAt;
    }

    public void setReference(final double imagingCenterUm, final double piezoOffsetUm,
                             final String setAt) {
        if (!Double.isFinite(imagingCenterUm) || !Double.isFinite(piezoOffsetUm)) {
            throw new IllegalArgumentException("the reference must be a number");
        }
        referenceImagingCenterUm = imagingCenterUm;
        referencePiezoOffsetUm = piezoOffsetUm;
        referenceSetAt = setAt;
    }

    /** @throws IllegalArgumentException as {@link FocusSearch.Settings} does, or for a measure */
    public void setSearch(final double rangeUm, final double coarseUm, final double fineUm,
                          final String measure, final boolean extendedRange) {
        // checks the values
        new FocusSearch.Settings(rangeUm, coarseUm, fineUm, 0.02, extendedRange);
        if (!MEASURES.contains(measure)) {
            throw new IllegalArgumentException("measure must be one of " + MEASURES);
        }
        this.rangeUm = rangeUm;
        this.coarseUm = coarseUm;
        this.fineUm = fineUm;
        this.measure = measure;
        this.extendedRange = extendedRange;
    }

    public boolean extendedRange() {
        return extendedRange;
    }

    public void setImaging(final String camera, final String channelPreset,
                           final double exposureMs) {
        if (!(Double.isFinite(exposureMs) && exposureMs > 0)) {
            throw new IllegalArgumentException("the exposure must be a positive number");
        }
        this.camera = camera == null ? "" : camera;
        this.channelPreset = channelPreset == null ? "" : channelPreset;
        this.exposureMs = exposureMs;
    }

    public double rangeUm() {
        return rangeUm;
    }

    public double coarseUm() {
        return coarseUm;
    }

    public double fineUm() {
        return fineUm;
    }

    public String measure() {
        return measure;
    }

    public String camera() {
        return camera == null ? "" : camera;
    }

    public String channelPreset() {
        return channelPreset == null ? "" : channelPreset;
    }

    public double exposureMs() {
        return exposureMs;
    }

    public FocusSearch.Settings searchSettings() {
        return new FocusSearch.Settings(rangeUm, coarseUm, fineUm, 0.02, extendedRange);
    }
}

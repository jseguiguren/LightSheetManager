package org.micromanager.lightsheetmanager.model.autofocus;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIScanner;

/**
 * The Experiment Setup tab's "Test autofocus": the search scapecontrol's autofocus runs
 * ({@link FocusSearch}), on the imaging the {@link FocusSetup} names, measured as frames arrive and
 * never saved. It shows the correction it would apply and changes nothing: the piezo, the sheet,
 * the channel, the camera, its exposure and Live are put back however it ends.
 *
 * <p>The piezo is driven through the Core, whose calls throw, and not through ASIPiezo, whose
 * getPosition returns 0 on a failed read: the start position it would put the piezo back to.
 */
public final class TestAutofocus {

    private static final String LOWER_LIMIT_MM = "LowerLim(mm)"; // ASITiger.h g_LowerLimPropertyName
    private static final String UPPER_LIMIT_MM = "UpperLim(mm)"; // ASITiger.h g_UpperLimPropertyName

    private TestAutofocus() {
    }

    /**
     * Runs the test on the calling thread (not the EDT: it waits on the hardware).
     *
     * @param progress told each position measured, for the tab to show
     * @param cancelled asked before each move; true stops the search, and everything is put back
     * @throws IllegalStateException if it cannot run safely: an acquisition runs, no reference is
     *     set, the piezo is outside its travel, or the channel could not be put back
     */
    public static FocusSearch.Result run(final LightSheetManager model, final FocusSetup setup,
                                         final Consumer<String> progress,
                                         final BooleanSupplier cancelled) throws Exception {
        if (model.acquisitions().isRunning()) {
            throw new IllegalStateException("an acquisition is running");
        }
        if (!setup.hasReference()) {
            throw new IllegalStateException("set the reference first");
        }
        final Studio studio = model.studio();
        final CMMCore core = studio.core();
        final String piezo = model.devices().device("ImagingFocus").getDeviceName();
        final ASIScanner scanner = model.devices().device("IllumSlice");
        // the piezo's own travel, as LSM checks it before a run (PLogicScape)
        final double[] travelUm = {
                Double.parseDouble(core.getProperty(piezo, LOWER_LIMIT_MM)) * 1000.0,
                Double.parseDouble(core.getProperty(piezo, UPPER_LIMIT_MM)) * 1000.0};

        final String camera = core.getCameraDevice();
        final String group = model.acquisitions().settings().channels().group();
        final String preset = setup.channelPreset().isEmpty() || group.isEmpty()
                ? "" : setup.channelPreset();
        final String presetBefore = preset.isEmpty() ? "" : core.getCurrentConfig(group);
        if (!preset.isEmpty() && presetBefore.isEmpty()) {
            throw new IllegalStateException("the channel group " + group + " matches no preset "
                    + "now, so the test could not put it back: choose a preset in Micro-Manager, "
                    + "or test with the channel as now");
        }
        final String imagingCamera = setup.camera().isEmpty() ? camera : setup.camera();
        final double exposureBefore = core.getExposure(imagingCamera);
        final boolean wasBeamOn = scanner.isBeamOn();
        // before anything moves: the piezo goes back here at the end, so it must be a real place
        final double start = core.getPosition(piezo);
        if (!(Double.isFinite(start) && travelUm[0] <= start && start <= travelUm[1])) {
            throw new IllegalStateException("the piezo reads " + start + " µm, outside its travel "
                    + travelUm[0] + " to " + travelUm[1] + " µm");
        }
        // Studio's way to hold Live while the camera is used: suspend, then resume
        studio.live().setSuspended(true);
        try {
            core.setCameraDevice(imagingCamera);
            core.setExposure(imagingCamera, setup.exposureMs());
            if (!preset.isEmpty()) {
                core.setConfig(group, preset);
                core.waitForConfig(group, preset);
            }
            scanner.setBeamOn(true);
            if (!scanner.isBeamOn()) {
                throw new IllegalStateException("the sheet did not turn on");
            }
            final boolean dcts = "dcts".equals(setup.measure());
            return FocusSearch.find(z -> {
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException("stopped");
                }
                try {
                    core.setPosition(piezo, z);
                    core.waitForDevice(piezo);
                    core.snapImage();
                    final double score = score(core.getImage(), (int) core.getImageWidth(),
                            (int) core.getImageHeight(), dcts);
                    progress.accept(String.format("%.3f µm: %.4g", z, score));
                    return score;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException("at " + z + " µm: " + e.getMessage(), e);
                }
            }, setup.referenceImagingCenterUm(), setup.searchSettings(), travelUm);
        } finally {
            // each step on its own: one that fails must not leave the others undone
            final StringBuilder failed = new StringBuilder();
            restore(failed, "the piezo to " + start + " µm", () -> {
                core.setPosition(piezo, start);
                core.waitForDevice(piezo);
            });
            restore(failed, "the sheet", () -> scanner.setBeamOn(wasBeamOn));
            if (!preset.isEmpty() && !presetBefore.isEmpty()) {
                restore(failed, group + " " + presetBefore, () -> {
                    core.setConfig(group, presetBefore);
                    core.waitForConfig(group, presetBefore);
                });
            }
            restore(failed, "the exposure", () -> core.setExposure(imagingCamera, exposureBefore));
            restore(failed, "the Core camera", () -> core.setCameraDevice(camera));
            restore(failed, "Live", () -> studio.live().setSuspended(false));
            if (failed.length() > 0) {
                studio.logs().showError("Test autofocus could not put back:" + failed);
            }
        }
    }

    private interface Step {
        void run() throws Exception;
    }

    private static void restore(final StringBuilder failed, final String what, final Step step) {
        try {
            step.run();
        } catch (Exception e) {
            failed.append("\n- ").append(what).append(": ").append(e.getMessage());
        }
    }

    private static double score(final Object image, final int width, final int height,
                                final boolean dcts) {
        final double[] px = new double[width * height];
        if (image instanceof short[]) {
            final short[] s = (short[]) image;
            for (int i = 0; i < px.length; i++) {
                px[i] = s[i] & 0xFFFF;
            }
        } else if (image instanceof byte[]) {
            final byte[] b = (byte[]) image;
            for (int i = 0; i < px.length; i++) {
                px[i] = b[i] & 0xFF;
            }
        } else {
            throw new IllegalStateException("cannot measure a "
                    + (image == null ? "missing" : image.getClass().getSimpleName()) + " image");
        }
        return dcts ? FocusSearch.dcts(px, width, height) : FocusSearch.tenengrad(px, width, height);
    }
}

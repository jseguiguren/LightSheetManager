package org.micromanager.lightsheetmanager.liveview;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.swing.SwingUtilities;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorPlugin;

/**
 * Keeps the Live view orientation step in Micro-Manager's on-the-fly pipeline, enabled for
 * Live and disabled for acquisitions, so acquired images are stored as the cameras send them.
 */
public final class LiveViewPipeline {

    private LiveViewPipeline() {
    }

    /**
     * Sets the flips of the Live view: updates the step, or adds it the first time a flip is
     * set, then enables it for Live only. Adding the step opens Micro-Manager's On-The-Fly
     * Image Processing window. Call on the EDT, which owns the pipeline window's table: the
     * step is added enabled for acquisitions and disabled right after, with no gap another
     * thread could see.
     */
    public static void show(final Studio studio, final LiveViewOrientation orientation) {
        int index = indexOfStep(studio);
        if (index < 0) {
            if (orientation.isEmpty()) {
                return; // nothing to flip and no step to update
            }
            final ProcessorPlugin plugin = findPlugin(studio);
            if (plugin == null) {
                studio.logs().showError("The Live view cannot be flipped: Micro-Manager did not "
                        + "load the \"" + LiveViewOrientationPlugin.NAME + "\" processor.");
                return;
            }
            studio.data().addConfiguredProcessor(
                    plugin.createConfigurator(orientation.toPropertyMap()), plugin);
            index = indexOfStep(studio);
        } else {
            ((LiveViewOrientationConfigurator) configurators(studio).get(index))
                    .setOrientation(orientation);
        }
        studio.data().setApplicationPipelineStepEnabled(index, false);
        studio.data().setLivePipelineStepEnabled(index, true);
        studio.data().notifyPipelineChanged();
    }

    /**
     * Sets the step for the acquisition about to start: on for a test acquisition, which is
     * never saved, so its window shows the cameras as Live does; off for any other, so stored
     * images are never flipped. Call before the acquisition copies the pipeline.
     *
     * @return {@code true} if the acquisition's images will be flipped
     */
    public static boolean prepareForAcquisition(final Studio studio,
                                                final boolean testAcquisition) {
        keepOutOfAcquisitions(studio);
        if (!testAcquisition) {
            return false;
        }
        final boolean[] flipped = {false};
        onEdt(studio, () -> {
            final int index = indexOfStep(studio);
            if (index >= 0 && !LiveViewOrientation.fromPropertyMap(
                    configurators(studio).get(index).getSettings()).isEmpty()) {
                studio.data().setApplicationPipelineStepEnabled(index, true);
                flipped[0] = true;
            }
        });
        return flipped[0];
    }

    /**
     * Disables the step for acquisitions if someone ticked it in the pipeline window, so the
     * acquisition about to start stores unflipped images. Call before the acquisition copies
     * the pipeline, from any thread: the work runs on the EDT, so it cannot race someone
     * editing the pipeline window.
     */
    public static void keepOutOfAcquisitions(final Studio studio) {
        onEdt(studio, () -> disableForAcquisitions(studio));
    }

    /** {@link #keepOutOfAcquisitions}, queued on the EDT without waiting for it. */
    public static void keepOutOfAcquisitionsLater(final Studio studio) {
        SwingUtilities.invokeLater(() -> disableForAcquisitions(studio));
    }

    private static void onEdt(final Studio studio, final Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            studio.logs().logError(e, "Interrupted while setting \""
                    + LiveViewOrientationPlugin.NAME + "\" for the acquisition.");
        } catch (InvocationTargetException e) {
            studio.logs().logError(e, "Could not set \""
                    + LiveViewOrientationPlugin.NAME + "\" for the acquisition.");
        }
    }

    private static void disableForAcquisitions(final Studio studio) {
        final List<ProcessorConfigurator> configs = configurators(studio);
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i) instanceof LiveViewOrientationConfigurator
                    && studio.data().isApplicationPipelineStepEnabled(i)) {
                studio.data().setApplicationPipelineStepEnabled(i, false);
                studio.logs().logMessage("Disabled \"" + LiveViewOrientationPlugin.NAME
                        + "\" for acquisitions; it only flips the Live view.");
            }
        }
    }

    private static List<ProcessorConfigurator> configurators(final Studio studio) {
        // every step, enabled or not, in the order of the pipeline window's rows
        return studio.data().getApplicationPipelineConfigurators(true);
    }

    private static int indexOfStep(final Studio studio) {
        final List<ProcessorConfigurator> configs = configurators(studio);
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i) instanceof LiveViewOrientationConfigurator) {
                return i;
            }
        }
        return -1;
    }

    private static ProcessorPlugin findPlugin(final Studio studio) {
        // keyed by class name (ConfiguratorWrapper.fromString)
        return studio.plugins().getProcessorPlugins().get(LiveViewOrientationPlugin.class.getName());
    }
}

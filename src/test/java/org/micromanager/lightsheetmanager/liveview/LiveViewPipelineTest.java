package org.micromanager.lightsheetmanager.liveview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.micromanager.LogManager;
import org.micromanager.PluginManager;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.DataManager;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.ProcessorPlugin;

/**
 * Studio's pipeline, reduced to what LiveViewPipeline touches: rows of configurators with an
 * acquisition flag and a Live flag each, as in PipelineTableModel. New rows start enabled for
 * both, as ConfiguratorWrapper does.
 */
public class LiveViewPipelineTest {

    private final List<ProcessorConfigurator> rows = new ArrayList<>();
    private final List<Boolean> enabled = new ArrayList<>();
    private final List<Boolean> enabledLive = new ArrayList<>();
    private int notifications;
    private final List<String> errors = new ArrayList<>();
    private final LiveViewOrientationPlugin plugin = new LiveViewOrientationPlugin();

    @SuppressWarnings("unchecked")
    private static <T> T stub(final Class<T> type, final java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, h);
    }

    private void addRow(final ProcessorConfigurator config, final boolean app, final boolean live) {
        rows.add(config);
        enabled.add(app);
        enabledLive.add(live);
    }

    private Studio studio() {
        final DataManager data = stub(DataManager.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getApplicationPipelineConfigurators": return rows;
                case "addConfiguredProcessor":
                    addRow((ProcessorConfigurator) a[0], true, true);
                    return null;
                case "isApplicationPipelineStepEnabled": return enabled.get((Integer) a[0]);
                case "setApplicationPipelineStepEnabled":
                    enabled.set((Integer) a[0], (Boolean) a[1]);
                    return null;
                case "setLivePipelineStepEnabled":
                    enabledLive.set((Integer) a[0], (Boolean) a[1]);
                    return null;
                case "notifyPipelineChanged":
                    notifications++;
                    return null;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
        final HashMap<String, ProcessorPlugin> plugins = new HashMap<>();
        plugins.put(LiveViewOrientationPlugin.class.getName(), plugin);
        final PluginManager pluginManager = stub(PluginManager.class, (p, m, a) -> plugins);
        final LogManager logs = stub(LogManager.class, (p, m, a) -> {
            if (m.getName().equals("showError") || m.getName().equals("logError")) {
                errors.add(String.valueOf(a[a.length - 1]));
            }
            return null;
        });
        return stub(Studio.class, (p, m, a) -> {
            switch (m.getName()) {
                case "data": return data;
                case "plugins": return pluginManager;
                case "logs": return logs;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
    }

    private static ProcessorConfigurator otherStep() {
        return stub(ProcessorConfigurator.class, (p, m, a) -> null);
    }

    @Test
    public void noFlipsAndNoStepLeavesThePipelineAlone() {
        LiveViewPipeline.show(studio(), LiveViewOrientation.none());
        assertTrue(rows.isEmpty());
        assertEquals(0, notifications);
    }

    @Test
    public void firstFlipAddsTheStepForLiveOnly() {
        addRow(otherStep(), true, false);
        final LiveViewOrientation o = LiveViewOrientation.none().with("CamB", true, false);
        LiveViewPipeline.show(studio(), o);
        assertEquals(2, rows.size());
        assertTrue(rows.get(1) instanceof LiveViewOrientationConfigurator);
        assertFalse("never in acquisitions", enabled.get(1));
        assertTrue(enabledLive.get(1));
        assertEquals(o, LiveViewOrientation.fromPropertyMap(rows.get(1).getSettings()));
        assertTrue("the other step is untouched", enabled.get(0));
        assertEquals(1, notifications);
    }

    @Test
    public void laterChangesUpdateTheExistingStep() {
        final LiveViewOrientationConfigurator step = new LiveViewOrientationConfigurator(
                LiveViewOrientation.none().with("CamB", true, false).toPropertyMap());
        addRow(otherStep(), false, false);
        addRow(step, true, false); // as restored from a profile with the boxes ticked by hand
        final LiveViewOrientation o = LiveViewOrientation.none().with("CamB", false, true);
        LiveViewPipeline.show(studio(), o);
        assertEquals(2, rows.size());
        assertSame(step, rows.get(1));
        assertEquals(o, LiveViewOrientation.fromPropertyMap(step.getSettings()));
        assertFalse(enabled.get(1));
        assertTrue(enabledLive.get(1));
    }

    @Test
    public void clearingTheFlipsKeepsAStepThatPassesImagesThrough() {
        addRow(new LiveViewOrientationConfigurator(
                LiveViewOrientation.none().with("CamB", true, true).toPropertyMap()), false, true);
        LiveViewPipeline.show(studio(), LiveViewOrientation.none());
        assertTrue(LiveViewOrientation.fromPropertyMap(rows.get(0).getSettings()).isEmpty());
    }

    @Test
    public void acquisitionsNeverGetTheStep() {
        addRow(otherStep(), true, true);
        addRow(new LiveViewOrientationConfigurator(PropertyMaps.emptyPropertyMap()), true, true);
        LiveViewPipeline.keepOutOfAcquisitions(studio());
        assertTrue("other steps keep their setting", enabled.get(0));
        assertFalse(enabled.get(1));
        assertTrue(enabledLive.get(1));
    }

    @Test
    public void imagesOfOtherCamerasAndUnnamedImagesPassThroughUncopied() {
        final LiveViewOrientationProcessor processor = new LiveViewOrientationProcessor(
                null, LiveViewOrientation.none().with("CamB", true, true)); // ij unused here
        for (final String camera : new String[] {"CamA", null}) {
            final Metadata metadata = stub(Metadata.class, (p, m, a) -> camera);
            final Image image = stub(Image.class, (p, m, a) -> metadata);
            final List<Image> out = new ArrayList<>();
            processor.processImage(image, stub(ProcessorContext.class, (p, m, a) -> {
                out.add((Image) a[0]);
                return null;
            }));
            assertEquals(1, out.size());
            assertSame(image, out.get(0));
        }
    }
}

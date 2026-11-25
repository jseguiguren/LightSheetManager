package org.micromanager.lightsheetmanager.liveview;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.lightsheetmanager.LightSheetManagerPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * The on-the-fly processing step that mirrors Live images per camera. Light Sheet Manager adds
 * it to Micro-Manager's pipeline and keeps it enabled for Live only ({@link LiveViewPipeline});
 * the flips are set on the Camera tab.
 */
@Plugin(type = ProcessorPlugin.class)
public class LiveViewOrientationPlugin implements ProcessorPlugin, SciJavaPlugin {

    public static final String NAME = "Light Sheet Manager Live View Orientation";

    private Studio studio_;

    @Override
    public void setContext(final Studio studio) {
        studio_ = studio;
    }

    @Override
    public ProcessorConfigurator createConfigurator(final PropertyMap settings) {
        return new LiveViewOrientationConfigurator(settings);
    }

    @Override
    public ProcessorFactory createFactory(final PropertyMap settings) {
        final LiveViewOrientation orientation = LiveViewOrientation.fromPropertyMap(settings);
        return () -> new LiveViewOrientationProcessor(studio_.data().ij(), orientation);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getHelpText() {
        return "Mirrors the Live images of the cameras ticked on the Camera tab of Light Sheet "
                + "Manager. Set the flips there; the step is meant for Live only.";
    }

    @Override
    public String getVersion() {
        return LightSheetManagerPlugin.version;
    }

    @Override
    public String getCopyright() {
        return LightSheetManagerPlugin.copyright;
    }
}

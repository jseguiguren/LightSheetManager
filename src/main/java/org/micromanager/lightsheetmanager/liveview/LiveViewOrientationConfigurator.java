package org.micromanager.lightsheetmanager.liveview;

import org.micromanager.PropertyMap;
import org.micromanager.data.ProcessorConfigurator;

/**
 * Holds the pipeline step's settings. It has no window of its own: the flips are set on the
 * Camera tab of Light Sheet Manager, which writes them here.
 */
public class LiveViewOrientationConfigurator implements ProcessorConfigurator {

    // written from the EDT, read when Micro-Manager builds a pipeline
    private volatile PropertyMap settings_;

    public LiveViewOrientationConfigurator(final PropertyMap settings) {
        settings_ = settings;
    }

    public void setOrientation(final LiveViewOrientation orientation) {
        settings_ = orientation.toPropertyMap();
    }

    @Override
    public void showGUI() {
        // nothing to show: the Camera tab is the editor
    }

    @Override
    public void cleanup() {
    }

    @Override
    public PropertyMap getSettings() {
        return settings_;
    }
}

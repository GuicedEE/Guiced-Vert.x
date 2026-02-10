package com.guicedee.vertx.implementations;

import com.guicedee.client.services.IGuiceConfig;
import com.guicedee.client.services.lifecycle.IGuiceConfigurator;

public class VertxClassScanConfig implements IGuiceConfigurator {
    @Override
    public IGuiceConfig<?> configure(IGuiceConfig<?> config) {
        config.setIgnoreClassVisibility(true)
                .setIgnoreMethodVisibility(true)
                .setIgnoreFieldVisibility(true)
                .setAnnotationScanning(true)
                .setClasspathScanning(true);
        return config;
    }
}

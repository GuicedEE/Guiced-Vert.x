package com.guicedee.vertx.spi;

import io.vertx.core.VertxBuilder;

@FunctionalInterface
public interface VertxConfigurator {
    VertxBuilder builder(VertxBuilder builder);
}

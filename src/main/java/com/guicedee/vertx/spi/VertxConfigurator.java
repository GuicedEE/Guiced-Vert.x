package com.guicedee.vertx.spi;

import io.vertx.core.VertxBuilder;

/**
 * ServiceLoader extension point for contributing additional
 * {@link VertxBuilder} configuration during startup.
 */
@FunctionalInterface
public interface VertxConfigurator {
    VertxBuilder builder(VertxBuilder builder);
}

package com.guicedee.vertx.spi;

import io.vertx.core.VertxBuilder;

/**
 * ServiceLoader extension point for contributing additional
 * {@link VertxBuilder} configuration during startup.
 */
@FunctionalInterface
public interface VertxConfigurator {
    VertxBuilder builder(VertxBuilder builder);

    /** Compose options before the builder is configured. Return the shared options or an intentional replacement. */
    default io.vertx.core.VertxOptions options(io.vertx.core.VertxOptions options) { return options; }
}

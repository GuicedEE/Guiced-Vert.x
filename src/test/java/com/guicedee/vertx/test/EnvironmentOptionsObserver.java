package com.guicedee.vertx.test;

import com.guicedee.vertx.spi.VertxConfigurator;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;

/** Records composed options without changing the runtime being tested. */
public final class EnvironmentOptionsObserver implements VertxConfigurator {
    static volatile VertxOptions observed;
    @Override public VertxBuilder builder(VertxBuilder builder) { return builder; }
    @Override public VertxOptions options(VertxOptions options) {
        observed = new VertxOptions(options);
        return options;
    }
}

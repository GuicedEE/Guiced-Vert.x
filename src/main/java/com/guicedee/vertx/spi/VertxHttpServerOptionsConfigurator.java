package com.guicedee.vertx.spi;

import io.vertx.core.http.HttpServerOptions;

@FunctionalInterface
public interface VertxHttpServerOptionsConfigurator
{
    HttpServerOptions builder(HttpServerOptions builder);
}

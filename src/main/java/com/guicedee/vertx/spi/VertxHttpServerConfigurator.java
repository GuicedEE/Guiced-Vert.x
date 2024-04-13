package com.guicedee.vertx.spi;

import io.vertx.core.http.HttpServer;

@FunctionalInterface
public interface VertxHttpServerConfigurator
{
    HttpServer builder(HttpServer builder);
}

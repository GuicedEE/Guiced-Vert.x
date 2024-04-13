package com.guicedee.vertx.spi;

import io.vertx.ext.web.Router;

@FunctionalInterface
public interface VertxRouterConfigurator
{
    Router builder(Router builder);
}

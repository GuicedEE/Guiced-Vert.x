package com.guicedee.vertx.spi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class VertxConsumersStartup implements VerticleStartup<VertxConsumersStartup>
{
    @Override
    public void start(Promise<Void> startPromise, Vertx vertx, AbstractVerticle verticle, String assignedPackage)
    {
        try {
            // Register consumers filtered to this verticle's package assignment.
            VertxEventRegistry.registerEventConsumersFiltered(assignedPackage, VerticleBuilder.getAnnotatedPrefixes());
            log.debug("VertxConsumersStartup completed for assignedPackage='{}'", assignedPackage);
        } catch (Throwable t) {
            log.error("Failed to register consumers for assignedPackage='{}'", assignedPackage, t);
            // Do not fail the verticle start promise here to avoid cascading failures.
        }
    }
}

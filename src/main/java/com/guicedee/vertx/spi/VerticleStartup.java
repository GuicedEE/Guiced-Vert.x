package com.guicedee.vertx.spi;

import com.guicedee.client.services.IDefaultService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * SPI for initializing a Vert.x verticle instance before it starts.
 * <p>
 * Implementations can perform setup using the provided Vertx instance,
 * verticle, and assigned package context, then complete or fail the
 * {@link Promise} to signal startup readiness.
 */
public interface VerticleStartup<J extends VerticleStartup<J>> extends IDefaultService<J>
{

    void start(Promise<Void> startPromise, Vertx vertx, AbstractVerticle verticle,String assignedPackage);
}

package com.guicedee.vertx.spi;

import com.guicedee.client.services.IDefaultService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public interface VerticleStartup<J extends VerticleStartup<J>> extends IDefaultService<J>
{

    void start(Promise<Void> startPromise, Vertx vertx, AbstractVerticle verticle,String assignedPackage);
}

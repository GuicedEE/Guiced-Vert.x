package com.guicedee.vertx.spi;

import com.guicedee.client.services.lifecycle.IGuicePostStartup;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;

/**
 * Deploys Vert.x verticles after the Guice injector is fully built.
 * <p>
 * Verticle deployment is deferred to post-startup because {@link VerticleStartup}
 * implementations may use the Guice injector during their {@code start()} method.
 * Running them during pre-startup (before the injector exists) would cause a
 * recursive injector build error.
 */
@Log4j2
public class VertxVerticlePostStartup implements IGuicePostStartup<VertxVerticlePostStartup>
{
    @Override
    public List<Uni<Boolean>> postLoad()
    {
        log.info("🚀 Deploying Vert.x verticles (post-startup)");

        // Deploy verticles - each verticle will register its assigned consumers via VertxConsumersStartup
        new VerticleBuilder().findVerticles();

        // Convert the verticle deployment futures to Uni so the lifecycle waits for them to complete
        Map<String, Future<?>> verticleFutures = VerticleBuilder.getVerticleFutures();
        if (!verticleFutures.isEmpty())
        {
            return verticleFutures.values().stream()
                    .map(f -> Uni.createFrom().<Boolean>emitter(em ->
                            f.onComplete(ar -> {
                                if (ar.succeeded())
                                {
                                    em.complete(true);
                                }
                                else
                                {
                                    em.fail(ar.cause());
                                }
                            })
                    ))
                    .toList();
        }

        return List.of(Uni.createFrom().item(true));
    }

    /**
     * Ensures verticle deployment runs early in the post-startup chain,
     * before consumers like the web server (which runs at MIN_VALUE + 500).
     *
     * @return the ordering value for this post-startup task
     */
    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 50;
    }
}



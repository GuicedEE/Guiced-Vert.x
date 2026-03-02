package com.guicedee.vertx.spi;

import com.guicedee.vertx.VertxEventDefinition;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.internal.ContextInternal;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;

@Log4j2
public class EventConsumerVerticle extends AbstractVerticle {

    private final String address;
    private final VertxEventDefinition definition;
    private final Method targetMethod;
    private final Class<?> targetClass;

    public EventConsumerVerticle(String address, VertxEventDefinition definition, Method targetMethod, Class<?> targetClass) {
        this.address = address;
        this.definition = definition;
        this.targetMethod = targetMethod;
        this.targetClass = targetClass;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            // Guard against duplicate registration for the same address
            if (!VertxEventRegistry.getRegisteredAddresses().add(address)) {
                log.debug("Consumer for address '{}' already registered, skipping duplicate", address);
                startPromise.tryComplete();
                return;
            }

            boolean localOnly = definition != null && definition.options().localOnly();
            MessageConsumer<?> consumer;
            if (localOnly) {
                consumer = vertx.eventBus().localConsumer(address);
            } else {
                consumer = vertx.eventBus().consumer(address);
            }

            // Note: buffering configuration (maxBufferedMessages) may not be supported across all Vert.x versions.
            // If supported in your version, you can enable it here.

            consumer.handler(message -> {
                vertx.runOnContext(v ->
                    VertxEventRegistry
                            .dispatch(vertx, message, targetMethod, targetClass, definition)
                            .subscribe().with(
                                    ignored -> { /* no-op */ },
                                    ex -> {
                                        Throwable cause = (ex instanceof java.lang.reflect.InvocationTargetException && ex.getCause() != null)
                                                ? ex.getCause() : ex;
                                        log.error("Error dispatching message for {}: {}", message.address(), cause.getMessage(), cause);
                                        try {
                                            message.fail(500, String.valueOf(cause.getMessage()));
                                        } catch (Throwable ignored2) {
                                        }
                                    }
                            )
                );
            });

            log.debug("Registered consumer on address '{}' in verticle {} (localOnly={})", address, this.getClass().getSimpleName(), localOnly);
            startPromise.tryComplete();
        } catch (Throwable t) {
            log.error("Failed to start EventConsumerVerticle for {}", address, t);
            startPromise.tryFail(t);
        }
    }
}

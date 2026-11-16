package com.guicedee.vertx.spi;

import com.guicedee.vertx.VertxEventDefinition;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;

@Log4j2
public class EventConsumerVerticle extends AbstractVerticle {

    private final String address;
    private final VertxEventDefinition definition;
    private final Method targetMethod;
    private final Class<?> targetClass;
    private final Runnable onStop;
    private MessageConsumer<?> consumer;

    public EventConsumerVerticle(String address, VertxEventDefinition definition, Method targetMethod, Class<?> targetClass) {
        this(address, definition, targetMethod, targetClass, () -> {});
    }

    EventConsumerVerticle(String address, VertxEventDefinition definition, Method targetMethod,
                          Class<?> targetClass, Runnable onStop) {
        this.address = address;
        this.definition = definition;
        this.targetMethod = targetMethod;
        this.targetClass = targetClass;
        this.onStop = onStop;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            boolean localOnly = definition != null && definition.options().localOnly();
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
            consumer.completion().onComplete(startPromise);
        } catch (Throwable t) {
            log.error("Failed to start EventConsumerVerticle for {}", address, t);
            startPromise.tryFail(t);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (consumer == null) {
            onStop.run();
            stopPromise.tryComplete();
        } else {
            consumer.unregister().onComplete(result -> {
                onStop.run();
                stopPromise.handle(result);
            });
        }
    }
}

package com.guicedee.vertx;

import io.vertx.core.eventbus.Message;

/**
 * Interface for consuming Vertx event bus messages
 */
public interface VertxConsumer<T> {
    /**
     * Handles a message from the Vertx event bus
     *
     * @param message The message to handle
     */
    void consume(Message<T> message);
}
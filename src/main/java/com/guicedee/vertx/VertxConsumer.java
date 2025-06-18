package com.guicedee.vertx;

import com.guicedee.client.CallScopeProperties;
import com.guicedee.client.CallScopeSource;
import com.guicedee.client.IGuiceContext;
import io.vertx.core.eventbus.Message;

/**
 * Interface for consuming Vertx event bus messages
 */
public interface VertxConsumer<T> {

    /**
     * Sets the source of the message as consumers
     */
    default void configure()
    {
        var csp = IGuiceContext.get(CallScopeProperties.class);
        if (csp.getSource() == null) {
            csp.setSource(CallScopeSource.Startup);
        }
    }

    /**
     * Handles a message from the Vertx event bus
     *
     * @param message The message to handle
     */
    void consume(Message<T> message);
}
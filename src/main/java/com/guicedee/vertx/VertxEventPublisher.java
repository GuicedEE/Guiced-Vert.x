package com.guicedee.vertx;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import lombok.EqualsAndHashCode;
import lombok.extern.log4j.Log4j2;

/**
 * Publisher for Vertx event bus messages
 */
@JsonSerialize(as = Void.class)
@EqualsAndHashCode(of = {"address"})
@Log4j2
public class VertxEventPublisher<T> {

    private final Vertx vertx;
    private final String address;
    private final VertxEventDefinition eventDefinition;

    public VertxEventPublisher(Vertx vertx, String address, VertxEventDefinition eventDefinition) {
        this.vertx = vertx;
        this.address = address;
        this.eventDefinition = eventDefinition;
    }

    /**
     * Publish a message to the event bus
     *
     * @param message The message to publish
     */
    public void publish(T message) {
        log.trace("Publishing message to address {} - {}", address, message);
        vertx.eventBus().publish(address, message);
    }

    /**
     * Send a message to the event bus (point-to-point)
     *
     * @param message The message to send
     * @return A future that completes with the reply
     */
    public <R> Future<R> send(T message) {
        log.trace("Sending message to address {} - {}", address, message);
        return vertx.eventBus().request(address, message)
                .map(reply -> (R) reply.body());
    }

    /**
     * Publish a message to the event bus with delivery options
     *
     * @param message The message to publish
     * @param options Delivery options
     */
    public void publish(T message, DeliveryOptions options) {
        log.trace("Publishing message to address {} with options - {}", address, message);
        vertx.eventBus().publish(address, message, options);
    }

    /**
     * Send a message to the event bus (point-to-point) with delivery options
     *
     * @param message The message to send
     * @param options Delivery options
     * @return A future that completes with the reply
     */
    public <R> Future<R> send(T message, DeliveryOptions options) {
        log.trace("Sending message to address {} with options - {}", address, message);
        return vertx.eventBus().request(address, message, options)
                .map(reply -> (R) reply.body());
    }
}

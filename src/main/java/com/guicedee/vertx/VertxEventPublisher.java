package com.guicedee.vertx;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
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
        try {
            String jsonMessage = IJsonRepresentation.getObjectMapper().writeValueAsString(message);
            vertx.eventBus().publish(address, jsonMessage);
        } catch (Exception e) {
            log.error("Error serializing message to JSON", e);
            throw new RuntimeException("Error publishing message", e);
        }
    }

    /**
     * Send a message to the event bus (point-to-point)
     *
     * @param message The message to send
     * @return A future that completes with the reply
     */
    public <R> Future<R> send(T message) {
        log.trace("Sending message to address {} - {}", address, message);
        try {
            String jsonMessage = IJsonRepresentation.getObjectMapper().writeValueAsString(message);
            return vertx.eventBus().request(address, jsonMessage)
                    .map(reply -> {
                        Object body = reply.body();
                        if (body instanceof String) {
                            try {
                                return (R) IJsonRepresentation.getObjectMapper().readValue((String) body, Object.class);
                            } catch (Exception e) {
                                log.warn("Could not deserialize reply, returning raw body", e);
                                return (R) body;
                            }
                        }
                        return (R) body;
                    });
        } catch (Exception e) {
            log.error("Error serializing message to JSON", e);
            return Future.failedFuture(e);
        }
    }

    /**
     * Publish a message to the event bus with delivery options
     *
     * @param message The message to publish
     * @param options Delivery options
     */
    public void publish(T message, DeliveryOptions options) {
        log.trace("Publishing message to address {} with options - {}", address, message);
        try {
            String jsonMessage = IJsonRepresentation.getObjectMapper().writeValueAsString(message);
            vertx.eventBus().publish(address, jsonMessage, options);
        } catch (Exception e) {
            log.error("Error serializing message to JSON", e);
            throw new RuntimeException("Error publishing message with options", e);
        }
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
        try {
            String jsonMessage = IJsonRepresentation.getObjectMapper().writeValueAsString(message);
            return vertx.eventBus().request(address, jsonMessage, options)
                    .map(reply -> {
                        Object body = reply.body();
                        if (body instanceof String) {
                            try {
                                return (R) IJsonRepresentation.getObjectMapper().readValue((String) body, Object.class);
                            } catch (Exception e) {
                                log.warn("Could not deserialize reply, returning raw body", e);
                                return (R) body;
                            }
                        }
                        return (R) body;
                    });
        } catch (Exception e) {
            log.error("Error serializing message to JSON", e);
            return Future.failedFuture(e);
        }
    }
}

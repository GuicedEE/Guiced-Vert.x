package com.guicedee.vertx;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;

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

    /**
     * The reference type of the generic parameter T
     */
    @Getter
    private final Type referenceType;

    public VertxEventPublisher(Vertx vertx, String address, VertxEventDefinition eventDefinition) {
        this(vertx, address, eventDefinition, Object.class);
    }

    /**
     * Constructor with reference type
     *
     * @param vertx The Vertx instance
     * @param address The event bus address
     * @param eventDefinition The event definition
     * @param referenceType The reference type of the generic parameter T
     */
    public VertxEventPublisher(Vertx vertx, String address, VertxEventDefinition eventDefinition, Type referenceType) {
        this.vertx = vertx;
        this.address = address;
        this.eventDefinition = eventDefinition;
        this.referenceType = referenceType;
    }

    /**
     * Gets the codec name for the given message using the CodecRegistry
     *
     * @param message The message
     * @return The codec name, or null if it's a standard Vertx type
     */
    private String getCodecName(Object message) {
        if (message == null) {
            return null;
        }

        Class<?> messageClass = message.getClass();
        return com.guicedee.vertx.spi.CodecRegistry.getCodecName(messageClass);
    }

    /**
     * Publish a message to the event bus
     *
     * @param message The message to publish
     */
    public void publish(T message) {
        log.trace("Publishing message to address {} - {}", address, message);
        try {
            String codecName = getCodecName(message);
            if (codecName != null) {
                DeliveryOptions options = new DeliveryOptions().setCodecName(codecName);
                vertx.eventBus().publish(address, message, options);
            } else {
                vertx.eventBus().publish(address, message);
            }
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
            String codecName = getCodecName(message);
            if (codecName != null) {
                DeliveryOptions options = new DeliveryOptions().setCodecName(codecName);
                return vertx.eventBus().request(address, message, options)
                        .map(reply -> {
                            Object body = reply.body();
                            return (R) body;
                        });
            } else {
                return vertx.eventBus().request(address, message)
                        .map(reply -> {
                            Object body = reply.body();
                            return (R) body;
                        });
            }
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
            // Set codec name if not already set and message is not a standard Vertx type
            String codecName = getCodecName(message);
            if (codecName != null && options.getCodecName() == null) {
                options.setCodecName(codecName);
            }

            vertx.eventBus().publish(address, message, options);
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
            // Set codec name if not already set and message is not a standard Vertx type
            String codecName = getCodecName(message);
            if (codecName != null && options.getCodecName() == null) {
                options.setCodecName(codecName);
            }

            return vertx.eventBus().request(address, message, options)
                    .map(reply -> {
                        Object body = reply.body();
                        return (R) body;
                    });
        } catch (Exception e) {
            log.error("Error serializing message to JSON", e);
            return Future.failedFuture(e);
        }
    }
}

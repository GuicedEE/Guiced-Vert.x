package com.guicedee.vertx;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
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
        doImmediatePublish(message, null);
    }

    /**
     * Fire-and-forget point-to-point send (no reply expected)
     *
     * @param message The message to send
     */
    public void send(T message) {
        log.trace("Fire-and-forget send to address {} - {}", address, message);
        doImmediateSend(message, null);
    }

    /**
     * Publish a message to the event bus with delivery options
     *
     * @param message The message to publish
     * @param options Delivery options
     */
    public void publish(T message, DeliveryOptions options) {
        log.trace("Publishing message to address {} with options - {}", address, message);
        doImmediatePublish(message, options);
    }

    /**
     * Publish a message locally only (won't cross the cluster)
     */
    public void publishLocal(T message) {
        DeliveryOptions options = new DeliveryOptions().setLocalOnly(true);
        publish(message, options);
    }

    /**
     * Publish a message with optional headers and localOnly flag
     */
    public void publish(T message, java.util.Map<String, String> headers, boolean localOnly) {
        DeliveryOptions options = new DeliveryOptions().setLocalOnly(localOnly);
        if (headers != null) {
            headers.forEach(options::addHeader);
        }
        publish(message, options);
    }

    /**
     * Fire-and-forget point-to-point send with delivery options (no reply expected)
     *
     * @param message The message to send
     * @param options Delivery options
     */
    public void send(T message, DeliveryOptions options) {
        log.trace("Fire-and-forget send to address {} with options - {}", address, message);
        doImmediateSend(message, options);
    }

    /**
     * Fire-and-forget local-only send (won't cross the cluster)
     */
    public void sendLocal(T message) {
        DeliveryOptions options = new DeliveryOptions().setLocalOnly(true);
        send(message, options);
    }

    /**
     * Fire-and-forget send with headers and localOnly flag
     */
    public void send(T message, java.util.Map<String, String> headers, boolean localOnly) {
        DeliveryOptions options = new DeliveryOptions().setLocalOnly(localOnly);
        if (headers != null) {
            headers.forEach(options::addHeader);
        }
        send(message, options);
    }

    /**
     * Request/reply: point-to-point that expects a reply.
     * Uses @VertxEventOptions.timeoutMs() when configured.
     */
    public <R> Future<R> request(T message) {
        log.trace("Requesting on address {} - {}", address, message);
        try {
            DeliveryOptions options = new DeliveryOptions();
            // Default timeout from annotation if provided
            if (eventDefinition != null && eventDefinition.options() != null) {
                long configured = eventDefinition.options().timeoutMs();
                if (configured > 0) options.setSendTimeout(configured);
            }
            String codecName = getCodecName(message);
            if (codecName != null) options.setCodecName(codecName);
            return vertx.eventBus().request(address, message, options)
                    .map(reply -> (R) reply.body());
        } catch (Exception e) {
            log.error("Error performing request", e);
            return Future.failedFuture(e);
        }
    }

    /**
     * Request/reply with explicit delivery options.
     */
    public <R> Future<R> request(T message, DeliveryOptions options) {
        log.trace("Requesting on address {} with options - {}", address, message);
        try {
            if (options == null) options = new DeliveryOptions();
            // Apply codec if needed
            String codecName = getCodecName(message);
            if (codecName != null && options.getCodecName() == null) {
                options.setCodecName(codecName);
            }
            // If no timeout provided, prefer annotation default
            if (options.getSendTimeout() == 0 && eventDefinition != null && eventDefinition.options() != null) {
                long configured = eventDefinition.options().timeoutMs();
                if (configured > 0) options.setSendTimeout(configured);
            }
            return vertx.eventBus().request(address, message, options)
                    .map(reply -> (R) reply.body());
        } catch (Exception e) {
            log.error("Error performing request with options", e);
            return Future.failedFuture(e);
        }
    }

    /**
     * Request/reply with explicit timeout.
     */
    public <R> Future<R> request(T message, long timeoutMs) {
        DeliveryOptions options = new DeliveryOptions();
        if (timeoutMs > 0) options.setSendTimeout(timeoutMs);
        return request(message, options);
    }

    // ============ Internal helpers ============

    private void doImmediatePublish(T message, DeliveryOptions options) {
        try {
            if (options == null) {
                String codecName = getCodecName(message);
                if (codecName != null) {
                    options = new DeliveryOptions().setCodecName(codecName);
                    vertx.eventBus().publish(address, message, options);
                } else {
                    vertx.eventBus().publish(address, message);
                }
            } else {
                DeliveryOptions opts = cloneOptionsWithCodec(options, message);
                vertx.eventBus().publish(address, message, opts);
            }
        } catch (Exception e) {
            log.error("Error serializing message to JSON", e);
            throw new RuntimeException("Error publishing message", e);
        }
    }

    private void doImmediateSend(T message, DeliveryOptions options) {
        try {
            if (options == null) {
                String codecName = getCodecName(message);
                if (codecName != null) {
                    options = new DeliveryOptions().setCodecName(codecName);
                    vertx.eventBus().send(address, message, options);
                } else {
                    vertx.eventBus().send(address, message);
                }
            } else {
                DeliveryOptions opts = cloneOptionsWithCodec(options, message);
                vertx.eventBus().send(address, message, opts);
            }
        } catch (Exception e) {
            log.error("Error sending message", e);
            throw new RuntimeException("Error sending message", e);
        }
    }

    private DeliveryOptions cloneOptionsWithCodec(DeliveryOptions options, Object message) {
        DeliveryOptions src = options == null ? new DeliveryOptions() : options;
        DeliveryOptions dst = new DeliveryOptions()
                .setCodecName(src.getCodecName())
                .setSendTimeout(src.getSendTimeout())
                .setLocalOnly(src.isLocalOnly());
        if (src.getHeaders() != null) {
            src.getHeaders().forEach(entry -> dst.addHeader(entry.getKey(), entry.getValue()));
        }
        String codecName = getCodecName(message);
        if (codecName != null && dst.getCodecName() == null) {
            dst.setCodecName(codecName);
        }
        return dst;
    }


    /**
     * Request/reply with headers and timeout.
     */
    public <R> Future<R> request(T message, java.util.Map<String, String> headers, long timeoutMs) {
        DeliveryOptions options = new DeliveryOptions();
        if (headers != null) headers.forEach(options::addHeader);
        if (timeoutMs > 0) options.setSendTimeout(timeoutMs);
        return request(message, options);
    }
}

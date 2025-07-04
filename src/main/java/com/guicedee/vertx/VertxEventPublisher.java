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
import java.util.regex.Pattern;

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

    /**
     * Pattern for converting camel case to kebab case
     */
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])|([A-Z])([A-Z][a-z])");

    /**
     * Standard Vertx types that don't need custom codecs
     */
    private static final Class<?>[] STANDARD_VERTX_TYPES = {
        String.class,
        Boolean.class,
        boolean.class,
        Integer.class,
        int.class,
        Long.class,
        long.class,
        Double.class,
        double.class,
        Float.class,
        float.class,
        Short.class,
        short.class,
        Byte.class,
        byte.class,
        Character.class,
        char.class,
        JsonObject.class,
        JsonArray.class,
        Buffer.class,
        byte[].class
    };

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
     * Checks if the given class is a standard Vertx type
     *
     * @param clazz The class to check
     * @return true if the class is a standard Vertx type, false otherwise
     */
    private boolean isStandardVertxType(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }

        for (Class<?> standardType : STANDARD_VERTX_TYPES) {
            if (standardType.equals(clazz)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Converts a camel case string to kebab case
     *
     * @param camelCase The camel case string
     * @return The kebab case string
     */
    private String toKebabCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "";
        }

        // Replace camel case with kebab case (e.g., "camelCase" -> "camel-case")
        // Also handle acronyms (e.g., "UWEServerMessage" -> "uwe-server-message")
        String kebabCase = CAMEL_CASE_PATTERN.matcher(camelCase).replaceAll(match -> {
            // Check which pattern matched
            if (match.group(1) != null && match.group(2) != null) {
                // First pattern: lowercase to uppercase
                return match.group(1) + "-" + match.group(2);
            } else {
                // Second pattern: uppercase to uppercase followed by lowercase
                return match.group(3) + "-" + match.group(4);
            }
        }).toLowerCase();
        return kebabCase;
    }

    /**
     * Gets the codec name for the given message
     *
     * @param message The message
     * @return The codec name, or null if it's a standard Vertx type
     */
    private String getCodecName(Object message) {
        if (message == null) {
            return null;
        }

        Class<?> messageClass = message.getClass();
        if (isStandardVertxType(messageClass)) {
            return null;
        }

        // Get the simple name of the class (without package)
        String className = messageClass.getSimpleName();

        // Convert to kebab case
        return toKebabCase(className);
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

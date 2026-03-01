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
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    // Throttling configuration (per-publisher instance)
    private final long throttleMs;
    private final int warnThreshold;

    // Simple FIFO queue and periodic drain (1 message per throttleMs)
    private final Queue<Queued> queue = new ArrayDeque<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private volatile long timerId = -1L;
    private final AtomicInteger lastWarnBucket = new AtomicInteger(0);

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

        // Resolve throttle and warning thresholds (env or system properties), with per-address override support
        String normalized = normalizeAddress(address);
        this.throttleMs = resolveLong(
                firstNonBlank(
                        sysOrEnv("VERTX_PUBLISH_THROTTLE_MS_" + normalized),
                        sysOrEnv("VERTX_PUBLISH_THROTTLE_MS")
                ),
                50L
        );
        this.warnThreshold = (int) resolveLong(
                firstNonBlank(
                        sysOrEnv("VERTX_PUBLISH_QUEUE_WARN_" + normalized),
                        sysOrEnv("VERTX_PUBLISH_QUEUE_WARN")
                ),
                1000L
        );
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
        if (throttleMs <= 0) {
            // Bypass throttle completely
            doImmediatePublish(message, null);
            return;
        }
        enqueue(new Queued(true, message, null));
    }

    /**
     * Fire-and-forget point-to-point send (no reply expected)
     *
     * @param message The message to send
     */
    public void send(T message) {
        log.trace("Fire-and-forget send to address {} - {}", address, message);
        if (throttleMs <= 0) {
            doImmediateSend(message, null);
            return;
        }
        enqueue(new Queued(false, message, null));
    }

    /**
     * Publish a message to the event bus with delivery options
     *
     * @param message The message to publish
     * @param options Delivery options
     */
    public void publish(T message, DeliveryOptions options) {
        log.trace("Publishing message to address {} with options - {}", address, message);
        if (throttleMs <= 0) {
            doImmediatePublish(message, options);
            return;
        }
        // Copy options to avoid external mutation issues
        DeliveryOptions opts = cloneOptionsWithCodec(options, message);
        enqueue(new Queued(true, message, opts));
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
        if (throttleMs <= 0) {
            doImmediateSend(message, options);
            return;
        }
        DeliveryOptions opts = cloneOptionsWithCodec(options, message);
        enqueue(new Queued(false, message, opts));
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

    // ============ Internal helpers (throttle & immediate dispatch) ============

    private void enqueue(Queued q) {
        Objects.requireNonNull(q);
        synchronized (queue) {
            queue.add(q);
            // Warning strategy when queue grows beyond threshold (no drops)
            if (warnThreshold > 0 && queue.size() > warnThreshold) {
                int bucket = (int) Math.max(1, Math.floorDiv(queue.size(), Math.max(1, warnThreshold)));
                int prev = lastWarnBucket.get();
                if (bucket > prev && lastWarnBucket.compareAndSet(prev, bucket)) {
                    log.warn("Publisher queue for address '{}' is growing (size={}, threshold={}). Messages are throttled to one every {} ms.",
                            address, queue.size(), warnThreshold, throttleMs);
                }
            }

            // Start periodic drain if not running
            if (draining.compareAndSet(false, true)) {
                timerId = vertx.setPeriodic(throttleMs, tid -> drainOne());
            }
        }
    }

    private void drainOne() {
        Queued next;
        synchronized (queue) {
            next = queue.poll();
            if (next == null) {
                // Stop timer, nothing to drain
                if (timerId != -1L) {
                    vertx.cancelTimer(timerId);
                    timerId = -1L;
                }
                draining.set(false);
                lastWarnBucket.set(0);
                return;
            }
        }

        try {
            if (next.publish) {
                if (next.options != null) {
                    vertx.eventBus().publish(address, next.message, next.options);
                } else {
                    String codec = getCodecName(next.message);
                    if (codec != null) {
                        vertx.eventBus().publish(address, next.message, new DeliveryOptions().setCodecName(codec));
                    } else {
                        vertx.eventBus().publish(address, next.message);
                    }
                }
            } else {
                if (next.options != null) {
                    vertx.eventBus().send(address, next.message, next.options);
                } else {
                    String codec = getCodecName(next.message);
                    if (codec != null) {
                        vertx.eventBus().send(address, next.message, new DeliveryOptions().setCodecName(codec));
                    } else {
                        vertx.eventBus().send(address, next.message);
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Error dispatching throttled message on '{}': {}", address, t.getMessage(), t);
        }
    }

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

    private static String sysOrEnv(String key) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) v = System.getenv(key);
        return v;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static long resolveLong(String raw, long def) {
        if (raw == null || raw.isBlank()) return def;
        try { return Long.parseLong(raw.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static String normalizeAddress(String addr) {
        if (addr == null) return "";
        return addr.toUpperCase().replace('.', '_').replace('-', '_');
    }

    private static final class Queued {
        final boolean publish;
        final Object message;
        final DeliveryOptions options;
        Queued(boolean publish, Object message, DeliveryOptions options) {
            this.publish = publish;
            this.message = message;
            this.options = options;
        }
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

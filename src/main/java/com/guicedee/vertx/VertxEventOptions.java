package com.guicedee.vertx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Options for Vertx event bus consumers
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface VertxEventOptions {
    /**
     * @return Whether to register the consumer with a local-only handler
     */
    boolean localOnly() default true;

    /**
     * @return Auto start/bind the consumer on startup, otherwise make an injection call to start
     */
    boolean autobind() default true;

    /**
     * @return Number of consumer instances to create
     */
    int consumerCount() default 1;

    /**
     * If true, dispatch handling on worker threads instead of the event loop.
     * Heavy IO/DB writes should set this to true.
     */
    boolean worker() default false;

    /**
     * Name of the dedicated worker pool to use when worker() is true.
     * If empty, Vert.x default worker pool is used.
     */
    String workerPool() default "";

    /**
     * Size of the dedicated worker pool when workerPool() is set.
     * Ignored if workerPool() is empty or size <= 0.
     */
    int workerPoolSize() default 0;

    /**
     * Alternative alias for consumerCount. If > 0, overrides consumerCount().
     */
    int instances() default 0;

    /**
     * Header name used to indicate ordering key. When non-empty, publishers may
     * add this header to enable per-key ordering strategies. Currently advisory.
     */
    String orderedByHeader() default "";

    /**
     * Maximum buffered messages for the consumer before backpressure.
     * Passed to MessageConsumer#setMaxBufferedMessages when > 0.
     */
    int maxBufferedMessages() default 0;

    /**
     * Optional resume at messages threshold for future backpressure strategies.
     * Currently informational.
     */
    int resumeAtMessages() default 0;

    /**
     * Batch window (ms) for consumer-side coalescing. Currently informational.
     */
    int batchWindowMs() default 0;

    /**
     * Batch max size for consumer-side coalescing. Currently informational.
     */
    int batchMax() default 0;

    /**
     * Optional send timeout override (ms) applied by helpers during request-reply send.
     */
    long timeoutMs() default 0L;
}

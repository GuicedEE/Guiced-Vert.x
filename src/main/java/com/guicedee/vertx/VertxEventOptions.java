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
}

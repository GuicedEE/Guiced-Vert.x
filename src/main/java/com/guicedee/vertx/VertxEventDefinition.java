package com.guicedee.vertx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for defining Vertx event properties
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface VertxEventDefinition {
    /**
     * @return The address of the event bus to which this consumer will listen
     */
    String value();

    /**
     * @return A set of event options for configuration
     */
    VertxEventOptions options() default @VertxEventOptions;
}

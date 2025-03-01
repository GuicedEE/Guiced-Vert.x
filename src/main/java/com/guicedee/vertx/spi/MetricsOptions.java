package com.guicedee.vertx.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface MetricsOptions
{
    /**
     * If metrics is enabled, default is false
     * @return
     */
    boolean enabled() default false;
}

package com.guicedee.vertx.spi;

import io.vertx.core.ThreadingModel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE,ElementType.TYPE})
public @interface Verticle
{
    ThreadingModel threadingModel() default ThreadingModel.EVENT_LOOP;
    int defaultInstances() default 1;
    boolean ha() default false;
    String workerPoolName();
    int workerPoolSize() default 20;
    long maxWorkerExecuteTime() default 2;
    TimeUnit maxWorkerExecuteTimeUnit() default TimeUnit.MINUTES;

    Capabilities[] capabilities() default {};

    enum Capabilities
    {
        Rest("com.guicedee.guicedservlets.rest"),
        RabbitMQ("com.guicedee.rabbit"),
        Web("com.guicedee.vertx.web"),
        Telemetry("com.guicedee.telemetry"),
        MicroProfileConfig("com.guicedee.microprofile.config"),
        OpenAPI("com.guicedee.guicedservlets.openapi"),
        Swagger("com.guicedee.servlets.swaggerui"),
        Hazelcast("com.guicedee.guicedhazelcast"),
        Cerial("com.guicedee.cerial"),
        Persistence("com.guicedee.guicedpersistence"),
        Sockets("com.guicedee.vertx.websockets"),
        WebServices("com.guicedee.guicedservlets.webservices");

        private final String packageName;

        Capabilities(String packageName)
        {
            this.packageName = packageName;
        }

        public String getPackageName()
        {
            return this.packageName;
        }
    }
}
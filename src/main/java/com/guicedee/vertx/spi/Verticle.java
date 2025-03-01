package com.guicedee.vertx.spi;

import io.vertx.core.ThreadingModel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for configuring a Vert.x Verticle.
 * All configurations and setup from this verticle are configured.
 *
 * To separate different modules in the same application, capabilities can be specified to limit what a verticle loads and configures.
 *
 * Http Server creation is a vertx main function, and exist across verticles
 *
 * <p>A verticle is a fundamental building block in Vert.x applications. It represents
 * a deployable unit that contains the application logic. This annotation provides
 * configuration options for threading models, worker pool settings, high availability,
 * and supported capabilities, ensuring flexible and runtime-adjustable configurations.</p>
 *
 * <p>Features like threading models and worker pool sizes can be customized depending
 * on application requirements (blocking vs non-blocking tasks). Customizations for
 * high availability and integration with specific modules (capabilities) such as Rest APIs
 * or RabbitMQ are also included.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface Verticle {
    /**
     * Specifies the threading model used for the verticle.
     * <ul>
     *     <li>{@link io.vertx.core.ThreadingModel#EVENT_LOOP}: Executes all operations on event loop threads
     *     (non-blocking tasks).</li>
     *     <li>{@link io.vertx.core.ThreadingModel#WORKER}: Allows blocking operations to be handled via a worker thread pool.</li>
     * </ul>
     *
     * @return the threading model (default: {@code ThreadingModel.EVENT_LOOP})
     */
    ThreadingModel threadingModel() default ThreadingModel.EVENT_LOOP;

    /**
     * Defines the number of instances of the verticle to be deployed.
     * For example, to scale a verticle horizontally, more instances can be deployed,
     * which Vert.x runs on different threads or cluster nodes.
     *
     * @return the number of instances (default: 1)
     */
    int defaultInstances() default 1;

    /**
     * Indicates whether the verticle should be configured as high-availability (HA).
     * Setting this to {@code true} makes the verticle part of a cluster and eligible
     * for failover to other nodes in case of failure.
     *
     * @return {@code true} if high availability is enabled, {@code false} otherwise (default: false)
     */
    boolean ha() default false;

    /**
     * Specifies a custom worker pool name to use for worker threading.
     * This name allows you to separate the tasks handled by this worker pool from others.
     *
     * @return the worker pool name (required field, must be specified)
     */
    String workerPoolName();

    /**
     * Specifies the size of the worker pool for this verticle.
     * The worker pool is used to execute blocking tasks without affecting the event loop.
     *
     * @return the number of threads in the worker pool (default: 20)
     */
    int workerPoolSize() default 20;

    /**
     * Defines the maximum time that a worker thread is allowed to execute before
     * being flagged as blocked. This is useful to detect potential performance bottlenecks.
     *
     * @return the maximum execute time for worker threads (default: 2 minutes)
     */
    long maxWorkerExecuteTime() default 2;

    /**
     * Specifies the time unit for the maximum worker execution time.
     *
     * @return the time unit for the maximum execute time (default: {@code TimeUnit.MINUTES})
     */
    TimeUnit maxWorkerExecuteTimeUnit() default TimeUnit.MINUTES;

    /**
     * Specifies the capabilities supported by the verticle.
     * <p>These capabilities represent modules or features integrated with
     * the verticle (e.g., REST APIs, RabbitMQ messaging, etc.). By specifying
     * a capability, its associated package or functionality can be enabled automatically.</p>
     *
     * @return an array of capabilities to enable for the verticle (default: empty array)
     */
    Capabilities[] capabilities() default {};

    /**
     * Enum representing a list of supported capabilities for Vert.x verticles.
     *
     * <p>Each capability corresponds to a specific package or module integration
     * that the verticle supports (e.g., REST APIs, messaging, sockets, etc.).</p>
     */
    enum Capabilities {
        /**
         * REST (API) capability integrated with Vert.x.
         */
        Rest("com.guicedee.guicedservlets.rest"),

        /**
         * RabbitMQ messaging capability.
         */
        RabbitMQ("com.guicedee.rabbit"),

        /**
         * Web capability for HTTP/HTTPS server handling.
         */
        Web("com.guicedee.vertx.web"),

        /**
         * Telemetry capability for distributed tracing and metrics.
         */
        Telemetry("com.guicedee.telemetry"),

        /**
         * MicroProfile Config for configurable application settings.
         */
        MicroProfileConfig("com.guicedee.microprofile.config"),

        /**
         * OpenAPI integration for API documentation and definitions.
         */
        OpenAPI("com.guicedee.guicedservlets.openapi"),

        /**
         * Swagger UI support for interactive API documentation.
         */
        Swagger("com.guicedee.servlets.swaggerui"),

        /**
         * Hazelcast capability for distributed data structures and clustering.
         */
        Hazelcast("com.guicedee.guicedhazelcast"),

        /**
         * Cerial capability for serialization and deserialization.
         */
        Cerial("com.guicedee.cerial"),

        /**
         * Persistence (e.g., database) integration support.
         */
        Persistence("com.guicedee.guicedpersistence"),

        /**
         * WebSockets capability for real-time messaging.
         */
        Sockets("com.guicedee.vertx.websockets"),

        /**
         * Web services capability for SOAP interactions.
         */
        WebServices("com.guicedee.guicedservlets.webservices");

        // Associated package name for the capability
        private final String packageName;

        /**
         * Constructor to associate a package name with the capability.
         *
         * @param packageName the package name
         */
        Capabilities(String packageName) {
            this.packageName = packageName;
        }

        /**
         * Gets the package name associated with the capability.
         *
         * @return the package name
         */
        public String getPackageName() {
            return this.packageName;
        }
    }
}
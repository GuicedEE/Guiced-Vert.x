package com.guicedee.vertx.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <code>
 *     @EventBusOptions(
 *     clusterPublicHost = "192.168.1.100",
 *     clusterPublicPort = 8080,
 *     clusterPingInterval = 15000,
 *     clusterPingReplyInterval = 15000,
 *     reconnectAttempts = 5,
 *     reconnectInterval = 2000,
 *     trustAll = false,
 *     clientAuth = "REQUIRED"
 * )
 * public class ClusteredEventBusConfig {
 *     // Configuration details for working with a clustered Event Bus
 * }
 * </code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface EventBusOptions
{

    /**
     * The public cluster host of the Event Bus, used for network accessibility
     * in clustered environments. A null value uses the default host of the cluster manager.
     *
     * @return the cluster public host (default: {@code null})
     */
    String clusterPublicHost() default "";

    /**
     * The public-facing cluster port of the Event Bus. A value of -1 uses the default port
     * of the cluster manager or the same value as {@code clusterPort}.
     *
     * @return the cluster public port (default: -1)
     */
    int clusterPublicPort() default -1;

    /**
     * The interval, in milliseconds, between cluster ping messages to indicate liveness.
     *
     * @return the cluster ping interval (default: 20000 ms)
     */
    long clusterPingInterval() default 20000;

    /**
     * The interval, in milliseconds, between cluster ping response messages.
     *
     * @return the cluster ping reply interval (default: 20000 ms)
     */
    long clusterPingReplyInterval() default 20000;

    // SERVER CONFIGURATION

    /**
     * The host address of the Event Bus server when clustered. If not specified, it binds to
     * all available network interfaces.
     *
     * @return the server host address (default: bind to all interfaces)
     */
    String host() default "";

    /**
     * The port on which the Event Bus server listens when clustered. If not defined, a random port is assigned.
     *
     * @return the server listen port (default: 0, which assigns a random port)
     */
    int port() default 0;

    /**
     * The maximum number of pending connections the server socket can accept before refusing new connections.
     *
     * @return the server backlog queue size (default: -1, which is system-defined)
     */
    int acceptBacklog() default -1;

    // CLIENT CONFIGURATION

    /**
     * The number of attempts to reconnect a client if the Event Bus connection is lost.
     * A value of 0 disables reconnect attempts.
     *
     * @return the maximum reconnect attempts (default: 0)
     */
    int reconnectAttempts() default 0;

    /**
     * The interval, in milliseconds, between reconnect attempts when reconnecting a client
     * to the Event Bus.
     *
     * @return the reconnect attempt interval (default: 1000 ms)
     */
    long reconnectInterval() default 1000;

    /**
     * The maximum duration, in milliseconds, that client connections will attempt
     * to connect before timing out.
     *
     * @return the connection timeout for clients (default: 60000 ms)
     */
    int connectTimeout() default 60000;

    /**
     * Indicates whether the client should trust all Event Bus server certificates for SSL/TLS
     * connections (ignores certificate validation).
     *
     * @return {@code true} if all certificates are trusted, {@code false} otherwise (default: true)
     */
    boolean trustAll() default true;

    // SECURITY OPTIONS

    /**
     * Configures the client authentication method for Event Bus SSL/TLS connections.
     * Possible values are NONE, REQUEST, or REQUIRED.
     *
     * @return the client authentication method (default: {@link io.vertx.core.http.ClientAuth#NONE})
     */
    String clientAuth() default "NONE";

}

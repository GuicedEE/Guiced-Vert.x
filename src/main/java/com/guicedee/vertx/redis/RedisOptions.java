package com.guicedee.vertx.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares Redis client configuration for the application.
 * Place on a class or {@code package-info.java} to configure a Redis connection.
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders resolved at startup,
 * and every attribute can be overridden via environment variables with the
 * {@code REDIS_} prefix (e.g. {@code REDIS_HOST}, {@code REDIS_PORT}).
 * <p>
 * Multiple Redis connections can be configured by repeating this annotation
 * (using {@link RedisOptionsContainer}).
 *
 * <h2>Environment Variables</h2>
 * <p>
 * Each attribute can be overridden with an environment variable following the pattern:
 * {@code REDIS_{NAME}_{ATTRIBUTE}} where NAME is the uppercase connection name.
 * <ul>
 *   <li>{@code REDIS_CONNECTION_STRING} or {@code REDIS_{name}_CONNECTION_STRING}</li>
 *   <li>{@code REDIS_HOST} / {@code REDIS_PORT} / {@code REDIS_DATABASE}</li>
 *   <li>{@code REDIS_PASSWORD}</li>
 *   <li>{@code REDIS_MODE} — STANDALONE, SENTINEL, CLUSTER, REPLICATION</li>
 *   <li>{@code REDIS_MAX_POOL_SIZE}</li>
 *   <li>{@code REDIS_MAX_WAITING}</li>
 *   <li>{@code REDIS_MASTER_NAME}</li>
 *   <li>{@code REDIS_TLS_ENABLED}</li>
 *   <li>{@code REDIS_TLS_CERT_PATH}</li>
 *   <li>{@code REDIS_TLS_VERIFY_HOST}</li>
 *   <li>{@code REDIS_PROTOCOL} — RESP2, RESP3</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * &#64;RedisOptions(
 *     name = "cache",
 *     connectionString = "${REDIS_URL:redis://localhost:6379/0}",
 *     maxPoolSize = 8
 * )
 * package com.example.app;
 * </pre>
 *
 * <h2>Sentinel Mode</h2>
 * <pre>
 * &#64;RedisOptions(
 *     name = "ha-redis",
 *     mode = RedisOptions.Mode.SENTINEL,
 *     connectionString = "redis://sentinel1:5000",
 *     endpoints = {"redis://sentinel2:5000", "redis://sentinel3:5000"},
 *     masterName = "mymaster"
 * )
 * </pre>
 *
 * <h2>Cluster Mode</h2>
 * <pre>
 * &#64;RedisOptions(
 *     name = "cluster",
 *     mode = RedisOptions.Mode.CLUSTER,
 *     connectionString = "redis://node1:7000",
 *     endpoints = {"redis://node2:7001", "redis://node3:7002"}
 * )
 * </pre>
 *
 * <h2>TLS</h2>
 * <pre>
 * &#64;RedisOptions(
 *     name = "secure",
 *     connectionString = "redis://secure-host:6380",
 *     tlsEnabled = true,
 *     tlsCertPath = "/path/to/ca.crt",
 *     tlsVerifyHost = "HTTPS"
 * )
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(RedisOptionsContainer.class)
public @interface RedisOptions {

    /**
     * Logical name for this Redis connection (used as the Guice {@code @Named} qualifier).
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String name() default "default";

    /**
     * The primary Redis connection string.
     * Supports {@code ${ENV_VAR:default}} placeholders.
     * <p>
     * Example: {@code "redis://localhost:6379/0"} or {@code "${REDIS_URL:redis://localhost:6379}"}
     */
    String connectionString() default "${REDIS_CONNECTION_STRING:redis://localhost:6379}";

    /**
     * Additional endpoints for Sentinel, Cluster, or Replication modes.
     * Each entry supports {@code ${ENV_VAR}} placeholders.
     */
    String[] endpoints() default {};

    /**
     * The Redis client mode.
     */
    Mode mode() default Mode.STANDALONE;

    /**
     * Password for Redis authentication.
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String password() default "${REDIS_PASSWORD:}";

    /**
     * Maximum connection pool size. Defaults to 6.
     */
    int maxPoolSize() default 6;

    /**
     * Maximum number of requests waiting for a pooled connection. Defaults to 24.
     */
    int maxWaitingHandlers() default 24;

    /**
     * Whether TLS/SSL is enabled for the connection.
     */
    boolean tlsEnabled() default false;

    /**
     * Path to the PEM trust certificate for TLS.
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String tlsCertPath() default "${REDIS_TLS_CERT_PATH:}";

    /**
     * Hostname verification algorithm. Empty disables verification, "HTTPS" enables it.
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String tlsVerifyHost() default "${REDIS_TLS_VERIFY_HOST:}";

    /**
     * Preferred Redis protocol version. AUTO means let the client negotiate.
     */
    Protocol protocol() default Protocol.AUTO;

    /**
     * The Sentinel master name. Only used when {@link #mode()} is {@link Mode#SENTINEL}.
     * Supports {@code ${ENV_VAR}} placeholders.
     */
    String masterName() default "${REDIS_MASTER_NAME:mymaster}";

    /**
     * The Redis database number. Defaults to 0.
     */
    int database() default 0;

    /**
     * Whether this is the default Redis binding (injectable without {@code @Named}).
     */
    boolean defaultConnection() default true;

    /**
     * Redis client mode.
     */
    enum Mode {
        STANDALONE,
        SENTINEL,
        CLUSTER,
        REPLICATION
    }

    /**
     * Redis protocol version.
     */
    enum Protocol {
        /** Let the client auto-negotiate (defaults to RESP3 attempt). */
        AUTO,
        RESP2,
        RESP3
    }
}


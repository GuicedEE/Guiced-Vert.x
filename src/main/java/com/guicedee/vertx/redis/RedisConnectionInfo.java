package com.guicedee.vertx.redis;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;


/**
 * Configuration holder for a Vert.x Redis client connection.
 * <p>
 * Supports all 4 Redis client modes: Standalone, Sentinel, Cluster, and Replication.
 *
 * <h2>Environment overrides</h2>
 * Annotation discovery uses global {@code REDIS_*} keys for each definition. Named
 * connections can be configured independently through a concrete {@link RedisModule}.
 * See the generated configuration reference for exact keys and source precedence.
 */
@ToString
@EqualsAndHashCode(of = {"name"})
@Getter
@Setter
@Accessors(chain = true)
public class RedisConnectionInfo {

    /**
     * A logical name for this Redis connection (used as the Guice @Named qualifier).
     */
    private String name = "default";

    /**
     * The primary connection string. Defaults to {@code redis://localhost:6379}.
     */
    private String connectionString = "redis://localhost:6379";

    /**
     * Additional connection strings for sentinel/cluster/replication modes.
     */
    private final List<String> endpoints = new ArrayList<>();

    /**
     * The Redis client type. One of: STANDALONE, SENTINEL, CLUSTER, REPLICATION.
     * Defaults to STANDALONE.
     */
    private RedisMode redisMode = RedisMode.STANDALONE;

    /**
     * The password for authentication. Null means no auth.
     */
    private String password;

    /**
     * The maximum pool size. Defaults to 6.
     */
    private int maxPoolSize = 6;

    /**
     * The maximum number of waiting handlers for a connection. Defaults to 24.
     */
    private int maxWaitingHandlers = 24;

    /**
     * Whether TLS/SSL is enabled. Defaults to false.
     */
    private boolean tlsEnabled = false;

    /**
     * Path to the trust store / certificate for TLS connections.
     */
    private String trustCertPath;

    /**
     * Hostname verification algorithm for TLS. Empty string disables verification, "HTTPS" enables it.
     */
    private String hostnameVerificationAlgorithm = "";

    /**
     * The preferred protocol version. RESP2 or RESP3. Null means use default (RESP3 negotiation).
     */
    private RedisProtocol preferredProtocol;

    /**
     * The master name for Sentinel mode. Defaults to "mymaster".
     */
    private String masterName = "mymaster";

    /**
     * The database number to select on connection. Defaults to -1 (inherit the connection URI).
     */
    private int database = -1;

    /**
     * If this is the default Redis binding (no @Named required).
     */
    private boolean defaultConnection = true;

    /**
     * Adds an additional endpoint for cluster/sentinel/replication modes.
     *
     * @param endpoint a Redis connection string (e.g. redis://host:port)
     * @return this for fluent chaining
     */
    public RedisConnectionInfo addEndpoint(String endpoint) {
        this.endpoints.add(endpoint);
        return this;
    }

    /**
     * Redis client mode enum.
     */
    public enum RedisMode {
        STANDALONE,
        SENTINEL,
        CLUSTER,
        REPLICATION
    }

    /**
     * Redis protocol version enum.
     */
    public enum RedisProtocol {
        RESP2,
        RESP3
    }
}


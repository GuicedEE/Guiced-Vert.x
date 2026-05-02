package com.guicedee.vertx.redis;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.redis.client.ProtocolVersion;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * A Guice module that creates and binds a Vert.x {@link Redis} client and {@link RedisAPI} into the injector.
 * <p>
 * <h3>Usage</h3>
 * <ol>
 *   <li>Subclass this class and implement {@link #getRedisConnectionInfo()}.</li>
 *   <li>Register the subclass as an {@code IGuiceModule} SPI provider.</li>
 *   <li>Inject {@code Redis} or {@code RedisAPI} (optionally with {@code @Named("yourName")}).</li>
 * </ol>
 *
 * <pre>{@code
 * public class MyRedisModule extends RedisModule<MyRedisModule> {
 *     @Override
 *     protected RedisConnectionInfo getRedisConnectionInfo() {
 *         return new RedisConnectionInfo()
 *                 .setName("cache")
 *                 .setConnectionString("redis://localhost:6379/0")
 *                 .setMaxPoolSize(8);
 *     }
 * }
 * }</pre>
 */
@Log4j2
public abstract class RedisModule<J extends RedisModule<J>>
        extends AbstractModule
        implements IGuiceModule<J>, IGuicePreDestroy<J> {

    private final List<Redis> redisClients = new ArrayList<>();

    /**
     * Provides the Redis connection info for this module.
     *
     * @return the connection info describing how to configure the Redis client
     */
    protected abstract RedisConnectionInfo getRedisConnectionInfo();

    @Override
    protected void configure() {
        // First, configure the programmatic connection from getRedisConnectionInfo()
        RedisConnectionInfo info = getRedisConnectionInfo();
        if (info != null) {
            configureConnection(info);
        } else {
            log.debug("No programmatic RedisConnectionInfo provided from {}", getClass().getName());
        }

        // Also configure any annotation-discovered connections
        for (RedisConnectionInfo discovered : RedisPreStartup.getDiscoveredConnections()) {
            configureConnection(discovered);
        }
    }

    private void configureConnection(RedisConnectionInfo info) {

        log.info("🔴 Configuring Redis module '{}' — {} [{}]",
                info.getName(), info.getConnectionString(), info.getRedisMode());

        try {
            Vertx vertx = VertXPreStartup.getVertx();
            if (vertx == null) {
                log.error("❌ Vert.x instance is not available. Cannot create Redis client.");
                return;
            }

            // Build Redis options
            io.vertx.redis.client.RedisOptions options = new io.vertx.redis.client.RedisOptions();

            // Set client type
            switch (info.getRedisMode()) {
                case STANDALONE -> options.setType(RedisClientType.STANDALONE);
                case SENTINEL -> options.setType(RedisClientType.SENTINEL);
                case CLUSTER -> options.setType(RedisClientType.CLUSTER);
                case REPLICATION -> options.setType(RedisClientType.REPLICATION);
            }

            // Set connection string(s)
            options.addConnectionString(info.getConnectionString());
            for (String endpoint : info.getEndpoints()) {
                options.addConnectionString(endpoint);
            }

            // Pool configuration
            options.setMaxPoolSize(info.getMaxPoolSize());
            options.setMaxPoolWaiting(info.getMaxWaitingHandlers());

            // Sentinel master name
            if (info.getRedisMode() == RedisConnectionInfo.RedisMode.SENTINEL) {
                options.setMasterName(info.getMasterName());
            }

            // Password
            if (info.getPassword() != null && !info.getPassword().isBlank()) {
                options.setPassword(info.getPassword());
            }

            // Protocol version
            if (info.getPreferredProtocol() != null) {
                switch (info.getPreferredProtocol()) {
                    case RESP2 -> options.setPreferredProtocolVersion(ProtocolVersion.RESP2);
                    case RESP3 -> options.setPreferredProtocolVersion(ProtocolVersion.RESP3);
                }
            }

            // TLS configuration
            if (info.isTlsEnabled()) {
                NetClientOptions netOptions = options.getNetClientOptions();
                netOptions.setSsl(true);
                if (info.getTrustCertPath() != null && !info.getTrustCertPath().isBlank()) {
                    netOptions.setTrustOptions(new PemTrustOptions().addCertPath(info.getTrustCertPath()));
                }
                netOptions.setHostnameVerificationAlgorithm(
                        info.getHostnameVerificationAlgorithm() != null ? info.getHostnameVerificationAlgorithm() : "");
            }

            // Create the Redis client
            Redis redisClient = Redis.createClient(vertx, options);
            redisClients.add(redisClient);

            // Create RedisAPI wrapper
            RedisAPI redisAPI = RedisAPI.api(redisClient);

            // Bind Redis with @Named qualifier
            bind(Redis.class)
                    .annotatedWith(Names.named(info.getName()))
                    .toInstance(redisClient);

            // Bind RedisAPI with @Named qualifier
            bind(RedisAPI.class)
                    .annotatedWith(Names.named(info.getName()))
                    .toInstance(redisAPI);

            // If this is the default, also bind without @Named
            if (info.isDefaultConnection()) {
                bind(Redis.class).toInstance(redisClient);
                bind(RedisAPI.class).toInstance(redisAPI);
            }

            log.info("✅ Redis client bound as @Named(\"{}\"){}",
                    info.getName(),
                    info.isDefaultConnection() ? " [default]" : "");

        } catch (Throwable t) {
            log.error("❌ Failed to create Redis client for '{}': {}", info.getName(), t.getMessage(), t);
        }
    }

    @Override
    public void onDestroy() {
        for (Redis client : redisClients) {
            try {
                client.close();
                log.info("🛑 Redis client closed");
            } catch (Throwable t) {
                log.debug("⚠️ Redis client close failed: {}", t.getMessage());
            }
        }
        redisClients.clear();
    }

    @Override
    public Integer sortOrder() {
        return 60;
    }
}









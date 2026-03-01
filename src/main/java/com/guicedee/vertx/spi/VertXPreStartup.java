package com.guicedee.vertx.spi;

import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.jackson.DatabindCodec;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Bootstraps the shared Vert.x instance during Guice pre-startup and
 * tears it down on shutdown.
 * <p>
 * Startup responsibilities include applying annotation-driven options,
 * loading {@link VertxConfigurator} contributions, configuring JSON
 * serialization, and pre-registering event types/codecs before verticles
 * are deployed. The Vert.x instance is cached and reused across calls.
 */
@Getter
public class VertXPreStartup implements IGuicePreStartup<VertXPreStartup>, IGuicePreDestroy<VertXPreStartup> {
    private static Vertx vertx;

    public static Optional<io.vertx.core.Verticle> getAssociatedVerticle(Class<?> clazz) {
        String packageName = clazz.getPackageName(); // Get package name of the class
        Map<String, Verticle> verticlePackages = VerticleBuilder.getVerticlePackages(); // Map of package prefixes to Verticles
        // Find a matching verticle for the class's package
        return verticlePackages.entrySet().stream()
                .filter(entry -> packageName.startsWith(entry.getKey())) // Match based on package prefix
                .map(Map.Entry::getValue) // Extract the associated Verticle
                .findFirst(); // Return the first match if found
    }


    @Override
    public List<Future<Boolean>> onStartup() {
        if (vertx == null) {
            // Configure object mapper for JSON serialization
            configureObjectMapper();

            // Initialize the Vertx builder
            VertxBuilder builder = Vertx.builder();

            // Configure Vertx options based on annotations
            configureVertxOptions(builder);

            // Apply additional configurations from ServiceLoader
            applyServiceLoaderConfigurations(builder);

            // Build the Vertx instance
            vertx = builder.build();

            // Scan event definitions early so codec registry has full type info
            // This populates eventConsumerDefinitions and eventConsumerClass maps
            VertxEventRegistry.scanAndRegisterEvents();

            // Register dynamic codecs for all event types up-front
            CodecRegistry.createAndRegisterCodecsForAllEventTypes(vertx);

            // Deploy verticles - each verticle will register its assigned consumers via VertxConsumersStartup
            // The returned futures are collected so the startup lifecycle can wait for them
            new VerticleBuilder().findVerticles();

            // Return the verticle deployment futures so the lifecycle waits for them to complete
            // before moving to post-startups
            Map<String, Future<?>> verticleFutures = VerticleBuilder.getVerticleFutures();
            if (verticleFutures != null && !verticleFutures.isEmpty()) {
                return verticleFutures.values().stream()
                        .map(f -> f.map(v -> true))
                        .map(f -> (Future<Boolean>) f)
                        .toList();
            }
        }
        return List.of(Future.succeededFuture(true));
    }

    private void configureObjectMapper() {
        IJsonRepresentation.configureObjectMapper(DatabindCodec.mapper());
    }

    private void configureVertxOptions(VertxBuilder builder) {
        // Process the @VertX annotation
        var vertxStaticConfig = IGuiceContext.instance().getScanResult().getClassesWithAnnotation(VertX.class);
        if (vertxStaticConfig.size() > 1) {
            throw new RuntimeException("Only one VertX class may be defined");
        }
        if (vertxStaticConfig.size() == 1) {
            var clazz = vertxStaticConfig.getFirst().loadClass();
            VertX annotation = clazz.getDeclaredAnnotation(VertX.class);
            if (annotation == null) {
                throw new RuntimeException("Could not read VertX annotation from class");
            }
            VertX wrappedVertX = new VertX() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return VertX.class;
                }

                @Override
                public int eventLoopPoolSize() {
                    return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENT_LOOP_POOL_SIZE", String.valueOf(annotation.eventLoopPoolSize())));
                }

                @Override
                public int workerPoolSize() {
                    return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_WORKER_POOL_SIZE", String.valueOf(annotation.workerPoolSize())));
                }

                @Override
                public long blockedThreadCheckInterval() {
                    return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_BLOCKED_THREAD_CHECK_INTERVAL", String.valueOf(annotation.blockedThreadCheckInterval())));
                }

                @Override
                public java.util.concurrent.TimeUnit blockedThreadCheckIntervalTimeUnit() {
                    return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_BLOCKED_THREAD_CHECK_INTERVAL_TIME_UNIT", annotation.blockedThreadCheckIntervalTimeUnit().name()));
                }

                @Override
                public long maxEventLoopExecuteTime() {
                    return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_EVENT_LOOP_EXECUTE_TIME", String.valueOf(annotation.maxEventLoopExecuteTime())));
                }

                @Override
                public java.util.concurrent.TimeUnit maxEventLoopExecuteTimeUnit() {
                    return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT", annotation.maxEventLoopExecuteTimeUnit().name()));
                }

                @Override
                public long maxWorkerExecuteTime() {
                    return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_WORKER_EXECUTE_TIME", String.valueOf(annotation.maxWorkerExecuteTime())));
                }

                @Override
                public java.util.concurrent.TimeUnit maxWorkerExecuteTimeUnit() {
                    return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_WORKER_EXECUTE_TIME_UNIT", annotation.maxWorkerExecuteTimeUnit().name()));
                }

                @Override
                public int internalBlockingPoolSize() {
                    return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_INTERNAL_BLOCKING_POOL_SIZE", String.valueOf(annotation.internalBlockingPoolSize())));
                }

                @Override
                public boolean haEnabled() {
                    return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_HA_ENABLED", String.valueOf(annotation.haEnabled())));
                }

                @Override
                public int quorumSize() {
                    return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_QUORUM_SIZE", String.valueOf(annotation.quorumSize())));
                }

                @Override
                public long warningExceptionTime() {
                    return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_WARNING_EXCEPTION_TIME", String.valueOf(annotation.warningExceptionTime())));
                }

                @Override
                public java.util.concurrent.TimeUnit warningExceptionTimeUnit() {
                    return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_WARNING_EXCEPTION_TIME_UNIT", annotation.warningExceptionTimeUnit().name()));
                }

                @Override
                public boolean preferNativeTransport() {
                    return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_PREFER_NATIVE_TRANSPORT", String.valueOf(annotation.preferNativeTransport())));
                }

                @Override
                public boolean useDaemonThreads() {
                    return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_USE_DAEMON_THREADS", String.valueOf(annotation.useDaemonThreads())));
                }

                @Override
                public boolean disableTCCL() {
                    return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_DISABLE_TCCL", String.valueOf(annotation.disableTCCL())));
                }
            };
            applyVertxAnnotation(builder, wrappedVertX);
        }

        // Process other configuration annotations
        processMetricsOptions(builder);
        processFileSystemOptions(builder);
        processEventBusOptions(builder);
        processAddressResolverOptions(builder);
    }

    private void applyVertxAnnotation(VertxBuilder builder, VertX annotation) {
        builder.with(new VertxOptions()
                .setEventLoopPoolSize(annotation.eventLoopPoolSize())
                .setWorkerPoolSize(annotation.workerPoolSize())
                .setBlockedThreadCheckInterval(annotation.blockedThreadCheckInterval())
                .setBlockedThreadCheckIntervalUnit(annotation.blockedThreadCheckIntervalTimeUnit())
                .setMaxEventLoopExecuteTime(annotation.maxEventLoopExecuteTime())
                .setMaxEventLoopExecuteTimeUnit(annotation.maxEventLoopExecuteTimeUnit())
                .setMaxWorkerExecuteTime(annotation.maxWorkerExecuteTime())
                .setMaxWorkerExecuteTimeUnit(annotation.maxWorkerExecuteTimeUnit())
                .setInternalBlockingPoolSize(annotation.internalBlockingPoolSize())
                .setPreferNativeTransport(annotation.preferNativeTransport())
                .setHAEnabled(annotation.haEnabled())
                .setQuorumSize(annotation.quorumSize())
                .setWarningExceptionTime(annotation.warningExceptionTime())
                .setDisableTCCL(annotation.disableTCCL())
        );
    }

    private void processMetricsOptions(VertxBuilder builder) {
        var metricsConfig = IGuiceContext.instance().getScanResult().getClassesWithAnnotation(MetricsOptions.class);
        if (metricsConfig.size() == 1) {
            var clazz = metricsConfig.getFirst().loadClass();
            MetricsOptions metricsAnnotation = clazz.getDeclaredAnnotation(MetricsOptions.class);
            if (metricsAnnotation != null) {
                MetricsOptions wrappedMetrics = new MetricsOptions() {
                    @Override
                    public Class<? extends java.lang.annotation.Annotation> annotationType() {
                        return MetricsOptions.class;
                    }

                    @Override
                    public boolean enabled() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_METRICS_ENABLED", String.valueOf(metricsAnnotation.enabled())));
                    }
                };
                builder.with(new VertxOptions()
                        .setMetricsOptions(new io.vertx.core.metrics.MetricsOptions().setEnabled(wrappedMetrics.enabled()))
                );
            }
        }
    }

    private void processFileSystemOptions(VertxBuilder builder) {
        var fileSystemConfig = IGuiceContext.instance().getScanResult().getClassesWithAnnotation(FileSystemOptions.class);
        if (fileSystemConfig.size() == 1) {
            var clazz = fileSystemConfig.getFirst().loadClass();
            FileSystemOptions fileSystemAnnotation = clazz.getDeclaredAnnotation(FileSystemOptions.class);
            if (fileSystemAnnotation != null) {
                FileSystemOptions wrappedFS = new FileSystemOptions() {
                    @Override
                    public Class<? extends java.lang.annotation.Annotation> annotationType() {
                        return FileSystemOptions.class;
                    }

                    @Override
                    public boolean classPathResolvingEnabled() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_CLASSPATH_RESOLVING", String.valueOf(fileSystemAnnotation.classPathResolvingEnabled())));
                    }

                    @Override
                    public boolean fileCachingEnabled() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_FILE_CACHING", String.valueOf(fileSystemAnnotation.fileCachingEnabled())));
                    }

                    @Override
                    public String fileCacheDir() {
                        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_FILE_CACHE_DIR", fileSystemAnnotation.fileCacheDir());
                    }
                };
                builder.with(new VertxOptions()
                        .setFileSystemOptions(new io.vertx.core.file.FileSystemOptions()
                                .setClassPathResolvingEnabled(wrappedFS.classPathResolvingEnabled())
                                .setFileCachingEnabled(wrappedFS.fileCachingEnabled())
                                .setFileCacheDir(wrappedFS.fileCacheDir())
                        )
                );
            }
        }
    }

    private void processEventBusOptions(VertxBuilder builder) {
        var eventBusConfig = IGuiceContext.instance().getScanResult().getClassesWithAnnotation(EventBusOptions.class);
        if (eventBusConfig.size() == 1) {
            var clazz = eventBusConfig.getFirst().loadClass();
            EventBusOptions eventBusAnnotation = clazz.getDeclaredAnnotation(EventBusOptions.class);
            if (eventBusAnnotation != null) {
                EventBusOptions wrappedEB = new EventBusOptions() {
                    @Override
                    public Class<? extends java.lang.annotation.Annotation> annotationType() {
                        return EventBusOptions.class;
                    }

                    @Override
                    public String clusterPublicHost() {
                        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PUBLIC_HOST", eventBusAnnotation.clusterPublicHost());
                    }

                    @Override
                    public int clusterPublicPort() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PUBLIC_PORT", String.valueOf(eventBusAnnotation.clusterPublicPort())));
                    }

                    @Override
                    public long clusterPingInterval() {
                        return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PING_INTERVAL", String.valueOf(eventBusAnnotation.clusterPingInterval())));
                    }

                    @Override
                    public long clusterPingReplyInterval() {
                        return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PING_REPLY_INTERVAL", String.valueOf(eventBusAnnotation.clusterPingReplyInterval())));
                    }

                    @Override
                    public String host() {
                        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_HOST", eventBusAnnotation.host());
                    }

                    @Override
                    public int port() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_PORT", String.valueOf(eventBusAnnotation.port())));
                    }

                    @Override
                    public int acceptBacklog() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_ACCEPT_BACKLOG", String.valueOf(eventBusAnnotation.acceptBacklog())));
                    }

                    @Override
                    public int reconnectAttempts() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_RECONNECT_ATTEMPTS", String.valueOf(eventBusAnnotation.reconnectAttempts())));
                    }

                    @Override
                    public long reconnectInterval() {
                        return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_RECONNECT_INTERVAL", String.valueOf(eventBusAnnotation.reconnectInterval())));
                    }

                    @Override
                    public int connectTimeout() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CONNECT_TIMEOUT", String.valueOf(eventBusAnnotation.connectTimeout())));
                    }

                    @Override
                    public boolean trustAll() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_TRUST_ALL", String.valueOf(eventBusAnnotation.trustAll())));
                    }

                    @Override
                    public String clientAuth() {
                        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLIENT_AUTH", eventBusAnnotation.clientAuth());
                    }
                };
                builder.with(new VertxOptions()
                        .setEventBusOptions(new io.vertx.core.eventbus.EventBusOptions()
                                .setClusterPublicHost(wrappedEB.clusterPublicHost())
                                .setClusterPublicPort(wrappedEB.clusterPublicPort())
                                .setClusterPingInterval(wrappedEB.clusterPingInterval())
                                .setClusterPingReplyInterval(wrappedEB.clusterPingReplyInterval())
                                .setHost(wrappedEB.host())
                                .setPort(wrappedEB.port())
                                .setAcceptBacklog(wrappedEB.acceptBacklog())
                                .setReconnectAttempts(wrappedEB.reconnectAttempts())
                                .setReconnectInterval(wrappedEB.reconnectInterval())
                                .setConnectTimeout(wrappedEB.connectTimeout())
                                .setTrustAll(wrappedEB.trustAll())
                                .setClientAuth(io.vertx.core.http.ClientAuth.valueOf(wrappedEB.clientAuth()))
                        )
                );
            }
        }
    }

    private void processAddressResolverOptions(VertxBuilder builder) {
        var addressResolverConfig = IGuiceContext.instance().getScanResult().getClassesWithAnnotation(AddressResolverOptions.class);
        if (addressResolverConfig.size() == 1) {
            var clazz = addressResolverConfig.getFirst().loadClass();
            AddressResolverOptions addressResolverAnnotation = clazz.getDeclaredAnnotation(AddressResolverOptions.class);
            if (addressResolverAnnotation != null) {
                AddressResolverOptions wrappedAR = new AddressResolverOptions() {
                    @Override
                    public Class<? extends java.lang.annotation.Annotation> annotationType() {
                        return AddressResolverOptions.class;
                    }

                    @Override
                    public String hostsPath() {
                        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_HOSTS_PATH", addressResolverAnnotation.hostsPath());
                    }

                    @Override
                    public int hostsRefreshPeriod() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_HOSTS_REFRESH_PERIOD", String.valueOf(addressResolverAnnotation.hostsRefreshPeriod())));
                    }

                    @Override
                    public String[] servers() {
                        String serversStr = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_SERVERS", String.join(",", addressResolverAnnotation.servers()));
                        return serversStr.isBlank() ? new String[0] : serversStr.split(",");
                    }

                    @Override
                    public boolean rotateServers() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_ROTATE_SERVERS", String.valueOf(addressResolverAnnotation.rotateServers())));
                    }

                    @Override
                    public int cacheMinTimeToLive() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_CACHE_MIN_TTL", String.valueOf(addressResolverAnnotation.cacheMinTimeToLive())));
                    }

                    @Override
                    public int cacheMaxTimeToLive() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_CACHE_MAX_TTL", String.valueOf(addressResolverAnnotation.cacheMaxTimeToLive())));
                    }

                    @Override
                    public int cacheNegativeTimeToLive() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_CACHE_NEGATIVE_TTL", String.valueOf(addressResolverAnnotation.cacheNegativeTimeToLive())));
                    }

                    @Override
                    public long queryTimeout() {
                        return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_QUERY_TIMEOUT", String.valueOf(addressResolverAnnotation.queryTimeout())));
                    }

                    @Override
                    public int maxQueries() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_MAX_QUERIES", String.valueOf(addressResolverAnnotation.maxQueries())));
                    }

                    @Override
                    public boolean rdFlag() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_RD_FLAG", String.valueOf(addressResolverAnnotation.rdFlag())));
                    }

                    @Override
                    public String[] searchDomains() {
                        String domainsStr = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_SEARCH_DOMAINS", String.join(",", addressResolverAnnotation.searchDomains()));
                        return domainsStr.isBlank() ? new String[0] : domainsStr.split(",");
                    }

                    @Override
                    public int ndots() {
                        return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_NDOTS", String.valueOf(addressResolverAnnotation.ndots())));
                    }

                    @Override
                    public boolean optResourceEnabled() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_OPT_RESOURCE_ENABLED", String.valueOf(addressResolverAnnotation.optResourceEnabled())));
                    }

                    @Override
                    public boolean roundRobinInetAddress() {
                        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_ROUND_ROBIN", String.valueOf(addressResolverAnnotation.roundRobinInetAddress())));
                    }
                };
                builder.with(new VertxOptions()
                        .setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions()
                                .setHostsPath(wrappedAR.hostsPath())
                                .setHostsRefreshPeriod(wrappedAR.hostsRefreshPeriod())
                                .setServers(List.of(wrappedAR.servers()))
                                .setRotateServers(wrappedAR.rotateServers())
                                .setCacheMinTimeToLive(wrappedAR.cacheMinTimeToLive())
                                .setCacheMaxTimeToLive(wrappedAR.cacheMaxTimeToLive())
                                .setCacheNegativeTimeToLive(wrappedAR.cacheNegativeTimeToLive())
                                .setQueryTimeout(wrappedAR.queryTimeout())
                                .setMaxQueries(wrappedAR.maxQueries())
                                .setRdFlag(wrappedAR.rdFlag())
                                .setSearchDomains(List.of(wrappedAR.searchDomains()))
                                .setNdots(wrappedAR.ndots())
                                .setOptResourceEnabled(wrappedAR.optResourceEnabled())
                                .setRoundRobinInetAddress(wrappedAR.roundRobinInetAddress())
                        )
                );
            }
        }
    }

    private void applyServiceLoaderConfigurations(VertxBuilder builder) {
        ServiceLoader<VertxConfigurator> load = ServiceLoader.load(VertxConfigurator.class);
        for (VertxConfigurator a : load) {
            builder = a.builder(builder);
        }
    }


    public static Vertx getVertx() {
        if (vertx == null) {
            new VertXPreStartup().onStartup();
        }
        return vertx;
    }

    @Override
    public void onDestroy() {
        vertx.close();
    }

    @Override
    public Integer sortOrder() {
        return Integer.MIN_VALUE + 38;
    }
}

package com.guicedee.vertx.spi;

import com.guicedee.client.utils.Discovery;

import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;

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
    private static volatile Vertx vertx;
    private static Future<Void> closing;
    private static Future<Vertx> starting;
    private static Future<Void> resourceClosing;

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
        final io.vertx.core.Promise<Vertx> resource;
        synchronized (VertXPreStartup.class) {
            if (closing != null) {
                if (!closing.isComplete()) return List.of(Future.failedFuture("Vertx is still stopping"));
                closing = null;starting = null;resourceClosing = null;
            }
            if (starting != null) return List.of(starting.map(true));
            if (vertx != null) return List.of(Future.succeededFuture(true));
            resource = io.vertx.core.Promise.promise();starting = resource.future();
        }
        try {
            Class.forName("com.guicedee.client.scopes.CallScoper");
            VertxBuilder builder = Vertx.builder();
            VertxOptions options = configureVertxOptions();
            builder = applyServiceLoaderConfigurations(builder, options);
            Future<Vertx> created = clusterMode ? builder.buildClustered() : Future.succeededFuture(builder.build());
            created.onComplete(result -> {
                if (result.failed()) { resource.tryFail(result.cause());return; }
                Vertx instance = result.result();
                synchronized (VertXPreStartup.class) { vertx = instance; }
                try {
                    VertxEventRegistry.scanAndRegisterEvents();
                    CodecRegistry.createAndRegisterCodecsForAllEventTypes(instance);
                    resource.tryComplete(instance);
                } catch (Throwable failure) {
                    closeInstance(instance).onComplete(closed -> resource.tryFail(failure));
                }
            });
        } catch (Throwable failure) { resource.tryFail(failure); }
        return List.of(resource.future().map(true));
    }

    private VertxOptions configureVertxOptions() {
        VertxOptions options = new VertxOptions();
        // Process the @VertX annotation
        var vertxStaticConfig = Discovery.annotations(VertX.class);
        if (vertxStaticConfig.size() > 1) {
            throw new RuntimeException("Only one VertX configuration may be defined");
        }
        VertX annotation = vertxStaticConfig.isEmpty() ? null : vertxStaticConfig.getFirst();
        VertX wrappedVertX = new VertX() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return VertX.class;
            }

            @Override
            public int eventLoopPoolSize() {
                return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENT_LOOP_POOL_SIZE", String.valueOf((annotation == null || annotation.eventLoopPoolSize() == -1 ? options.getEventLoopPoolSize() : annotation.eventLoopPoolSize()))));
            }

            @Override
            public int workerPoolSize() {
                return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_WORKER_POOL_SIZE", String.valueOf((annotation == null ? options.getWorkerPoolSize() : annotation.workerPoolSize()))));
            }

            @Override
            public long blockedThreadCheckInterval() {
                return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_BLOCKED_THREAD_CHECK_INTERVAL", String.valueOf((annotation == null ? options.getBlockedThreadCheckInterval() : annotation.blockedThreadCheckInterval()))));
            }

            @Override
            public java.util.concurrent.TimeUnit blockedThreadCheckIntervalTimeUnit() {
                return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_BLOCKED_THREAD_CHECK_INTERVAL_TIME_UNIT", (annotation == null ? options.getBlockedThreadCheckIntervalUnit() : annotation.blockedThreadCheckIntervalTimeUnit()).name()));
            }

            @Override
            public long maxEventLoopExecuteTime() {
                return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_EVENT_LOOP_EXECUTE_TIME", String.valueOf((annotation == null ? options.getMaxEventLoopExecuteTime() : annotation.maxEventLoopExecuteTime()))));
            }

            @Override
            public java.util.concurrent.TimeUnit maxEventLoopExecuteTimeUnit() {
                return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT", (annotation == null ? options.getMaxEventLoopExecuteTimeUnit() : annotation.maxEventLoopExecuteTimeUnit()).name()));
            }

            @Override
            public long maxWorkerExecuteTime() {
                return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_WORKER_EXECUTE_TIME", String.valueOf((annotation == null ? options.getMaxWorkerExecuteTime() : annotation.maxWorkerExecuteTime()))));
            }

            @Override
            public java.util.concurrent.TimeUnit maxWorkerExecuteTimeUnit() {
                return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_MAX_WORKER_EXECUTE_TIME_UNIT", (annotation == null ? options.getMaxWorkerExecuteTimeUnit() : annotation.maxWorkerExecuteTimeUnit()).name()));
            }

            @Override
            public int internalBlockingPoolSize() {
                return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_INTERNAL_BLOCKING_POOL_SIZE", String.valueOf((annotation == null ? options.getInternalBlockingPoolSize() : annotation.internalBlockingPoolSize()))));
            }

            @Override
            public boolean haEnabled() {
                return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_HA_ENABLED", String.valueOf((annotation == null ? options.isHAEnabled() : annotation.haEnabled()))));
            }

            @Override
            public String haGroup() {
                return com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_HA_GROUP", annotation == null ? options.getHAGroup() : annotation.haGroup());
            }

            @Override
            public int quorumSize() {
                return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_QUORUM_SIZE", String.valueOf((annotation == null ? options.getQuorumSize() : annotation.quorumSize()))));
            }

            @Override
            public long warningExceptionTime() {
                return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_WARNING_EXCEPTION_TIME", String.valueOf((annotation == null ? options.getWarningExceptionTime() : annotation.warningExceptionTime()))));
            }

            @Override
            public java.util.concurrent.TimeUnit warningExceptionTimeUnit() {
                return java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_WARNING_EXCEPTION_TIME_UNIT", (annotation == null ? options.getWarningExceptionTimeUnit() : annotation.warningExceptionTimeUnit()).name()));
            }

            @Override
            public boolean preferNativeTransport() {
                return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_PREFER_NATIVE_TRANSPORT", String.valueOf((annotation == null ? options.getPreferNativeTransport() : annotation.preferNativeTransport()))));
            }

            @Override
            public boolean useDaemonThreads() {
                return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_USE_DAEMON_THREADS", String.valueOf((annotation == null ? Boolean.TRUE.equals(options.getUseDaemonThread()) : annotation.useDaemonThreads()))));
            }

            @Override
            public boolean disableTCCL() {
                return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_DISABLE_TCCL", String.valueOf((annotation == null ? options.getDisableTCCL() : annotation.disableTCCL()))));
            }
        };
        applyVertxAnnotation(options, wrappedVertX);

        // Process other configuration annotations
        processMetricsOptions(options);
        processFileSystemOptions(options);
        processEventBusOptions(options);
        processAddressResolverOptions(options);
        return options;
    }

    private void applyVertxAnnotation(VertxOptions options, VertX annotation) {
        options
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
                .setHAGroup(annotation.haGroup())
                .setUseDaemonThread(annotation.useDaemonThreads())
                .setWarningExceptionTime(annotation.warningExceptionTime())
                .setWarningExceptionTimeUnit(annotation.warningExceptionTimeUnit())
                .setDisableTCCL(annotation.disableTCCL());
    }

    private void processMetricsOptions(VertxOptions options) {
        MetricsOptions annotation = singleConfiguration(MetricsOptions.class);
        var resolved = new io.vertx.core.metrics.MetricsOptions();
        resolved.setEnabled(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_METRICS_ENABLED", String.valueOf(annotation == null ? resolved.isEnabled() : annotation.enabled()))));
        options.setMetricsOptions(resolved);
    }

    private void processFileSystemOptions(VertxOptions options) {
        FileSystemOptions annotation = singleConfiguration(FileSystemOptions.class);
        var resolved = new io.vertx.core.file.FileSystemOptions();
        resolved.setClassPathResolvingEnabled(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_CLASSPATH_RESOLVING", String.valueOf(annotation == null ? resolved.isClassPathResolvingEnabled() : annotation.classPathResolvingEnabled()))));
        resolved.setFileCachingEnabled(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_FILE_CACHING", String.valueOf(annotation == null ? resolved.isFileCachingEnabled() : annotation.fileCachingEnabled()))));
        String fileCacheDir = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_FILE_CACHE_DIR", annotation == null ? "" : annotation.fileCacheDir());
        if (!fileCacheDir.isBlank()) resolved.setFileCacheDir(fileCacheDir);
        resolved.setFileCacheDirAsExactPath(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_FILESYSTEM_FILE_CACHE_DIR_AS_EXACT_PATH", String.valueOf(annotation == null ? resolved.isFileCacheDirAsExactPath() : annotation.fileCacheDirAsExactPath()))));
        options.setFileSystemOptions(resolved);
    }

    private void processEventBusOptions(VertxOptions options) {
        EventBusOptions annotation = singleConfiguration(EventBusOptions.class);
        var resolved = new io.vertx.core.eventbus.EventBusOptions();
        String clusterPublicHost = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PUBLIC_HOST", annotation == null ? "" : annotation.clusterPublicHost());
        if (!clusterPublicHost.isBlank()) resolved.setClusterPublicHost(clusterPublicHost);
        int clusterPublicPort = Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PUBLIC_PORT", String.valueOf(annotation == null ? resolved.getClusterPublicPort() : annotation.clusterPublicPort())));
        if (clusterPublicPort != -1) resolved.setClusterPublicPort(clusterPublicPort);
        resolved.setClusterPingInterval(Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PING_INTERVAL", String.valueOf(annotation == null ? resolved.getClusterPingInterval() : annotation.clusterPingInterval()))));
        resolved.setClusterPingReplyInterval(Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLUSTER_PING_REPLY_INTERVAL", String.valueOf(annotation == null ? resolved.getClusterPingReplyInterval() : annotation.clusterPingReplyInterval()))));
        String host = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_HOST", annotation == null ? "" : annotation.host());
        if (!host.isBlank()) resolved.setHost(host);
        resolved.setPort(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_PORT", String.valueOf(annotation == null ? resolved.getPort() : annotation.port()))));
        resolved.setAcceptBacklog(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_ACCEPT_BACKLOG", String.valueOf(annotation == null ? resolved.getAcceptBacklog() : annotation.acceptBacklog()))));
        resolved.setReconnectAttempts(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_RECONNECT_ATTEMPTS", String.valueOf(annotation == null ? resolved.getReconnectAttempts() : annotation.reconnectAttempts()))));
        resolved.setReconnectInterval(Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_RECONNECT_INTERVAL", String.valueOf(annotation == null ? resolved.getReconnectInterval() : annotation.reconnectInterval()))));
        resolved.setConnectTimeout(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CONNECT_TIMEOUT", String.valueOf(annotation == null ? resolved.getConnectTimeout() : annotation.connectTimeout()))));
        resolved.setTrustAll(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_TRUST_ALL", String.valueOf(annotation == null ? resolved.isTrustAll() : annotation.trustAll()))));
        resolved.setClientAuth(io.vertx.core.http.ClientAuth.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_EVENTBUS_CLIENT_AUTH", annotation == null ? resolved.getClientAuth().name() : annotation.clientAuth())));
        options.setEventBusOptions(resolved);
    }

    private void processAddressResolverOptions(VertxOptions options) {
        AddressResolverOptions annotation = singleConfiguration(AddressResolverOptions.class);
        var resolved = new io.vertx.core.dns.AddressResolverOptions();
        String hostsPath = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_HOSTS_PATH", annotation == null ? "" : annotation.hostsPath());
        if (!hostsPath.isBlank()) resolved.setHostsPath(hostsPath);
        resolved.setHostsRefreshPeriod(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_HOSTS_REFRESH_PERIOD", String.valueOf(annotation == null ? resolved.getHostsRefreshPeriod() : annotation.hostsRefreshPeriod()))));
        resolved.setHostsRefreshPeriodUnit(java.util.concurrent.TimeUnit.valueOf(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_HOSTS_REFRESH_PERIOD_UNIT", annotation == null ? java.util.concurrent.TimeUnit.MILLISECONDS.name() : annotation.hostsRefreshPeriodUnit().name())));
        String servers = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_SERVERS", ",", annotation == null ? new String[0] : annotation.servers());
        if (!servers.isBlank()) resolved.setServers(java.util.Arrays.stream(servers.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList());
        resolved.setRotateServers(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_ROTATE_SERVERS", String.valueOf(annotation == null ? resolved.isRotateServers() : annotation.rotateServers()))));
        resolved.setCacheMinTimeToLive(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_CACHE_MIN_TTL", String.valueOf(annotation == null ? resolved.getCacheMinTimeToLive() : annotation.cacheMinTimeToLive()))));
        resolved.setCacheMaxTimeToLive(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_CACHE_MAX_TTL", String.valueOf(annotation == null ? resolved.getCacheMaxTimeToLive() : annotation.cacheMaxTimeToLive()))));
        resolved.setCacheNegativeTimeToLive(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_CACHE_NEGATIVE_TTL", String.valueOf(annotation == null ? resolved.getCacheNegativeTimeToLive() : annotation.cacheNegativeTimeToLive()))));
        resolved.setQueryTimeout(Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_QUERY_TIMEOUT", String.valueOf(annotation == null ? resolved.getQueryTimeout() : annotation.queryTimeout()))));
        resolved.setMaxQueries(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_MAX_QUERIES", String.valueOf(annotation == null ? resolved.getMaxQueries() : annotation.maxQueries()))));
        resolved.setRdFlag(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_RD_FLAG", String.valueOf(annotation == null ? resolved.getRdFlag() : annotation.rdFlag()))));
        String searchDomains = com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_SEARCH_DOMAINS", ",", annotation == null ? new String[0] : annotation.searchDomains());
        if (!searchDomains.isBlank()) resolved.setSearchDomains(java.util.Arrays.stream(searchDomains.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList());
        resolved.setNdots(Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_NDOTS", String.valueOf(annotation == null || annotation.ndots() == -1 ? resolved.getNdots() : annotation.ndots()))));
        resolved.setOptResourceEnabled(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_OPT_RESOURCE_ENABLED", String.valueOf(annotation == null ? resolved.isOptResourceEnabled() : annotation.optResourceEnabled()))));
        resolved.setRoundRobinInetAddress(Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("VERTX_ADDR_RESOLVER_ROUND_ROBIN", String.valueOf(annotation == null ? resolved.isRoundRobinInetAddress() : annotation.roundRobinInetAddress()))));
        options.setAddressResolverOptions(resolved);
    }

    private static <A extends java.lang.annotation.Annotation> A singleConfiguration(Class<A> type) {
        var annotations = Discovery.annotations(type);
        if (annotations.size() > 1) throw new IllegalStateException("Only one " + type.getSimpleName() + " configuration may be defined");
        return annotations.isEmpty() ? null : annotations.getFirst();
    }

    private boolean clusterMode = false;

    private VertxBuilder applyServiceLoaderConfigurations(VertxBuilder builder, VertxOptions options) {
        clusterMode = false;
        var configurations = ServiceLoader.load(VertxConfigurator.class).stream().map(ServiceLoader.Provider::get).toList();
        for (VertxConfigurator configuration : configurations) options = configuration.options(options);
        builder.with(options);
        for (VertxConfigurator a : configurations) {
            if (a instanceof ClusterVertxConfigurator cluster) {
                if (!cluster.enabled()) continue;
                if (clusterMode) throw new IllegalStateException("Multiple enabled Vert.x cluster managers");
                clusterMode = true;
            }
            builder = a.builder(builder);
        }
        return builder;
    }


    public static synchronized boolean ready() {
        return closing == null && starting != null && starting.succeeded() && vertx != null;
    }

    public static synchronized Vertx getVertx() {
        if (closing != null) throw new IllegalStateException("Vertx is stopped");
        if (vertx == null) {
            new VertXPreStartup().onStartup();
        }
        return vertx;
    }

    /** Starts one asynchronous close; explicit startup is required before a stopped runtime can be reused. */
    private static synchronized Future<Void> closeInstance(Vertx instance) {
        if (resourceClosing == null) {
            try { resourceClosing = instance.close(); }
            catch (Throwable failure) { resourceClosing = Future.failedFuture(failure); }
        }
        return resourceClosing;
    }

    public static synchronized Future<Void> closeVertx() {
        if (closing != null) return closing;
        var completion = io.vertx.core.Promise.<Void>promise();
        closing = completion.future();
        Vertx retained = vertx;
        Future<Void> stopped;
        try {
            // A clustered or synchronously delayed startup can produce its resource
            // after shutdown begins. Await that result and close it before completing.
            stopped = starting == null
                    ? (retained == null ? Future.succeededFuture() : closeInstance(retained))
                    : starting.transform(result -> {
                        synchronized (VertXPreStartup.class) {
                            if (result.succeeded()) return closeInstance(result.result());
                            if (resourceClosing != null) return resourceClosing;
                            return vertx == null ? Future.succeededFuture() : closeInstance(vertx);
                        }
                    });
        }
        catch (Throwable failure) { stopped = Future.failedFuture(failure); }
        stopped.onComplete(result -> {
            synchronized (VertXPreStartup.class) {
                vertx = null;
                VertxEventRegistry.reset();VerticleBuilder.reset();CodecRegistry.reset();
                completion.handle(result);
            }
        });
        return completion.future();
    }
    @Override public void onDestroy() {
        if (io.vertx.core.Context.isOnEventLoopThread()) throw new IllegalStateException("Shutdown cannot block an event loop");
        try { closeVertx().toCompletionStage().toCompletableFuture().get(20, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt();throw new IllegalStateException("Vertx shutdown interrupted"); }
        catch (Exception failed) { throw new IllegalStateException("Vertx shutdown failed"); }
    }
    @Override public Integer shutdownSortOrder() { return Integer.MAX_VALUE - 200; }

    @Override
    public Integer sortOrder() {
        return Integer.MIN_VALUE + 38;
    }
}

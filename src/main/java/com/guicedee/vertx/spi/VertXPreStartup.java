package com.guicedee.vertx.spi;

import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedinjection.interfaces.IGuicePreStartup;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
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

@Getter
public class VertXPreStartup implements IGuicePreStartup<VertXPreStartup>, IGuicePreDestroy<VertXPreStartup>
{
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

            // Initialize the VertxEventRegistry
            VertxEventRegistry.scanAndRegisterEvents();
            VertxEventRegistry.registerEventConsumers();
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
            applyVertxAnnotation(builder, annotation);
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
                builder.with(new VertxOptions()
                        .setMetricsOptions(new io.vertx.core.metrics.MetricsOptions().setEnabled(metricsAnnotation.enabled()))
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
                builder.with(new VertxOptions()
                        .setFileSystemOptions(new io.vertx.core.file.FileSystemOptions()
                                .setClassPathResolvingEnabled(fileSystemAnnotation.classPathResolvingEnabled())
                                .setFileCachingEnabled(fileSystemAnnotation.fileCachingEnabled())
                                .setFileCacheDir(fileSystemAnnotation.fileCacheDir())
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
                builder.with(new VertxOptions()
                        .setEventBusOptions(new io.vertx.core.eventbus.EventBusOptions()
                                .setClusterPublicHost(eventBusAnnotation.clusterPublicHost())
                                .setClusterPublicPort(eventBusAnnotation.clusterPublicPort())
                                .setClusterPingInterval(eventBusAnnotation.clusterPingInterval())
                                .setClusterPingReplyInterval(eventBusAnnotation.clusterPingReplyInterval())
                                .setReconnectAttempts(eventBusAnnotation.reconnectAttempts())
                                .setReconnectInterval(eventBusAnnotation.reconnectInterval())
                                .setConnectTimeout(eventBusAnnotation.connectTimeout())
                                .setTrustAll(eventBusAnnotation.trustAll())
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
                builder.with(new VertxOptions()
                        .setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions()
                                .setHostsPath(addressResolverAnnotation.hostsPath())
                                .setHostsRefreshPeriod(addressResolverAnnotation.hostsRefreshPeriod())
                                .setServers(List.of(addressResolverAnnotation.servers()))
                                .setRotateServers(addressResolverAnnotation.rotateServers())
                                .setCacheMinTimeToLive(addressResolverAnnotation.cacheMinTimeToLive())
                                .setCacheMaxTimeToLive(addressResolverAnnotation.cacheMaxTimeToLive())
                                .setCacheNegativeTimeToLive(addressResolverAnnotation.cacheNegativeTimeToLive())
                                .setQueryTimeout(addressResolverAnnotation.queryTimeout())
                                .setMaxQueries(addressResolverAnnotation.maxQueries())
                                .setRdFlag(addressResolverAnnotation.rdFlag())
                                .setSearchDomains(List.of(addressResolverAnnotation.searchDomains()))
                                .setNdots(addressResolverAnnotation.ndots())
                                .setOptResourceEnabled(addressResolverAnnotation.optResourceEnabled())
                                .setRoundRobinInetAddress(addressResolverAnnotation.roundRobinInetAddress())
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


    public static Vertx getVertx()
    {
        if (vertx == null)
        {
            new VertXPreStartup().onStartup();
        }
        return vertx;
    }

    @Override
    public void onDestroy()
    {
        vertx.close();
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 50;
    }
}

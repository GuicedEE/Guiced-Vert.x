package com.guicedee.vertx.grpc;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * A Guice module that creates and binds Vert.x {@link GrpcServer} and {@link GrpcClient}
 * instances into the injector.
 * <p>
 * <h3>Usage</h3>
 * <ol>
 *   <li>Subclass this class and implement {@link #getGrpcConnectionInfo()}.</li>
 *   <li>Register the subclass as an {@code IGuiceModule} SPI provider.</li>
 *   <li>Inject {@code GrpcServer} or {@code GrpcClient} (optionally with {@code @Named("yourName")}).</li>
 * </ol>
 *
 * <pre>{@code
 * public class MyGrpcModule extends GrpcModule<MyGrpcModule> {
 *     @Override
 *     protected GrpcConnectionInfo getGrpcConnectionInfo() {
 *         return new GrpcConnectionInfo()
 *                 .setName("main")
 *                 .setPort(50051);
 *     }
 * }
 * }</pre>
 */
@Log4j2
public abstract class GrpcModule<J extends GrpcModule<J>>
        extends AbstractModule
        implements IGuiceModule<J>, IGuicePreDestroy<J> {

    private final List<HttpServer> httpServers = new ArrayList<>();
    private final List<GrpcClient> grpcClients = new ArrayList<>();

    /**
     * Provides the gRPC connection info for this module.
     *
     * @return the connection info describing how to configure the gRPC server/client
     */
    protected abstract GrpcConnectionInfo getGrpcConnectionInfo();

    @Override
    protected void configure() {
        // First, configure the programmatic connection from getGrpcConnectionInfo()
        GrpcConnectionInfo info = getGrpcConnectionInfo();
        if (info != null) {
            configureConnection(info);
        } else {
            log.debug("No programmatic GrpcConnectionInfo provided from {}", getClass().getName());
        }

        // Also configure any annotation-discovered connections
        for (GrpcConnectionInfo discovered : GrpcPreStartup.getDiscoveredConnections()) {
            configureConnection(discovered);
        }
    }

    private void configureConnection(GrpcConnectionInfo info) {
        log.info("📡 Configuring gRPC module '{}' — {}:{}",
                info.getName(), info.getHost(), info.getPort());

        try {
            Vertx vertx = VertXPreStartup.getVertx();
            if (vertx == null) {
                log.error("❌ Vert.x instance is not available. Cannot create gRPC server/client.");
                return;
            }

            // Build GrpcServerOptions
            GrpcServerOptions serverOptions = new GrpcServerOptions()
                    .setScheduleDeadlineAutomatically(info.isScheduleDeadlineAutomatically())
                    .setDeadlinePropagation(info.isDeadlinePropagation());

            // Remove disabled protocols
            if (!info.isGrpcWebEnabled()) {
                serverOptions.removeEnabledProtocol(io.vertx.grpc.server.GrpcProtocol.WEB);
                serverOptions.removeEnabledProtocol(io.vertx.grpc.server.GrpcProtocol.WEB_TEXT);
            }

            // Create the GrpcServer
            GrpcServer grpcServer = GrpcServer.server(vertx, serverOptions);

            // Discover and register gRPC services via SPI
            ServiceLoader<IGrpcServiceProvider> serviceProviders = ServiceLoader.load(IGrpcServiceProvider.class);
            for (IGrpcServiceProvider provider : serviceProviders) {
                try {
                    provider.registerServices(grpcServer);
                    log.info("  ↳ gRPC services registered from: {}", provider.getClass().getSimpleName());
                } catch (Exception e) {
                    log.error("❌ Failed to register gRPC services from {}: {}",
                            provider.getClass().getName(), e.getMessage(), e);
                }
            }

            // Build HttpServerOptions for the gRPC server
            HttpServerOptions httpOptions = new HttpServerOptions()
                    .setPort(info.getPort())
                    .setHost(info.getHost());

            // TLS configuration
            if (info.isTlsEnabled()) {
                httpOptions.setSsl(true);
                if (info.getTlsCertPath() != null && !info.getTlsCertPath().isBlank()
                        && info.getTlsKeyPath() != null && !info.getTlsKeyPath().isBlank()) {
                    httpOptions.setKeyCertOptions(new PemKeyCertOptions()
                            .setCertPath(info.getTlsCertPath())
                            .setKeyPath(info.getTlsKeyPath()));
                }
            }

            // Create and start the HTTP server with gRPC handler
            HttpServer httpServer = vertx.createHttpServer(httpOptions);
            httpServer.requestHandler(grpcServer);
            httpServer.listen()
                    .onSuccess(s -> log.info("✅ gRPC server '{}' listening on {}:{}",
                            info.getName(), info.getHost(), s.actualPort()))
                    .onFailure(t -> log.error("❌ gRPC server '{}' failed to start: {}",
                            info.getName(), t.getMessage(), t));
            httpServers.add(httpServer);

            // Create a GrpcClient
            GrpcClientOptions clientOptions = new GrpcClientOptions();
            GrpcClient grpcClient = GrpcClient.client(vertx, clientOptions);
            grpcClients.add(grpcClient);

            // Bind GrpcServer with @Named qualifier
            bind(GrpcServer.class)
                    .annotatedWith(Names.named(info.getName()))
                    .toInstance(grpcServer);

            // Bind GrpcClient with @Named qualifier
            bind(GrpcClient.class)
                    .annotatedWith(Names.named(info.getName()))
                    .toInstance(grpcClient);

            // If this is the default, also bind without @Named
            if (info.isDefaultConnection()) {
                bind(GrpcServer.class).toInstance(grpcServer);
                bind(GrpcClient.class).toInstance(grpcClient);
            }

            log.info("✅ gRPC server & client bound as @Named(\"{}\"){}",
                    info.getName(),
                    info.isDefaultConnection() ? " [default]" : "");

        } catch (Throwable t) {
            log.error("❌ Failed to create gRPC server/client for '{}': {}",
                    info.getName(), t.getMessage(), t);
        }
    }

    @Override
    public void onDestroy() {
        for (HttpServer server : httpServers) {
            try {
                server.close();
                log.info("🛑 gRPC HTTP server closed");
            } catch (Throwable t) {
                log.debug("⚠️ gRPC HTTP server close failed: {}", t.getMessage());
            }
        }
        httpServers.clear();

        for (GrpcClient client : grpcClients) {
            try {
                client.close();
                log.info("🛑 gRPC client closed");
            } catch (Throwable t) {
                log.debug("⚠️ gRPC client close failed: {}", t.getMessage());
            }
        }
        grpcClients.clear();
    }

    @Override
    public Integer sortOrder() {
        return 62;
    }
}



package com.guicedee.vertx.grpc;

import io.vertx.grpc.server.GrpcServer;

/**
 * SPI interface for registering gRPC service implementations with the {@link GrpcServer}.
 * <p>
 * Implement this interface and register it via {@code META-INF/services/} and
 * {@code module-info.java} to have your gRPC services automatically added
 * to the server during startup.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public class GreeterServiceProvider implements IGrpcServiceProvider {
 *     @Override
 *     public void registerServices(GrpcServer server) {
 *         GreeterGrpcService service = GreeterGrpcService.create(new GreeterServiceImpl());
 *         server.addService(service);
 *     }
 * }
 * }</pre>
 */
public interface IGrpcServiceProvider {

    /**
     * Register gRPC services on the given server.
     *
     * @param server the gRPC server to add services to
     */
    void registerServices(GrpcServer server);
}


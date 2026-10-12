package com.guicedee.vertx;

import io.netty.channel.*;
import io.netty.channel.socket.*;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.transport.Transport;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Capture accepted TCP transports before HTTP/TLS handlers can delay their close. */
public final class HttpTransportCapture implements VertxServiceProvider {
    private record Endpoints(java.net.SocketAddress local, java.net.SocketAddress remote) { }
    private static final ConcurrentHashMap<Endpoints, ChannelHandlerContext> accepted = new ConcurrentHashMap<>();
    private static final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(8192);

    private static ChannelHandlerContext capture(HttpServerRequest request) {
        if (request.version() != HttpVersion.HTTP_1_0 && request.version() != HttpVersion.HTTP_1_1) return null;
        var connection = request.connection();
        var local = connection.localAddress(true); var remote = connection.remoteAddress(true);
        if (local == null || remote == null || !local.isInetSocket() || !remote.isInetSocket()) return null;
        return accepted.get(new Endpoints(new InetSocketAddress(local.hostAddress(), local.port()),
                new InetSocketAddress(remote.hostAddress(), remote.port())));
    }

    @Override public void init(VertxBootstrap bootstrap) {
        var transport = bootstrap.transport();
        if (transport instanceof Capturing) return;
        if (transport == null) {
            var nativeTransport = io.vertx.core.transport.Transport.nativeTransport();
            transport = bootstrap.options().getPreferNativeTransport() && nativeTransport != null && nativeTransport.available()
                    ? nativeTransport.implementation() : io.vertx.core.transport.Transport.NIO.implementation();
        }
        bootstrap.transport(new Capturing(transport));
    }

    /** Resolve once at admission; never rebind a cancelled stream to a reused TCP endpoint. */
    public static java.util.function.Supplier<Future<Boolean>> cancellation(HttpServerRequest request) {
        var response = request.response();
        if (request.version() == HttpVersion.HTTP_2) return response::cancel;
        var context = capture(request);
        if (context == null || !context.channel().isActive() || response.closed())
            throw new IllegalStateException("HTTP transport unavailable");
        return () -> {
            if (context.channel().isActive()) {
                // This context precedes SSL and Vert.x outbound handlers. Its close reaches
                // the transport without waiting for a pending response/close-notify flush.
                var deadline = context.executor().schedule(() -> {
                    if (context.channel().isActive()) context.close();
                }, 2, TimeUnit.SECONDS);
                context.channel().closeFuture().addListener(ignored -> deadline.cancel(false));
            }
            return response.cancel();
        };
    }

    private static final class Capturing implements Transport {
        private final Transport delegate;
        private Capturing(Transport delegate) { this.delegate = delegate; }
        @Override public boolean supportsDomainSockets() { return delegate.supportsDomainSockets(); }
        @Override public boolean supportFileRegion() { return delegate.supportFileRegion(); }
        @Override public boolean isAvailable() { return delegate.isAvailable(); }
        @Override public Throwable unavailabilityCause() { return delegate.unavailabilityCause(); }
        @Override public java.net.SocketAddress convert(io.vertx.core.net.SocketAddress address) { return delegate.convert(address); }
        @Override public io.vertx.core.net.SocketAddress convert(java.net.SocketAddress address) { return delegate.convert(address); }
        @Override public IoHandlerFactory ioHandlerFactory() { return delegate.ioHandlerFactory(); }
        @Override public EventLoopGroup eventLoopGroup(int type, int size, java.util.concurrent.ThreadFactory threads, int ratio) {
            return delegate.eventLoopGroup(type, size, threads, ratio);
        }
        @Override public DatagramChannel datagramChannel(InternetProtocolFamily family) { return delegate.datagramChannel(family); }
        @Override public ChannelFactory<? extends DatagramChannel> datagramChannelFactory() { return delegate.datagramChannelFactory(); }
        @Override public ChannelFactory<? extends Channel> channelFactory(boolean domain) { return delegate.channelFactory(domain); }
        @Override public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domain) {
            var factory = delegate.serverChannelFactory(domain);
            if (domain) return factory;
            return () -> {
                var server = factory.newChannel();
                server.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext parent, Object message) throws Exception {
                        if (message instanceof SocketChannel child) child.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                            @Override public void channelActive(ChannelHandlerContext context) throws Exception {
                                var key = new Endpoints(context.channel().localAddress(), context.channel().remoteAddress());
                                if (capacity.tryAcquire()) {
                                    if (accepted.putIfAbsent(key, context) != null) capacity.release();
                                    else context.channel().closeFuture().addListener(ignored -> {
                                        if (accepted.remove(key, context)) capacity.release();
                                    });
                                }
                                super.channelActive(context);
                            }
                        });
                        super.channelRead(parent, message);
                    }
                });
                return server;
            };
        }
        @Override public void configure(DatagramChannel channel, io.vertx.core.datagram.DatagramSocketOptions options) { delegate.configure(channel, options); }
        @Override public void configure(io.vertx.core.net.TcpConfig options, boolean domain, io.netty.bootstrap.Bootstrap bootstrap) { delegate.configure(options, domain, bootstrap); }
        @Override public void configure(io.vertx.core.net.TcpConfig options, boolean domain, io.netty.bootstrap.ServerBootstrap bootstrap) { delegate.configure(options, domain, bootstrap); }
    }
}

package com.guicedee.vertx;

import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.http.WebSocketInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.http.HttpServerRequestWrapper;
import io.netty.channel.ChannelHandlerContext;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** A bounded shutdown for sockets whose peers have stopped reading. */
public final class WebSocketBackpressure {
    private static final java.util.Map<HttpConnection, java.lang.ref.WeakReference<ChannelHandlerContext>> transports = new java.util.WeakHashMap<>();
    private static final java.util.Map<io.vertx.core.MultiMap, java.lang.ref.WeakReference<ChannelHandlerContext>> socketTransports = new java.util.IdentityHashMap<>();

    /** Capture before Vert.x Web adds its forwarded-header socket wrapper. */
    public static HttpServerRequest capture(HttpServerRequest request) {
        if (!(request instanceof HttpServerRequestInternal internal)
                || !"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) return request;
        return new HttpServerRequestWrapper(internal) {
            @Override public Future<ServerWebSocket> toWebSocket() {
                return super.toWebSocket().onSuccess(socket -> {
                    if (socket instanceof WebSocketInternal raw) {
                        var connection = request.connection();
                        var context = raw.channelHandlerContext();
                        var headers = socket.headers();
                        var reference = new java.lang.ref.WeakReference<>(context);
                        synchronized (transports) { transports.put(connection, reference); socketTransports.put(headers, reference); }
                        context.channel().closeFuture().addListener(ignored -> {
                            synchronized (transports) { transports.remove(connection); socketTransports.remove(headers); }
                        });
                    }
                });
            }
        };
    }

    public static Future<Void> close(ServerWebSocket socket, short code, String reason) {
        return close(socket, null, code, reason);
    }
    public static Future<Void> close(ServerWebSocket socket, HttpConnection upgradeConnection, short code, String reason) {
        // Vert.x 5.2 starts its WebSocket close timeout after the close frame
        // flushes. The transport context closes beneath that handshake so a
        // blocked flush cannot retain the socket indefinitely.
        // Vert.x Web wraps upgraded sockets when applying forwarded headers.
        // Capture the HTTP transport at upgrade rather than reflect into its wrapper.
        ChannelHandlerContext transport;
        if (socket instanceof WebSocketInternal internal) transport = internal.channelHandlerContext();
        else synchronized (transports) {
            var reference = transports.get(upgradeConnection);
            // Route wrappers delegate the same header object. Identity matching
            // also prevents reused WebSocket keys from selecting another socket.
            if (reference == null) reference = socketTransports.get(socket.headers());
            transport = reference == null ? null : reference.get();
        }
        if (transport != null) {
            var deadline = transport.executor().schedule(() -> {
                if (transport.channel().isActive()) transport.close();
            }, 2, TimeUnit.SECONDS);
            try {
                return socket.shutdown(Duration.ofSeconds(2), code, reason)
                        .onComplete(ignored -> deadline.cancel(false));
            } catch (Throwable failure) {
                transport.executor().execute(transport::close);
                deadline.cancel(false);
                return Future.failedFuture(failure);
            }
        }
        return socket.shutdown(Duration.ofSeconds(2), code, reason);
    }
    private WebSocketBackpressure() { }
}

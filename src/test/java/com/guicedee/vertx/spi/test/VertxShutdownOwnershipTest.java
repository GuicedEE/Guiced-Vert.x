package com.guicedee.vertx.spi.test;

import com.guicedee.vertx.spi.VertXPreStartup;
import com.guicedee.vertx.VertXPostStartup;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VertxShutdownOwnershipTest {
    private Vertx runtime;

    private IGuicePreDestroy<?> registeredHook() {
        return java.util.ServiceLoader.load(IGuicePreDestroy.class).stream()
                .filter(provider -> provider.type() == VertXPostStartup.class)
                .findFirst().orElseThrow().get();
    }

    @BeforeEach void initialize() throws Exception {
        resetOwner();
        Class.forName("com.guicedee.client.scopes.CallScoper");
    }

    @AfterEach void cleanup() throws Exception {
        try {
            if (runtime != null) runtime.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally { resetOwner(); }
    }

    private static void set(String name, Object value) throws Exception {
        var field = VertXPreStartup.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void resetOwner() throws Exception {
        for (String field : List.of("vertx", "starting", "closing", "resourceClosing")) set(field, null);
    }

    @Test void startupAndShutdownUseDifferentPriorities() {
        var owner = new VertXPreStartup();
        assertTrue(owner.sortOrder() < 0);
        assertEquals(Integer.MAX_VALUE, owner.shutdownSortOrder());
        assertEquals(Integer.MAX_VALUE, registeredHook().shutdownSortOrder());
    }

    @Test void pendingStartupIsAwaitedAndItsLateRuntimeIsClosedExactlyOnce() throws Exception {
        runtime = Vertx.vertx();
        var stops = new AtomicInteger();
        runtime.deployVerticle(new AbstractVerticle() {
            @Override public void stop(Promise<Void> completion) {
                runtime.setTimer(100, ignored -> { stops.incrementAndGet(); completion.complete(); });
            }
        }).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        var listener = runtime.createHttpServer().requestHandler(request -> request.response().end())
                .listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        int port = listener.actualPort();
        var pending = Promise.<Vertx>promise();
        set("starting", pending.future());
        var closed = VertXPreStartup.closeVertx();
        assertFalse(closed.isComplete());
        pending.complete(runtime);
        registeredHook().onDestroy();
        registeredHook().onDestroy();
        assertTrue(closed.succeeded());
        assertSame(closed, VertXPreStartup.closeVertx());
        assertEquals(1, stops.get());
        assertThrows(IllegalStateException.class, VertXPreStartup::getVertx);
        try (var socket = new Socket()) {
            assertThrows(java.io.IOException.class, () -> socket.connect(new InetSocketAddress("127.0.0.1", port), 500));
        }
    }

    @Test void startupCleanupFailureIsNotConvertedIntoSuccessfulShutdown() throws Exception {
        set("starting", Future.failedFuture("fixture-start-failure"));
        set("resourceClosing", Future.failedFuture("fixture-cleanup-failure"));
        var closed = VertXPreStartup.closeVertx();
        var failure = assertThrows(ExecutionException.class,
                () -> closed.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("fixture-cleanup-failure", failure.getCause().getMessage());
        assertSame(closed, VertXPreStartup.closeVertx());
        assertThrows(IllegalStateException.class, () -> registeredHook().onDestroy());
    }
}

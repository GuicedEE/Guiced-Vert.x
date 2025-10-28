package com.guicedee.vertx.test;

import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import com.guicedee.vertx.VertxEventPublisher;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxEventRegistryIntegrationTest {

    private Vertx vertx;

    // Shared state for consumers to update and tests to assert
    public static final AtomicReference<String> lastPublishedMessage = new AtomicReference<>();
    public static final AtomicReference<String> lastHeaderValue = new AtomicReference<>();
    public static final AtomicBoolean lastWasWorker = new AtomicBoolean(false);

    @BeforeAll
    public void beforeAll() {
        // Boot Vert.x and auto-register consumers per VertXPreStartup
        VertXPreStartup pre = new VertXPreStartup();
        pre.onStartup();
        vertx = VertXPreStartup.getVertx();
        assertNotNull(vertx, "Vertx must be initialized for tests");
    }

    @AfterAll
    public void afterAll() {
        if (VertXPreStartup.getVertx() != null) {
            VertXPreStartup.getVertx().close();
        }
    }

    @Test
    public void testSendRequestReply_UsesWorkerDispatch() throws Exception {
        // Arrange
        VertxEventDefinition def = def("test.send.reply");
        VertxEventPublisher<String> publisher = new VertxEventPublisher<>(vertx, def.value(), def);

        // Act
        Future<String> fut = publisher.send("ping");
        String reply = fut.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        // Assert
        assertTrue(lastWasWorker.get(), "Consumer should run on worker when worker=true option is set");
        assertEquals("ACK:ping", reply);
    }

    @Test
    public void testPublishLocalWithHeaders_DeliveredAndHeadersVisible() throws Exception {
        // Arrange
        lastPublishedMessage.set(null);
        lastHeaderValue.set(null);
        VertxEventDefinition def = def("test.publish.headers");
        VertxEventPublisher<String> publisher = new VertxEventPublisher<>(vertx, def.value(), def);

        // Act
        publisher.publish("hello-local", Map.of("session", "abc123"), true);

        // Wait for the consumer to update shared state
        awaitTrue(() -> Objects.equals("hello-local", lastPublishedMessage.get()), Duration.ofSeconds(5));

        // Assert
        assertEquals("hello-local", lastPublishedMessage.get());
        assertEquals("abc123", lastHeaderValue.get(), "Header should be visible to consumer");
    }

    private static void awaitTrue(java.util.concurrent.Callable<Boolean> condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Condition not met within timeout: " + timeout);
    }

    // --- Test consumer definitions below ---

    @VertxEventDefinition(
            value = "test.send.reply",
            options = @VertxEventOptions(localOnly = true, worker = true)
    )
    public static class SendReplyConsumer {
        public String consume(Message<String> message) {
            // Record execution context (worker vs event loop)
            lastWasWorker.set(Vertx.currentContext() != null && Vertx.currentContext().isWorkerContext());
            return "ACK:" + message.body();
        }
    }

    @VertxEventDefinition(
            value = "test.publish.headers",
            options = @VertxEventOptions(localOnly = true, worker = true)
    )
    public static class PublishHeadersConsumer {
        public void consume(Message<String> message) {
            lastPublishedMessage.set(message.body());
            lastHeaderValue.set(message.headers().get("session"));
        }
    }

    // Minimal VertxEventDefinition factory for publishers used in tests
    private static VertxEventDefinition def(String address) {
        return new VertxEventDefinition() {
            @Override public String value() { return address; }
            @Override public VertxEventOptions options() { return new DefaultOptions(); }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return VertxEventDefinition.class; }
        };
    }

    // Default options for test publishers (values not used by publisher methods)
    private static class DefaultOptions implements VertxEventOptions {
        @Override public boolean localOnly() { return true; }
        @Override public boolean autobind() { return true; }
        @Override public int consumerCount() { return 1; }
        @Override public boolean worker() { return false; }
        @Override public String workerPool() { return ""; }
        @Override public int workerPoolSize() { return 0; }
        @Override public int instances() { return 0; }
        @Override public String orderedByHeader() { return ""; }
        @Override public int maxBufferedMessages() { return 0; }
        @Override public int resumeAtMessages() { return 0; }
        @Override public int batchWindowMs() { return 0; }
        @Override public int batchMax() { return 0; }
        @Override public long timeoutMs() { return 0; }
        @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return VertxEventOptions.class; }
    }
}

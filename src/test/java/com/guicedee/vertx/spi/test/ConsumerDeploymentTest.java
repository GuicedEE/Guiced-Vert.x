package com.guicedee.vertx.spi.test;

import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import com.guicedee.vertx.spi.EventConsumerVerticle;
import com.guicedee.vertx.spi.VerticleBuilder;
import com.guicedee.vertx.spi.VertxConsumersStartup;
import com.guicedee.vertx.spi.VertxEventRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerDeploymentTest {
    private static final String ADDRESS = "test.deployment.four";
    private Vertx vertx;

    @BeforeEach
    void setUp() throws Exception {
        VertxEventRegistry.reset();
        VerticleBuilder.reset();
        vertx = Vertx.vertx();
        var method = Consumer.class.getMethod("consume");
        VertxEventRegistry.getEventConsumerDefinitions().put(ADDRESS, method.getAnnotation(VertxEventDefinition.class));
        VertxEventRegistry.getEventConsumerMethods().put(ADDRESS, method);
        VertxEventRegistry.getEventConsumerMethodClasses().put(ADDRESS, Consumer.class);
        // Observe real event-bus routing without bootstrapping application services.
        vertx.eventBus().addInboundInterceptor(delivery -> {
            if (ADDRESS.equals(delivery.message().address())) {
                // Vert.x duplicates the context for each delivery; identify its owning verticle context.
                delivery.message().reply(System.identityHashCode(((ContextInternal) Vertx.currentContext()).unwrap()));
            } else {
                delivery.next();
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        await(vertx.close());
        VertxEventRegistry.reset();
        VerticleBuilder.reset();
    }

    @Test
    void startupDeploysFourConsumersWithoutDuplicatingAndAllowsRedeployment() throws Exception {
        String deployment = await(vertx.deployVerticle(parent()));
        Set<Integer> contexts = receivingContexts();
        assertEquals(4, contexts.size(), "all four consumers must receive messages");

        Promise<Void> duplicate = Promise.promise();
        new VertxConsumersStartup().start(duplicate, vertx, new AbstractVerticle() {}, Consumer.class.getPackageName());
        await(duplicate.future());
        assertEquals(contexts, receivingContexts(), "a repeated startup must not register another consumer group");

        await(vertx.undeploy(deployment));
        assertFalse(VertxEventRegistry.getRegisteredAddresses().contains(ADDRESS));
        assertThrows(ExecutionException.class, () -> await(vertx.eventBus().request(ADDRESS, "after undeploy")));

        await(vertx.deployVerticle(parent()));
        assertEquals(4, receivingContexts().size(), "undeployment must release the address for a new deployment");
    }

    @Test
    void eachSuppliedVerticleRegistersItsOwnConsumer() throws Exception {
        var method = Consumer.class.getMethod("consume");
        var definition = method.getAnnotation(VertxEventDefinition.class);
        await(vertx.deployVerticle(() -> new EventConsumerVerticle(ADDRESS, definition, method, Consumer.class),
                new DeploymentOptions().setInstances(4)));
        assertEquals(4, receivingContexts().size(), "address deduplication must not suppress intentional instances");
    }

    @Test
    void failedDeploymentPropagatesAndCanBeRetried() throws Exception {
        RuntimeException failure = new RuntimeException("deliberate deployment failure");
        Vertx failingVertx = (Vertx) Proxy.newProxyInstance(Vertx.class.getClassLoader(), new Class<?>[]{Vertx.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("deployVerticle")) return Future.failedFuture(failure);
                    throw new UnsupportedOperationException(method.getName());
                });
        Promise<Void> ready = Promise.promise();
        new VertxConsumersStartup().start(ready, failingVertx, new AbstractVerticle() {}, Consumer.class.getPackageName());
        assertSame(failure, assertThrows(ExecutionException.class, () -> await(ready.future())).getCause());
        assertFalse(VertxEventRegistry.getRegisteredAddresses().contains(ADDRESS));
        await(vertx.deployVerticle(parent()));
        assertEquals(4, receivingContexts().size());
    }

    private AbstractVerticle parent() {
        return new AbstractVerticle() {
            @Override
            public void start(Promise<Void> ready) {
                new VertxConsumersStartup().start(ready, vertx, this, Consumer.class.getPackageName());
            }
        };
    }

    private Set<Integer> receivingContexts() throws Exception {
        Set<Integer> contexts = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            contexts.add(await(vertx.eventBus().<Integer>request(ADDRESS, "probe")).body());
        }
        return contexts;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    public static class Consumer {
        @VertxEventDefinition(value = ADDRESS, options = @VertxEventOptions(instances = 4))
        public void consume() { }
    }
}

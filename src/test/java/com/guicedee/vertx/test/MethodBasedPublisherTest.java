package com.guicedee.vertx.test;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventPublisher;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

/**
 * Test class demonstrating how to publish messages to method-based consumers
 */
@Log4j2
public class MethodBasedPublisherTest {

    @Inject
    @Named("test.void.method")
    @VertxEventDefinition("test.void.method")
    private VertxEventPublisher<String> voidMethodPublisher;

    @Inject
    @Named("test.string.method")
    @VertxEventDefinition("test.string.method")
    private VertxEventPublisher<String> stringMethodPublisher;

    @Inject
    @Named("test.future.method")
    @VertxEventDefinition("test.future.method")
    private VertxEventPublisher<String> futureMethodPublisher;

    @Inject
    @Named("test.completable.method")
    @VertxEventDefinition("test.completable.method")
    private VertxEventPublisher<String> completableMethodPublisher;

    /**
     * Test publishing to a void method consumer
     */
    public void testVoidMethod() {
        log.info("Publishing to void method consumer");
        voidMethodPublisher.publish("Hello from void method test");
    }

    /**
     * Test publishing to a string method consumer
     */
    public Future<Object> testStringMethod() {
        log.info("Publishing to string method consumer");
        return stringMethodPublisher.send("Hello from string method test")
                .onSuccess(response -> log.info("Received response from string method: {}", response))
                .onFailure(error -> log.error("Error from string method", error));
    }

    /**
     * Test publishing to a future method consumer
     */
    public Future<Object> testFutureMethod() {
        log.info("Publishing to future method consumer");
        return futureMethodPublisher.send("Hello from future method test")
                .onSuccess(response -> log.info("Received response from future method: {}", response))
                .onFailure(error -> log.error("Error from future method", error));
    }

    /**
     * Test publishing to a completable future method consumer
     */
    public Future<Object> testCompletableMethod() {
        log.info("Publishing to completable future method consumer");
        return completableMethodPublisher.send("Hello from completable future method test")
                .onSuccess(response -> log.info("Received response from completable future method: {}", response))
                .onFailure(error -> log.error("Error from completable future method", error));
    }

    /**
     * Run all tests
     */
    public void runAllTests() {
        testVoidMethod();
        testStringMethod();
        testFutureMethod();
        testCompletableMethod();
    }

    /**
     * Main method to run the tests
     */
    public static void main(String[] args) {
        // Initialize the Guice context
        IGuiceContext.instance();

        // Get the test class
        MethodBasedPublisherTest test = IGuiceContext.get(MethodBasedPublisherTest.class);

        // Run all tests
        test.runAllTests();
    }
}

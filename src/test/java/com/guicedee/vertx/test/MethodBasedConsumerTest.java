package com.guicedee.vertx.test;

import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CompletableFuture;

/**
 * Test class demonstrating method-based event consumers with different return types and parameter types
 */
@Log4j2
public class MethodBasedConsumerTest {
    
    /**
     * Simple void method that consumes a message body directly
     */
    @VertxEventDefinition(value = "test.void.method", options = @VertxEventOptions(localOnly = true))
    public void handleVoidMethod(String message) {
        log.info("Received message in void method: {}", message);
    }
    
    /**
     * Method that consumes a Message object and returns a String
     */
    @VertxEventDefinition(value = "test.string.method", options = @VertxEventOptions(localOnly = true))
    public String handleStringMethod(Message<String> message) {
        log.info("Received message in string method: {}", message.body());
        return "Processed: " + message.body();
    }
    
    /**
     * Method that returns a Future
     */
    @VertxEventDefinition(value = "test.future.method", options = @VertxEventOptions(localOnly = true))
    public Future<String> handleFutureMethod(String message) {
        log.info("Received message in future method: {}", message);
        Promise<String> promise = Promise.promise();
        
        // Simulate async processing
        new Thread(() -> {
            try {
                Thread.sleep(100);
                promise.complete("Future processed: " + message);
            } catch (InterruptedException e) {
                promise.fail(e);
            }
        }).start();
        
        return promise.future();
    }
    
    /**
     * Method that returns a CompletableFuture
     */
    @VertxEventDefinition(value = "test.completable.method", options = @VertxEventOptions(localOnly = true))
    public CompletableFuture<String> handleCompletableFutureMethod(String message) {
        log.info("Received message in completable future method: {}", message);
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // Simulate async processing
        new Thread(() -> {
            try {
                Thread.sleep(100);
                future.complete("CompletableFuture processed: " + message);
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
}
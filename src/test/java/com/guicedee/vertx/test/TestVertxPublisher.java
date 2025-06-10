package com.guicedee.vertx.test;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventPublisher;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

/**
 * Example of a class that publishes Vertx events
 */
@Log4j2
public class TestVertxPublisher {
    
    @Inject
    @Named("test.event")
    @VertxEventDefinition("test.event")
    private VertxEventPublisher<String> eventPublisher;
    
    /**
     * Publishes a message to the event bus
     * 
     * @param message The message to publish
     */
    public void publishMessage(String message) {
        log.info("Publishing message: {}", message);
        eventPublisher.publish(message);
    }
    
    /**
     * Sends a message to the event bus and waits for a reply
     * 
     * @param message The message to send
     * @return A future that completes with the reply
     */
    public Future<String> sendMessage(String message) {
        log.info("Sending message: {}", message);
        return eventPublisher.send(message);
    }
    
    /**
     * Example of how to use the publisher
     */
    public static void main(String[] args) {
        // Initialize the Guice context
        IGuiceContext.instance();
        
        // Get the publisher
        TestVertxPublisher publisher = IGuiceContext.get(TestVertxPublisher.class);
        
        // Publish a message
        publisher.publishMessage("Hello, Vertx!");
        
        // Send a message and get a reply
        publisher.sendMessage("Hello, Vertx with reply!")
                .onSuccess(reply -> log.info("Received reply: {}", reply))
                .onFailure(error -> log.error("Error sending message", error));
    }
}
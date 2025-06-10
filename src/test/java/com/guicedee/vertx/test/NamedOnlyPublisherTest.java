package com.guicedee.vertx.test;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.VertxEventPublisher;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

/**
 * Test class demonstrating that publishers can be defined with only the @Named annotation
 */
@Log4j2
public class NamedOnlyPublisherTest {

    @Inject
    @Named("test.named.only")
    private VertxEventPublisher<String> namedOnlyPublisher;

    /**
     * Publishes a message to the event bus
     * 
     * @param message The message to publish
     */
    public void publishMessage(String message) {
        log.info("Publishing message: {}", message);
        namedOnlyPublisher.publish(message);
    }

    /**
     * Sends a message to the event bus and waits for a reply
     * 
     * @param message The message to send
     * @return A future that completes with the reply
     */
    public Future<String> sendMessage(String message) {
        log.info("Sending message: {}", message);
        return namedOnlyPublisher.send(message);
    }

    /**
     * Example of how to use the publisher
     */
    public static void main(String[] args) {
        // Initialize the Guice context
        IGuiceContext.instance();
        
        // Get the publisher
        NamedOnlyPublisherTest publisher = IGuiceContext.get(NamedOnlyPublisherTest.class);
        
        // Publish a message
        publisher.publishMessage("Hello from @Named only publisher!");
        
        // Send a message and get a reply
        publisher.sendMessage("Hello with reply from @Named only publisher!")
                .onSuccess(reply -> log.info("Received reply: {}", reply))
                .onFailure(error -> log.error("Error sending message", error));
    }
}
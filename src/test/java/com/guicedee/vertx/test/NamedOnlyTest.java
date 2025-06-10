package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import lombok.extern.log4j.Log4j2;

/**
 * Test runner for the NamedOnlyPublisherTest and NamedOnlyConsumerTest
 */
@Log4j2
public class NamedOnlyTest {
    
    /**
     * Main method to run the test
     */
    public static void main(String[] args) {
        log.info("Starting NamedOnlyTest");
        
        // Initialize the Guice context
        IGuiceContext.instance();
        
        // Get the consumer (this will register it with the event bus)
        IGuiceContext.get(NamedOnlyConsumerTest.class);
        log.info("Consumer registered");
        
        // Get the publisher
        NamedOnlyPublisherTest publisher = IGuiceContext.get(NamedOnlyPublisherTest.class);
        log.info("Publisher created");
        
        // Test publishing a message
        log.info("Testing publish...");
        publisher.publishMessage("Hello from @Named only test!");
        
        // Test sending a message and getting a reply
        log.info("Testing send with reply...");
        publisher.sendMessage("Hello with reply from @Named only test!")
                .onSuccess(reply -> {
                    log.info("Received reply: {}", reply);
                    log.info("Test completed successfully!");
                })
                .onFailure(error -> {
                    log.error("Error sending message", error);
                    log.error("Test failed!");
                });
        
        // Wait a bit for async operations to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
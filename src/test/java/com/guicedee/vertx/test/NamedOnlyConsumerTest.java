package com.guicedee.vertx.test;

import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import io.vertx.core.eventbus.Message;
import lombok.extern.log4j.Log4j2;

/**
 * Test class with a method-based consumer for the "test.named.only" address
 */
@Log4j2
public class NamedOnlyConsumerTest {
    
    /**
     * Handles messages from the NamedOnlyPublisherTest
     */
    @VertxEventDefinition(value = "test.named.only", options = @VertxEventOptions(localOnly = true))
    public String handleNamedOnlyMessage(Message<String> message) {
        log.info("Received message in NamedOnlyConsumerTest: {}", message.body());
        return "Response from NamedOnlyConsumerTest: " + message.body();
    }
}
package com.guicedee.vertx.test;

import com.guicedee.vertx.VertxConsumer;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import io.vertx.core.eventbus.Message;
import lombok.extern.log4j.Log4j2;

/**
 * Example of a Vertx event consumer
 */
@Log4j2
@VertxEventDefinition(value = "test.event", options = @VertxEventOptions(localOnly = true))
public class TestVertxConsumer implements VertxConsumer<String> {
    
    @Override
    public void consume(Message<String> message) {
        log.info("Received message: {}", message.body());
        message.reply("Message received: " + message.body());
    }
}
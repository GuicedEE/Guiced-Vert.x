package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test class demonstrating JSON conversion for method parameters
 */
@Log4j2
public class JsonConversionTest {

    /**
     * A simple data class for testing JSON conversion
     */
    @Data
    public static class TestData {
        private String name;
        private int age;
        private boolean active;

        // Default constructor required for Jackson
        public TestData() {
        }

        public TestData(String name, int age, boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }
    }

    /**
     * Method that expects a TestData object but receives a JsonObject
     */
    @VertxEventDefinition(value = "test.json.conversion", options = @VertxEventOptions(localOnly = true))
    public String handleJsonConversion(TestData data) {
        log.info("Received TestData: name={}, age={}, active={}", 
                data.getName(), data.getAge(), data.isActive());
        return "Processed TestData: " + data.getName();
    }

    /**
     * Method that receives a Message with TestData
     */
    @VertxEventDefinition(value = "test.json.conversion.message", options = @VertxEventOptions(localOnly = true))
    public String handleJsonConversionMessage(Message<TestData> message) {
        TestData data = message.body();
        log.info("Received TestData via Message: name={}, age={}, active={}", 
                data.getName(), data.getAge(), data.isActive());
        return "Processed TestData via Message: " + data.getName();
    }

    /**
     * Initialize the Guice context before running tests
     */
    @BeforeAll
    public static void setup() {
        // Initialize the Guice context
        IGuiceContext.instance();
    }

    /**
     * Test JSON conversion for method parameters
     */
    @Test
    public void testJsonConversion() throws InterruptedException {
        log.info("Starting JsonConversionTest");

        // Get the test class (this will register it with the event bus)
        JsonConversionTest test = IGuiceContext.get(JsonConversionTest.class);
        log.info("Test class registered");

        // Create a JsonObject with test data
        JsonObject jsonObject = new JsonObject()
                .put("name", "Test User")
                .put("age", 30)
                .put("active", true);

        // Get the Vertx instance
        var vertx = IGuiceContext.get(io.vertx.core.Vertx.class);

        // Use CountDownLatch to wait for async operations to complete
        CountDownLatch latch = new CountDownLatch(2);

        // Test sending a JsonObject to a method expecting TestData
        log.info("Testing JSON conversion...");
        vertx.eventBus().request("test.json.conversion", jsonObject)
                .onSuccess(reply -> {
                    log.info("Received reply: {}", reply.body());
                    log.info("JSON conversion test successful!");
                    latch.countDown();
                })
                .onFailure(error -> {
                    log.error("Error in JSON conversion test", error);
                    log.error("JSON conversion test failed!");
                    latch.countDown();
                });

        // Test sending a JsonObject to a method expecting Message<TestData>
        log.info("Testing JSON conversion with Message...");
        vertx.eventBus().request("test.json.conversion.message", jsonObject)
                .onSuccess(reply -> {
                    log.info("Received reply: {}", reply.body());
                    log.info("JSON conversion with Message test successful!");
                    latch.countDown();
                })
                .onFailure(error -> {
                    log.error("Error in JSON conversion with Message test", error);
                    log.error("JSON conversion with Message test failed!");
                    latch.countDown();
                });

        // Wait for async operations to complete
        latch.await(5, TimeUnit.SECONDS);
    }
}

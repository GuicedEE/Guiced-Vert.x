package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.VertxEventDefinition;
import com.guicedee.vertx.VertxEventOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class demonstrating JSON conversion for method parameters
 */
@Log4j2
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    public void setup() {
        // Initialize the Guice context
        IGuiceContext.instance().inject();
    }

    /**
     * Test JSON conversion for direct parameter (JsonObject → TestData)
     */
    @Test
    public void testJsonConversion() throws Exception {
        log.info("Starting testJsonConversion");

        JsonObject jsonObject = new JsonObject()
                .put("name", "Test User")
                .put("age", 30)
                .put("active", true);

        var vertx = IGuiceContext.get(io.vertx.core.Vertx.class);

        var result = vertx.eventBus().request("test.json.conversion", jsonObject)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("Processed TestData: Test User", result.body());
        log.info("testJsonConversion passed");
    }

    /**
     * Test JSON conversion for Message&lt;TestData&gt; parameter (JsonObject body → TestData)
     */
    @Test
    public void testJsonConversionMessage() throws Exception {
        log.info("Starting testJsonConversionMessage");

        JsonObject jsonObject = new JsonObject()
                .put("name", "Test User")
                .put("age", 30)
                .put("active", true);

        var vertx = IGuiceContext.get(io.vertx.core.Vertx.class);

        var result = vertx.eventBus().request("test.json.conversion.message", jsonObject)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("Processed TestData via Message: Test User", result.body());
        log.info("testJsonConversionMessage passed");
    }
}

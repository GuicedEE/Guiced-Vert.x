package com.guicedee.vertx.spi.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.spi.CodecRegistry;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CodecRegistry
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CodecRegistryTest {
    
    private Vertx vertx;
    
    @BeforeAll
    public void setUp() {
        // Boot Vert.x once and reuse across all tests
        IGuiceContext.instance().getConfig().setFieldScanning(true).setClasspathScanning(true).setAnnotationScanning(true).setIgnoreClassVisibility(true);
        IGuiceContext.instance().inject();
        vertx = VertXPreStartup.getVertx();
        assertNotNull(vertx, "Vertx must be initialized for tests");
    }

    @Test
    public void testToKebabCase() {
        assertEquals("test", CodecRegistry.toKebabCase("test"));
        assertEquals("test-case", CodecRegistry.toKebabCase("testCase"));
        assertEquals("test-case-example", CodecRegistry.toKebabCase("testCaseExample"));
        assertEquals("uwe-server-message", CodecRegistry.toKebabCase("UWEServerMessage"));
        assertEquals("", CodecRegistry.toKebabCase(""));
        assertEquals("", CodecRegistry.toKebabCase(null));
    }
    
    @Test
    public void testIsStandardVertxType() {
        assertTrue(CodecRegistry.isStandardVertxType(String.class));
        assertTrue(CodecRegistry.isStandardVertxType(Integer.class));
        assertTrue(CodecRegistry.isStandardVertxType(int.class));
        assertTrue(CodecRegistry.isStandardVertxType(Buffer.class));
        assertTrue(CodecRegistry.isStandardVertxType(null));
        
        assertFalse(CodecRegistry.isStandardVertxType(TestMessage.class));
        assertFalse(CodecRegistry.isStandardVertxType(Map.class));
    }
    
    @Test
    public void testGetCodecName() {
        assertEquals("test-message", CodecRegistry.getCodecName(TestMessage.class));
        assertNull(CodecRegistry.getCodecName(String.class));
        assertNull(CodecRegistry.getCodecName(null));
    }
    
    @Test
    public void testCreateAndRegisterCodec() throws Exception {
        String codecName = CodecRegistry.createAndRegisterCodec(vertx, TestMessage.class);
        assertEquals("test-message", codecName);
        
        // Test sending a message with the codec
        TestMessage testMessage = new TestMessage("Hello World");
        
        // Create a consumer and await its registration before sending
        var consumer = vertx.eventBus().consumer("test.address", message -> {
            assertTrue(message.body() instanceof TestMessage);
            TestMessage received = (TestMessage) message.body();
            assertEquals("Hello World", received.getContent());
            message.reply("Received");
        });
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        // Send a message with the codec and await the reply
        Object reply = vertx.eventBus().request("test.address", testMessage,
                new io.vertx.core.eventbus.DeliveryOptions().setCodecName(codecName))
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertNotNull(reply);

        // Test registering the same codec again
        String codecName2 = CodecRegistry.createAndRegisterCodec(vertx, TestMessage.class);
        assertEquals(codecName, codecName2);
        
        // Test registering a standard type
        assertNull(CodecRegistry.createAndRegisterCodec(vertx, String.class));
    }
    
    @Test
    public void testCreateAndRegisterCodecsForAllEventTypes() throws Exception {
        // We can't easily test this directly since it depends on the actual VertxEventRegistry state
        // Instead, we'll verify that our codec registration mechanism works
        
        // Register a codec for TestMessage
        String codecName = CodecRegistry.createAndRegisterCodec(vertx, TestMessage.class);
        assertEquals("test-message", codecName);
        
        // Create a future to await delivery
        var received = new java.util.concurrent.CompletableFuture<TestMessage>();

        // Create a consumer and await its registration before publishing
        var consumer = vertx.eventBus().consumer("test.address2", message -> {
            assertTrue(message.body() instanceof TestMessage);
            TestMessage msg = (TestMessage) message.body();
            received.complete(msg);
        });
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        // Send a message with the codec
        TestMessage testMessage = new TestMessage("Test Message");
        vertx.eventBus().publish("test.address2", testMessage, 
                new io.vertx.core.eventbus.DeliveryOptions().setCodecName(codecName));

        // Await delivery before the test exits
        TestMessage result = received.get(10, TimeUnit.SECONDS);
        assertEquals("Test Message", result.getContent());
    }
    
    /**
     * Test message class for codec testing
     */
    public static class TestMessage {
        private String content;
        
        public TestMessage() {
        }
        
        public TestMessage(String content) {
            this.content = content;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
    }
}
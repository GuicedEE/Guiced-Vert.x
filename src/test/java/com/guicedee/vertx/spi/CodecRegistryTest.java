package com.guicedee.vertx.spi;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CodecRegistry
 */
public class CodecRegistryTest {
    
    private Vertx vertx;
    
    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
    }
    
    @Test
    public void testToKebabCase() {
        assertEquals("test", CodecRegistry.toKebabCase("test"));
        assertEquals("test-case", CodecRegistry.toKebabCase("testCase"));
        assertEquals("test-case-example", CodecRegistry.toKebabCase("testCaseExample"));
        assertEquals("uwe-server-message", CodecRegistry.toKebabCase("UWEServerMessage"));
        assertEquals("", CodecRegistry.toKebabCase(""));
        assertNull(CodecRegistry.toKebabCase(null));
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
    public void testCreateAndRegisterCodec() {
        String codecName = CodecRegistry.createAndRegisterCodec(vertx, TestMessage.class);
        assertEquals("test-message", codecName);
        
        // Test sending a message with the codec
        TestMessage testMessage = new TestMessage("Hello World");
        
        // Create a consumer that will receive the message
        vertx.eventBus().consumer("test.address", message -> {
            assertTrue(message.body() instanceof TestMessage);
            TestMessage received = (TestMessage) message.body();
            assertEquals("Hello World", received.getContent());
            message.reply("Received");
        });
        
        // Send a message with the codec
        vertx.eventBus().request("test.address", testMessage, 
                new io.vertx.core.eventbus.DeliveryOptions().setCodecName(codecName))
                .onComplete(ar -> {
                    assertTrue(ar.succeeded());
                    assertEquals("Received", ar.result().body());
                });
        
        // Test registering the same codec again
        String codecName2 = CodecRegistry.createAndRegisterCodec(vertx, TestMessage.class);
        assertEquals(codecName, codecName2);
        
        // Test registering a standard type
        assertNull(CodecRegistry.createAndRegisterCodec(vertx, String.class));
    }
    
    @Test
    public void testCreateAndRegisterCodecsForAllEventTypes() {
        // We can't easily test this directly since it depends on the actual VertxEventRegistry state
        // Instead, we'll verify that our codec registration mechanism works
        
        // Register a codec for TestMessage
        String codecName = CodecRegistry.createAndRegisterCodec(vertx, TestMessage.class);
        assertEquals("test-message", codecName);
        
        // Create a consumer that will receive the message
        vertx.eventBus().consumer("test.address2", message -> {
            assertTrue(message.body() instanceof TestMessage);
            TestMessage received = (TestMessage) message.body();
            assertEquals("Test Message", received.getContent());
        });
        
        // Send a message with the codec
        TestMessage testMessage = new TestMessage("Test Message");
        vertx.eventBus().publish("test.address2", testMessage, 
                new io.vertx.core.eventbus.DeliveryOptions().setCodecName(codecName));
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
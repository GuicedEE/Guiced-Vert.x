package com.guicedee.vertx.spi.test;

import com.guicedee.vertx.spi.json.GuicedVertxJsonCodec;
import com.guicedee.vertx.spi.json.GuicedVertxJsonFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.JsonFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Vert.x is explicitly configured to use the GuicedEE Jackson 3
 * ({@code tools.jackson}) mapper for all of its JSON, instead of falling back to
 * Vert.x's Jackson 2 core-only codec (which throws
 * {@code "Mapping <type> is not available without Jackson Databind on the classpath"}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxJsonCodecTest
{
    /**
     * A plain POJO — the kind of object that previously failed to serialize through
     * Vert.x's Jackson 2 core-only fallback codec.
     */
    public static class Party
    {
        private String name;
        private int age;
        private boolean active;

        public Party()
        {
        }

        public Party(String name, int age, boolean active)
        {
            this.name = name;
            this.age = age;
            this.active = active;
        }

        public String getName()
        {
            return name;
        }

        public int getAge()
        {
            return age;
        }

        public boolean isActive()
        {
            return active;
        }
    }

    /**
     * The GuicedEE {@link JsonFactory} must be registered via the {@link ServiceLoader}
     * so Vert.x can discover it.
     */
    @Test
    public void factoryIsRegisteredViaServiceLoader()
    {
        boolean found = ServiceLoader.load(JsonFactory.class)
                .stream()
                .anyMatch(p -> p.type().equals(GuicedVertxJsonFactory.class));
        assertTrue(found, "GuicedVertxJsonFactory should be registered as a JsonFactory service provider");
    }

    /**
     * The factory must outrank Vert.x's default factory ({@link Integer#MAX_VALUE}) so it
     * is selected, and must expose the GuicedEE-backed codec.
     */
    @Test
    public void factoryWinsSelectionAndExposesCodec()
    {
        GuicedVertxJsonFactory factory = new GuicedVertxJsonFactory();
        assertEquals(Integer.MIN_VALUE, factory.order(), "Factory must have the lowest order to be preferred");
        assertInstanceOf(GuicedVertxJsonCodec.class, factory.codec());
    }

    /**
     * Vert.x's globally selected codec must be ours — proving the SPI override took effect
     * and Vert.x is not using its Jackson 2 fallback.
     */
    @Test
    public void vertxUsesGuicedCodec()
    {
        assertInstanceOf(GuicedVertxJsonCodec.class, Json.CODEC,
                "Vert.x Json.CODEC must be the GuicedEE Jackson 3 codec");
    }

    /**
     * The exact failure scenario: serialize a POJO through Vert.x {@code Json.encode}.
     * Previously this threw "Mapping ... is not available without Jackson Databind".
     */
    @Test
    public void encodesPojoThroughVertxJson()
    {
        Party party = new Party("Alice", 30, true);

        String json = Json.encode(party);
        assertTrue(json.contains("\"name\":\"Alice\""), json);
        assertTrue(json.contains("\"age\":30"), json);
        assertTrue(json.contains("\"active\":true"), json);

        Buffer buffer = Json.encodeToBuffer(party);
        assertNotNull(buffer);
        assertTrue(buffer.length() > 0);
    }

    /**
     * Round-trips a POJO through Vert.x's encode/decode using the GuicedEE codec.
     */
    @Test
    public void decodesPojoThroughVertxJson()
    {
        Party party = new Party("Bob", 42, false);
        String json = Json.encode(party);

        Party decoded = Json.decodeValue(json, Party.class);
        assertNotNull(decoded);
        assertEquals("Bob", decoded.getName());
        assertEquals(42, decoded.getAge());
        assertFalse(decoded.isActive());
    }

    /**
     * {@code JsonObject.mapFrom} / {@code JsonObject.mapTo} both route through the codec's
     * {@code fromValue}; verify the full round-trip works for POJOs.
     */
    @Test
    public void jsonObjectMapFromAndMapToRoundTrip()
    {
        Party party = new Party("Carol", 25, true);

        JsonObject jsonObject = JsonObject.mapFrom(party);
        assertEquals("Carol", jsonObject.getString("name"));
        assertEquals(25, jsonObject.getInteger("age"));
        assertTrue(jsonObject.getBoolean("active"));

        Party back = jsonObject.mapTo(Party.class);
        assertEquals("Carol", back.getName());
        assertEquals(25, back.getAge());
        assertTrue(back.isActive());
    }

    /**
     * Decoding to {@link Object} must adapt raw containers into Vert.x types, mirroring
     * Vert.x's own databind codec behaviour.
     */
    @Test
    public void decodeToObjectAdaptsContainers()
    {
        Object obj = Json.decodeValue("{\"a\":1}");
        assertInstanceOf(JsonObject.class, obj);
        assertEquals(1, ((JsonObject) obj).getInteger("a"));

        Object arr = Json.decodeValue("[1,2,3]");
        assertInstanceOf(JsonArray.class, arr);
        assertEquals(3, ((JsonArray) arr).size());
    }

    /**
     * Direct codec checks independent of SPI selection: {@code fromValue} converts a
     * {@link java.util.Map} to a POJO and a POJO to a {@link JsonObject}.
     */
    @Test
    public void codecFromValueConvertsBothDirections()
    {
        GuicedVertxJsonCodec codec = new GuicedVertxJsonCodec();

        JsonObject source = new JsonObject().put("name", "Dan").put("age", 18).put("active", false);
        Party party = codec.fromValue(source.getMap(), Party.class);
        assertEquals("Dan", party.getName());
        assertEquals(18, party.getAge());
        assertFalse(party.isActive());

        // POJO -> Object adapts the resulting Map into a JsonObject
        Object adapted = codec.fromValue(new Party("Eve", 50, true), Object.class);
        assertInstanceOf(JsonObject.class, adapted);
        assertEquals("Eve", ((JsonObject) adapted).getString("name"));
    }

    /**
     * The codec must serialize Vert.x container types ({@link JsonObject}, {@link JsonArray})
     * correctly via the registered {@code GuicedVertxJsonModule}.
     */
    @Test
    public void encodesVertxContainerTypes()
    {
        JsonObject jsonObject = new JsonObject().put("k", "v").put("n", 7);
        String objJson = Json.encode(jsonObject);
        assertEquals(jsonObject, new JsonObject(objJson));

        JsonArray jsonArray = new JsonArray(List.of("x", "y", "z"));
        String arrJson = Json.encode(jsonArray);
        assertEquals(jsonArray, new JsonArray(arrJson));
    }
}



package com.guicedee.vertx.spi.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.util.List;
import java.util.Map;

/**
 * Jackson 3 module that teaches the shared GuicedEE {@code ObjectMapper} how to
 * (de)serialize Vert.x container types so the mapper can act as the backing codec
 * for Vert.x's JSON facility.
 * <p>
 * Vert.x normally registers these handlers on its own internal mapper via its
 * {@code VertxModule}; because GuicedEE supplies its own {@code tools.jackson}
 * {@link tools.jackson.databind.ObjectMapper} to Vert.x (via the registered
 * {@code JsonFactory}), the equivalent handlers are registered here.
 * <ul>
 *     <li>{@link JsonObject} ⇄ JSON object (its underlying {@link Map})</li>
 *     <li>{@link JsonArray} ⇄ JSON array (its underlying {@link List})</li>
 *     <li>{@link Buffer} ⇄ base64-encoded binary</li>
 * </ul>
 */
public class GuicedVertxJsonModule extends SimpleModule
{
    /**
     * Registers the Vert.x type (de)serializers.
     */
    @SuppressWarnings("unchecked")
    public GuicedVertxJsonModule()
    {
        super("GuicedVertxTypes", Version.unknownVersion());

        addSerializer(JsonObject.class, new ValueSerializer<JsonObject>()
        {
            @Override
            public void serialize(JsonObject value, JsonGenerator gen, SerializationContext ctxt)
            {
                gen.writePOJO(value.getMap());
            }
        });
        addDeserializer(JsonObject.class, new ValueDeserializer<JsonObject>()
        {
            @Override
            public JsonObject deserialize(JsonParser p, DeserializationContext ctxt)
            {
                Map<String, Object> map = ctxt.readValue(p, Map.class);
                return map == null ? null : new JsonObject(map);
            }
        });

        addSerializer(JsonArray.class, new ValueSerializer<JsonArray>()
        {
            @Override
            public void serialize(JsonArray value, JsonGenerator gen, SerializationContext ctxt)
            {
                gen.writePOJO(value.getList());
            }
        });
        addDeserializer(JsonArray.class, new ValueDeserializer<JsonArray>()
        {
            @Override
            public JsonArray deserialize(JsonParser p, DeserializationContext ctxt)
            {
                List<Object> list = ctxt.readValue(p, List.class);
                return list == null ? null : new JsonArray(list);
            }
        });

        addSerializer(Buffer.class, new ValueSerializer<Buffer>()
        {
            @Override
            public void serialize(Buffer value, JsonGenerator gen, SerializationContext ctxt)
            {
                gen.writeBinary(value.getBytes());
            }
        });
        addDeserializer(Buffer.class, new ValueDeserializer<Buffer>()
        {
            @Override
            public Buffer deserialize(JsonParser p, DeserializationContext ctxt)
            {
                byte[] bytes = ctxt.readValue(p, byte[].class);
                return bytes == null ? null : Buffer.buffer(bytes);
            }
        });
    }
}




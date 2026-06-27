package com.guicedee.vertx.spi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;

/**
 * Message codec that serializes/deserializes payloads using the shared
 * Jackson mapper from {@link IJsonRepresentation}.
 * <p>
 * The codec retains the <em>full</em> generic {@link Type} it was created for (not just
 * the raw {@link Class}), so parameterized payloads such as {@code List<Dto>} or
 * {@code Map<String, Dto>} are reconstructed with their element types intact rather than
 * collapsing into {@code List<LinkedHashMap>} / {@code Map<String, LinkedHashMap>}.
 *
 * @param <T> The type of object this codec handles.
 */
@Log4j2
public class DynamicCodec<T> implements MessageCodec<T, T> {
    
    private final Type type;
    private final String codecName;
    
    /**
     * Creates a new dynamic codec for the given raw class.
     *
     * @param type The class of the type this codec handles.
     * @param codecName The name of the codec.
     */
    public DynamicCodec(Class<T> type, String codecName) {
        this((Type) type, codecName);
    }

    /**
     * Creates a new dynamic codec for the given (possibly generic) type.
     *
     * @param type The (possibly parameterized) type this codec handles.
     * @param codecName The name of the codec.
     */
    public DynamicCodec(Type type, String codecName) {
        this.type = type;
        this.codecName = codecName;
        log.debug("Created dynamic codec for type {} with name {}", type.getTypeName(), codecName);
    }

    /**
     * Resolves the Jackson {@link JavaType} for the retained generic type, capturing any
     * element/value type arguments so nested objects and lists are decoded with full fidelity.
     */
    private JavaType javaType() {
        return IJsonRepresentation.getObjectMapper().getTypeFactory().constructType(type);
    }
    
    @Override
    public void encodeToWire(Buffer buffer, T object) {
        try {
            buffer.appendString(IJsonRepresentation.getObjectMapper()
                    .writeValueAsString(object));
        } catch (JacksonException e) {
            log.error("Error encoding object to wire", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public T decodeFromWire(int pos, Buffer buffer) {
        String message = buffer.getString(pos, buffer.length(), "UTF-8");
        try {
            return IJsonRepresentation.getObjectMapper()
                    .readValue(message, javaType());
        } catch (JacksonException e) {
            log.error("Error decoding object from wire", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public T transform(T object) {
        if (object == null) {
            return null;
        }
        try {
            // Perform a deep copy by serializing and deserializing using the shared ObjectMapper.
            // Decoding through the full generic JavaType preserves nested object/list element types.
            byte[] json = IJsonRepresentation.getObjectMapper().writeValueAsBytes(object);
            return IJsonRepresentation.getObjectMapper()
                    .readValue(json, javaType());
        } catch (Exception e) {
            log.error("Error transforming object via serialize/deserialize for codec {}", codecName, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String name() {
        return codecName;
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}

package com.guicedee.vertx.spi;

import com.guicedee.client.utils.JsonRepresentation;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;

/**
 * Message codec that serializes/deserializes payloads using the shared
 * optional JSON representation SPI.
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
        log.trace("Created dynamic codec for type {} with name {}", type.getTypeName(), codecName);
    }

    
    @Override
    public void encodeToWire(Buffer buffer, T object) {
        try {
            buffer.appendString(JsonRepresentation.require().write(object));
        } catch (RuntimeException e) {
            log.error("Error encoding object to wire", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public T decodeFromWire(int pos, Buffer buffer) {
        String message = buffer.getString(pos, buffer.length(), "UTF-8");
        try {
            return JsonRepresentation.require().read(message, type);
        } catch (RuntimeException e) {
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
            // Retain the full generic type and preserve deep-copy semantics.
            return JsonRepresentation.require().copy(object, type);
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

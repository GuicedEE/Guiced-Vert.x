package com.guicedee.vertx.spi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import lombok.extern.log4j.Log4j2;

/**
 * A dynamic codec that can be instantiated for any type
 * @param <T> The type of object this codec handles
 */
@Log4j2
public class DynamicCodec<T> implements MessageCodec<T, T> {
    
    private final Class<T> type;
    private final String codecName;
    
    /**
     * Creates a new dynamic codec for the given type
     * @param type The class of the type this codec handles
     * @param codecName The name of the codec
     */
    public DynamicCodec(Class<T> type, String codecName) {
        this.type = type;
        this.codecName = codecName;
        log.debug("Created dynamic codec for type {} with name {}", type.getName(), codecName);
    }
    
    @Override
    public void encodeToWire(Buffer buffer, T object) {
        try {
            buffer.appendString(IJsonRepresentation.getObjectMapper()
                    .writeValueAsString(object));
        } catch (JsonProcessingException e) {
            log.error("Error encoding object to wire", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public T decodeFromWire(int pos, Buffer buffer) {
        String message = buffer.getString(pos, buffer.length(), "UTF-8");
        try {
            return IJsonRepresentation.getObjectMapper()
                    .readerFor(type)
                    .readValue(message);
        } catch (JsonProcessingException e) {
            log.error("Error decoding object from wire", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public T transform(T object) {
        return object;
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
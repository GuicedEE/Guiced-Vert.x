package com.guicedee.vertx.spi.json;

import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.json.JsonCodec;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Vert.x {@link JsonCodec} implementation backed by the GuicedEE-configured
 * Jackson 3 ({@code tools.jackson}) {@link ObjectMapper}.
 * <p>
 * Vert.x 5.1 ships a multi-release {@code JacksonFactory} that prefers a Jackson 2
 * {@code DatabindCodec}, then a Jackson 2 core-only {@code JacksonCodec}, and only
 * then the Jackson 3 codecs. When Jackson 2 <em>core</em> is present without Jackson 2
 * <em>databind</em>, Vert.x silently selects the core-only Jackson 2 codec, which cannot
 * map POJOs and fails with
 * {@code "Mapping <type> is not available without Jackson Databind on the classpath"}.
 * <p>
 * Registering this codec (via {@link GuicedVertxJsonFactory}) makes Vert.x route all of
 * its JSON through the same Jackson 3 mapper GuicedEE uses everywhere else, keeping
 * serialization consistent and avoiding the Jackson 2 fallback entirely.
 * <p>
 * The behaviour mirrors Vert.x's own {@code DatabindCodec}: when the requested type is
 * {@link Object}, raw {@link Map}/{@link List} results are wrapped into
 * {@link JsonObject}/{@link JsonArray}.
 */
public class GuicedVertxJsonCodec implements JsonCodec
{
    private static volatile ObjectMapper baseMapper;
    private static volatile ObjectMapper vertxMapper;

    /**
     * Returns the Vert.x-aware mapper derived from the shared GuicedEE mapper.
     * <p>
     * The shared mapper may be rebuilt at runtime (e.g. when plugins register extra
     * Jackson modules), so the derived mapper is rebuilt whenever the underlying shared
     * instance changes.
     *
     * @return a Jackson 3 mapper extended with Vert.x container-type handlers
     */
    private static ObjectMapper mapper()
    {
        ObjectMapper current = IJsonRepresentation.getObjectMapper();
        ObjectMapper derived = vertxMapper;
        if (derived == null || current != baseMapper)
        {
            synchronized (GuicedVertxJsonCodec.class)
            {
                if (vertxMapper == null || current != baseMapper)
                {
                    vertxMapper = current.rebuild()
                            .addModule(new GuicedVertxJsonModule())
                            .build();
                    baseMapper = current;
                }
                derived = vertxMapper;
            }
        }
        return derived;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromString(String json, Class<T> clazz) throws DecodeException
    {
        try
        {
            T value = mapper().readValue(json, clazz);
            return clazz == Object.class ? (T) adapt(value) : value;
        }
        catch (Exception e)
        {
            throw new DecodeException("Failed to decode: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromBuffer(Buffer buf, Class<T> clazz) throws DecodeException
    {
        try
        {
            T value = mapper().readValue(buf.getBytes(), clazz);
            return clazz == Object.class ? (T) adapt(value) : value;
        }
        catch (Exception e)
        {
            throw new DecodeException("Failed to decode: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromValue(Object json, Class<T> clazz)
    {
        T value = mapper().convertValue(json, clazz);
        return clazz == Object.class ? (T) adapt(value) : value;
    }

    @Override
    public String toString(Object object, boolean pretty) throws EncodeException
    {
        try
        {
            ObjectMapper m = mapper();
            return pretty
                    ? m.writerWithDefaultPrettyPrinter().writeValueAsString(object)
                    : m.writeValueAsString(object);
        }
        catch (Exception e)
        {
            throw new EncodeException("Failed to encode as JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public Buffer toBuffer(Object object, boolean pretty) throws EncodeException
    {
        try
        {
            ObjectMapper m = mapper();
            byte[] bytes = pretty
                    ? m.writerWithDefaultPrettyPrinter().writeValueAsBytes(object)
                    : m.writeValueAsBytes(object);
            return Buffer.buffer(bytes);
        }
        catch (Exception e)
        {
            throw new EncodeException("Failed to encode as JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps raw container results into their Vert.x equivalents, matching the behaviour
     * of Vert.x's built-in databind codec when decoding to {@link Object}.
     *
     * @param o the decoded value
     * @return a {@link JsonObject}/{@link JsonArray} for {@link Map}/{@link List}; otherwise the value unchanged
     */
    @SuppressWarnings("unchecked")
    private static Object adapt(Object o)
    {
        if (o instanceof List<?> list)
        {
            return new JsonArray(list);
        }
        if (o instanceof Map<?, ?> map)
        {
            return new JsonObject((Map<String, Object>) map);
        }
        return o;
    }
}





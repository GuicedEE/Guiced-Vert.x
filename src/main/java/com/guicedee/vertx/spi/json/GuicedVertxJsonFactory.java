package com.guicedee.vertx.spi.json;

import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Vert.x {@link JsonFactory} SPI provider that supplies a {@link GuicedVertxJsonCodec}
 * backed by the GuicedEE Jackson 3 ({@code tools.jackson}) mapper.
 * <p>
 * Vert.x selects the registered {@link JsonFactory} with the <em>lowest</em>
 * {@link #order()} (see {@code io.vertx.core.spi.Utils#load()}); the default
 * {@code JacksonFactory} uses {@link Integer#MAX_VALUE}. Returning
 * {@link Integer#MIN_VALUE} therefore guarantees this factory wins, so all Vert.x
 * JSON ({@code Json.encode}/{@code decode}, {@code JsonObject.mapTo}/{@code mapFrom},
 * event-bus payloads, etc.) flows through GuicedEE's Jackson 3 mapper.
 * <p>
 * Registered via {@code provides io.vertx.core.spi.JsonFactory} in {@code module-info}
 * (JPMS) and {@code META-INF/services} (class-path) so it is discovered by the
 * {@link java.util.ServiceLoader} Vert.x uses internally.
 */
public class GuicedVertxJsonFactory implements JsonFactory
{
    private static final JsonCodec CODEC = new GuicedVertxJsonCodec();

    /**
     * Public no-arg constructor required for {@link java.util.ServiceLoader} instantiation.
     */
    public GuicedVertxJsonFactory()
    {
    }

    /**
     * @return {@link Integer#MIN_VALUE} so this factory is preferred over Vert.x's default
     */
    @Override
    public int order()
    {
        return Integer.MIN_VALUE;
    }

    /**
     * @return the GuicedEE Jackson 3 backed codec
     */
    @Override
    public JsonCodec codec()
    {
        return CODEC;
    }
}


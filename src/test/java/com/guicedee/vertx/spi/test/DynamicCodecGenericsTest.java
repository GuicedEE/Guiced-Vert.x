package com.guicedee.vertx.spi.test;

import com.google.inject.TypeLiteral;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.spi.CodecRegistry;
import com.guicedee.vertx.spi.DynamicCodec;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the {@link DynamicCodec} / {@link CodecRegistry} reconstruct generic
 * payloads ({@code List<Dto>}, {@code Map<String, Dto>}) and nested objects with their
 * element types intact, rather than collapsing them into {@code LinkedHashMap}s.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DynamicCodecGenericsTest {

    private Vertx vertx;

    @BeforeAll
    public void setUp() {
        IGuiceContext.instance().getConfig()
                .setFieldScanning(true).setClasspathScanning(true)
                .setAnnotationScanning(true).setIgnoreClassVisibility(true);
        IGuiceContext.instance().inject();
        vertx = VertXPreStartup.getVertx();
        assertNotNull(vertx, "Vertx must be initialized for tests");
    }

    @Test
    public void getCodecNameFoldsGenericTypeArguments() {
        Type listType = new TypeLiteral<List<Dto>>() {}.getType();
        Type mapType = new TypeLiteral<Map<String, Dto>>() {}.getType();

        // Names are derived from the declared type (not the runtime ArrayList/LinkedHashMap),
        // and include the element types so different payloads get distinct codecs.
        assertEquals("list-dto", CodecRegistry.getCodecName(listType));
        assertEquals("map-string-dto", CodecRegistry.getCodecName(mapType));

        // Raw class behaviour is unchanged.
        assertEquals("dto", CodecRegistry.getCodecName((Type) Dto.class));
        assertNull(CodecRegistry.getCodecName((Type) null));
    }

    @Test
    public void codecPreservesListElementTypes() {
        Type listType = new TypeLiteral<List<Dto>>() {}.getType();
        DynamicCodec<List<Dto>> codec = new DynamicCodec<>(listType, "list-dto");

        List<Dto> original = List.of(new Dto("a", 1), new Dto("b", 2));

        // Wire round-trip (clustered path)
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);
        List<Dto> decoded = codec.decodeFromWire(0, buffer);
        assertEquals(2, decoded.size());
        assertInstanceOf(Dto.class, decoded.get(0), "List element must remain a Dto, not a Map");
        assertEquals("a", decoded.get(0).getName());
        assertEquals(2, decoded.get(1).getValue());

        // transform() round-trip (local same-JVM delivery)
        List<Dto> transformed = codec.transform(original);
        assertInstanceOf(Dto.class, transformed.get(0), "transform must keep Dto element types");
        assertEquals("b", transformed.get(1).getName());
    }

    @Test
    public void codecPreservesNestedObjectsAndLists() {
        DynamicCodec<Nested> codec = new DynamicCodec<>(Nested.class, "nested");

        Nested original = new Nested("n1", new Dto("primary", 9),
                List.of(new Dto("x", 1), new Dto("y", 2)));

        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);
        Nested decoded = codec.decodeFromWire(0, buffer);

        assertEquals("n1", decoded.getId());
        assertInstanceOf(Dto.class, decoded.getPrimary());
        assertEquals("primary", decoded.getPrimary().getName());
        assertEquals(2, decoded.getItems().size());
        assertInstanceOf(Dto.class, decoded.getItems().get(0));
        assertEquals("y", decoded.getItems().get(1).getName());
    }

    @Test
    public void listPayloadPreservesElementTypesOverBus() throws Exception {
        Type listType = new TypeLiteral<List<Dto>>() {}.getType();
        String codecName = CodecRegistry.createAndRegisterCodec(vertx, listType);
        assertEquals("list-dto", codecName);

        CompletableFuture<Object> received = new CompletableFuture<>();
        var consumer = vertx.eventBus().consumer("test.list", message -> received.complete(message.body()));
        consumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        List<Dto> payload = List.of(new Dto("a", 1), new Dto("b", 2));
        vertx.eventBus().publish("test.list", payload, new DeliveryOptions().setCodecName(codecName));

        Object body = received.get(10, TimeUnit.SECONDS);
        assertInstanceOf(List.class, body);
        List<?> list = (List<?>) body;
        assertEquals(2, list.size());
        assertInstanceOf(Dto.class, list.get(0), "Local delivery must preserve Dto element types");
        assertEquals("a", ((Dto) list.get(0)).getName());
    }

    /**
     * Simple element type used to detect generic-erasure (Map vs Dto) on decode.
     */
    public static class Dto {
        private String name;
        private int value;

        public Dto() {
        }

        public Dto(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    /**
     * A POJO carrying a nested object and a nested list to validate deep reconstruction.
     */
    public static class Nested {
        private String id;
        private Dto primary;
        private List<Dto> items;

        public Nested() {
        }

        public Nested(String id, Dto primary, List<Dto> items) {
            this.id = id;
            this.primary = primary;
            this.items = items;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Dto getPrimary() {
            return primary;
        }

        public void setPrimary(Dto primary) {
            this.primary = primary;
        }

        public List<Dto> getItems() {
            return items;
        }

        public void setItems(List<Dto> items) {
            this.items = items;
        }
    }
}


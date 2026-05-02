package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.grpc.GrpcConnectionInfo;
import com.guicedee.vertx.grpc.GrpcModule;
import com.guicedee.vertx.grpc.GrpcOptions;
import com.guicedee.vertx.grpc.GrpcPreStartup;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.client.GrpcClient;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the gRPC module.
 * <p>
 * Verifies that the GrpcServer and GrpcClient are properly bound in Guice.
 * This test validates the module wiring and Guice bindings without requiring
 * actual protobuf services.
 */
@Log4j2
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrpcTest {

    @BeforeAll
    static void setUp() {
        IGuiceContext.modules.add(new TestGrpcModule());
        IGuiceContext.instance().inject();
    }

    @AfterAll
    static void tearDown() {
        IGuiceContext.instance().destroy();
    }

    @Test
    @Order(1)
    void testGrpcServerBound() {
        GrpcServer server = IGuiceContext.get(GrpcServer.class);
        assertNotNull(server, "GrpcServer should be bound in Guice");
        log.info("✅ GrpcServer bound successfully");
    }

    @Test
    @Order(2)
    void testGrpcClientBound() {
        GrpcClient client = IGuiceContext.get(GrpcClient.class);
        assertNotNull(client, "GrpcClient should be bound in Guice");
        log.info("✅ GrpcClient bound successfully");
    }

    @Test
    @Order(3)
    void testGrpcConnectionInfo() {
        TestGrpcModule module = new TestGrpcModule();
        GrpcConnectionInfo info = module.getGrpcConnectionInfo();
        assertEquals("test-grpc", info.getName());
        assertEquals("127.0.0.1", info.getHost());
        assertEquals(50051, info.getPort());
        assertFalse(info.isTlsEnabled());
        assertTrue(info.isGrpcWebEnabled());
        assertTrue(info.isDefaultConnection());
        log.info("✅ GrpcConnectionInfo configured correctly");
    }

    @Test
    @Order(4)
    void testAnnotationBuildsConnectionInfo() {
        GrpcOptions opts = AnnotatedGrpcConfig.class.getAnnotation(GrpcOptions.class);
        assertNotNull(opts, "@GrpcOptions annotation should be present");

        GrpcConnectionInfo info = GrpcPreStartup.buildFromAnnotation(opts);
        assertEquals("annotated-grpc", info.getName());
        assertEquals(50052, info.getPort());
        assertTrue(info.isScheduleDeadlineAutomatically());
        assertTrue(info.isDeadlinePropagation());
        assertFalse(info.isDefaultConnection());
        log.info("✅ Annotation-based GrpcConnectionInfo built correctly");
    }

    @Test
    @Order(5)
    void testAnnotationEnvPlaceholderResolution() {
        String resolved = GrpcPreStartup.resolveEnvPlaceholders("${GRPC_TEST_HOST:myhost}:${GRPC_TEST_PORT:9999}");
        assertEquals("myhost:9999", resolved);
        log.info("✅ Environment placeholder resolution works correctly");
    }

    /**
     * Class annotated with @GrpcOptions for testing annotation processing.
     */
    @GrpcOptions(
            name = "annotated-grpc",
            port = 50052,
            scheduleDeadlineAutomatically = true,
            deadlinePropagation = true,
            defaultConnection = false
    )
    static class AnnotatedGrpcConfig {
    }

    /**
     * Test gRPC module connecting to localhost.
     */
    public static class TestGrpcModule extends GrpcModule<TestGrpcModule> {
        @Override
        public GrpcConnectionInfo getGrpcConnectionInfo() {
            return new GrpcConnectionInfo()
                    .setName("test-grpc")
                    .setHost("127.0.0.1")
                    .setPort(50051)
                    .setGrpcWebEnabled(true)
                    .setTranscodingEnabled(true)
                    .setDefaultConnection(true);
        }
    }
}


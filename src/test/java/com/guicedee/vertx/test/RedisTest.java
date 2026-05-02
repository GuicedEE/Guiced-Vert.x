package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.redis.RedisConnectionInfo;
import com.guicedee.vertx.redis.RedisModule;
import com.guicedee.vertx.redis.RedisOptions;
import com.guicedee.vertx.redis.RedisPreStartup;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Redis module.
 * <p>
 * Verifies that the Redis client and RedisAPI are properly bound in Guice.
 * Note: actual Redis commands require a running Redis server; this test
 * validates the module wiring and Guice bindings.
 */
@Log4j2
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisTest {

    @BeforeAll
    static void setUp() {
        IGuiceContext.modules.add(new TestRedisModule());
        IGuiceContext.instance().inject();
    }

    @AfterAll
    static void tearDown() {
        IGuiceContext.instance().destroy();
    }

    @Test
    @Order(1)
    void testRedisClientBound() {
        Redis redis = IGuiceContext.get(Redis.class);
        assertNotNull(redis, "Redis client should be bound in Guice");
        log.info("✅ Redis client bound successfully");
    }

    @Test
    @Order(2)
    void testRedisApiBound() {
        RedisAPI api = IGuiceContext.get(RedisAPI.class);
        assertNotNull(api, "RedisAPI should be bound in Guice");
        log.info("✅ RedisAPI bound successfully");
    }

    @Test
    @Order(3)
    void testRedisConnectionInfo() {
        TestRedisModule module = new TestRedisModule();
        RedisConnectionInfo info = module.getRedisConnectionInfo();
        assertEquals("test-redis", info.getName());
        assertEquals("redis://localhost:6379/0", info.getConnectionString());
        assertEquals(RedisConnectionInfo.RedisMode.STANDALONE, info.getRedisMode());
        assertEquals(4, info.getMaxPoolSize());
        log.info("✅ RedisConnectionInfo configured correctly");
    }

    @Test
    @Order(4)
    void testAnnotationBuildsConnectionInfo() {
        // Simulate annotation processing
        RedisOptions opts = AnnotatedRedisConfig.class.getAnnotation(RedisOptions.class);
        assertNotNull(opts, "@RedisOptions annotation should be present");

        RedisConnectionInfo info = RedisPreStartup.buildFromAnnotation(opts);
        assertEquals("annotated-cache", info.getName());
        assertEquals("redis://localhost:6379/2", info.getConnectionString());
        assertEquals(RedisConnectionInfo.RedisMode.STANDALONE, info.getRedisMode());
        assertEquals(12, info.getMaxPoolSize());
        assertFalse(info.isDefaultConnection());
        log.info("✅ Annotation-based RedisConnectionInfo built correctly");
    }

    @Test
    @Order(5)
    void testAnnotationEnvPlaceholderResolution() {
        // Test placeholder resolution
        String resolved = RedisPreStartup.resolveEnvPlaceholders("redis://${REDIS_TEST_HOST:myhost}:${REDIS_TEST_PORT:9999}/0");
        assertEquals("redis://myhost:9999/0", resolved);
        log.info("✅ Environment placeholder resolution works correctly");
    }

    /**
     * Class annotated with @RedisOptions for testing annotation processing.
     */
    @RedisOptions(
            name = "annotated-cache",
            connectionString = "redis://localhost:6379/2",
            maxPoolSize = 12,
            defaultConnection = false
    )
    static class AnnotatedRedisConfig {
    }

    /**
     * Test Redis module connecting to localhost (connection may fail but binding should succeed).
     */
    public static class TestRedisModule extends RedisModule<TestRedisModule> {
        @Override
        public RedisConnectionInfo getRedisConnectionInfo() {
            return new RedisConnectionInfo()
                    .setName("test-redis")
                    .setConnectionString("redis://localhost:6379/0")
                    .setMaxPoolSize(4)
                    .setMaxWaitingHandlers(16)
                    .setDefaultConnection(true);
        }
    }
}




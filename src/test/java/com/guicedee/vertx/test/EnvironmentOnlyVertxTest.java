package com.guicedee.vertx.test;

import com.guicedee.client.Environment;
import com.guicedee.vertx.spi.VertXPreStartup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentOnlyVertxTest {
    @TempDir Path directory;

    @Test void configuresPoolsWithoutAnnotationsAndPreservesPrecedence() throws Exception {
        Properties original = (Properties) System.getProperties().clone();
        var startup = new VertXPreStartup();
        try {
            startup.onDestroy();
            System.getProperties().keySet().removeIf(key -> key.toString().startsWith("VERTX_"));
            System.setProperty("user.dir", directory.toString());
            System.setProperty("GUICEDEE_DISCOVERY_MODE", "spi");
            Files.writeString(directory.resolve(".env"), "VERTX_EVENT_LOOP_POOL_SIZE=2\nVERTX_WORKER_POOL_SIZE=5\n");
            Files.writeString(directory.resolve(".env.local"), "VERTX_WORKER_POOL_SIZE=7\n");
            Environment.reloadDotEnv();
            System.setProperty("VERTX_EVENT_LOOP_POOL_SIZE", "3");
            EnvironmentOptionsObserver.observed = null;
            for (var future : startup.onStartup()) future.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertNotNull(EnvironmentOptionsObserver.observed);
            assertEquals(3, EnvironmentOptionsObserver.observed.getEventLoopPoolSize());
            assertEquals(7, EnvironmentOptionsObserver.observed.getWorkerPoolSize());
        } finally {
            try { startup.onDestroy(); }
            finally {
                System.setProperties(original);
                Environment.reloadDotEnv();
            }
        }
    }
}

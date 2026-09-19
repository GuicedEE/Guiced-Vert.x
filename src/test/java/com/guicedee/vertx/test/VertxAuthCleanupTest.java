package com.guicedee.vertx.test;

import com.guicedee.vertx.auth.VertxAuthPreDestroy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class VertxAuthCleanupTest {
    @Test void cleanupToleratesAbsentOptionalApisAndCanRepeat() {
        var cleanup = new VertxAuthPreDestroy();
        assertDoesNotThrow(cleanup::onDestroy);
        assertDoesNotThrow(cleanup::onDestroy);
    }
}

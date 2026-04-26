package com.guicedee.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.auth.htpasswd.HtpasswdAuth;
import io.vertx.ext.auth.htpasswd.HtpasswdAuthOptions;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the htpasswd authentication provider.
 * Uses plain-text mode for test simplicity.
 */
class HtpasswdAuthTest
{
    private static Vertx vertx;
    private static HtpasswdAuth htpasswdAuth;

    @BeforeAll
    static void setUp()
    {
        vertx = Vertx.vertx();
        htpasswdAuth = HtpasswdAuth.create(vertx, new HtpasswdAuthOptions()
                .setHtpasswdFile("src/test/resources/test.htpasswd")
                .setPlainTextEnabled(true));
    }

    @AfterAll
    static void tearDown()
    {
        if (vertx != null)
        {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Test
    void validUserAuthenticates()
    {
        var user = htpasswdAuth.authenticate(new UsernamePasswordCredentials("testuser", "testpass"))
                .toCompletionStage().toCompletableFuture().join();

        assertNotNull(user);
    }

    @Test
    void wrongPasswordFails()
    {
        assertThrows(Exception.class, () ->
                htpasswdAuth.authenticate(new UsernamePasswordCredentials("testuser", "wrong"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void unknownUserFails()
    {
        assertThrows(Exception.class, () ->
                htpasswdAuth.authenticate(new UsernamePasswordCredentials("nobody", "testpass"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void secondUserAuthenticates()
    {
        var user = htpasswdAuth.authenticate(new UsernamePasswordCredentials("admin", "adminpass"))
                .toCompletionStage().toCompletableFuture().join();

        assertNotNull(user);
    }
}


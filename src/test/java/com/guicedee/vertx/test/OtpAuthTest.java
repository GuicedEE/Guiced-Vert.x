package com.guicedee.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.otp.OtpCredentials;
import io.vertx.ext.auth.otp.OtpKey;
import io.vertx.ext.auth.otp.OtpKeyGenerator;
import io.vertx.ext.auth.otp.Authenticator;
import io.vertx.ext.auth.otp.totp.TotpAuth;
import io.vertx.ext.auth.otp.totp.TotpAuthOptions;
import io.vertx.core.Future;
import org.junit.jupiter.api.*;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OTP (TOTP) authentication provider.
 * Uses an in-memory authenticator store.
 */
class OtpAuthTest
{
    private static Vertx vertx;
    private static TotpAuth totpAuth;
    private static final ConcurrentHashMap<String, Authenticator> store = new ConcurrentHashMap<>();

    @BeforeAll
    static void setUp()
    {
        vertx = Vertx.vertx();
        totpAuth = TotpAuth.create(new TotpAuthOptions()
                .setPasswordLength(6)
                .setPeriod(30));

        totpAuth.authenticatorFetcher(id -> {
            Authenticator auth = store.get(id);
            return Future.succeededFuture(auth);
        });

        totpAuth.authenticatorUpdater(auth -> {
            store.put(auth.getIdentifier(), auth);
            return Future.succeededFuture();
        });
    }

    @AfterAll
    static void tearDown()
    {
        if (vertx != null)
        {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @BeforeEach
    void resetStore()
    {
        store.clear();
    }

    @Test
    void keyGeneratorCreatesValidKey()
    {
        OtpKey key = OtpKeyGenerator.create().generate();
        assertNotNull(key);
        assertNotNull(key.getKey());
        assertFalse(key.getKey().isBlank());
    }

    @Test
    void createAuthenticatorAndGenerateUri()
    {
        OtpKey key = OtpKeyGenerator.create().generate();

        var authenticator = totpAuth.createAuthenticator("user-1", key)
                .toCompletionStage().toCompletableFuture().join();

        assertNotNull(authenticator);
        assertEquals("user-1", authenticator.getIdentifier());
        assertNotNull(authenticator.getKey());

        // Verify stored
        assertTrue(store.containsKey("user-1"));

        // Generate URI for QR code
        String uri = totpAuth.generateUri(key, "TestApp", "user@test.com");
        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret="));
    }

    @Test
    void wrongCodeFails()
    {
        OtpKey key = OtpKeyGenerator.create().generate();

        totpAuth.createAuthenticator("user-2", key)
                .toCompletionStage().toCompletableFuture().join();

        // Use an obviously wrong code
        assertThrows(Exception.class, () ->
                totpAuth.authenticate(new OtpCredentials("user-2", "000000"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void unknownUserFails()
    {
        assertThrows(Exception.class, () ->
                totpAuth.authenticate(new OtpCredentials("nonexistent", "123456"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void multipleUsersIsolated()
    {
        OtpKey key1 = OtpKeyGenerator.create().generate();
        OtpKey key2 = OtpKeyGenerator.create().generate();

        totpAuth.createAuthenticator("alice", key1)
                .toCompletionStage().toCompletableFuture().join();
        totpAuth.createAuthenticator("bob", key2)
                .toCompletionStage().toCompletableFuture().join();

        assertEquals(2, store.size());
        assertNotEquals(store.get("alice").getKey(), store.get("bob").getKey());
    }
}


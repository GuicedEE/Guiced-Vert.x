package com.guicedee.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the JWT authentication provider integration.
 * Uses the Vert.x JWT API directly with a test keystore.
 */
class JwtAuthTest
{
    private static Vertx vertx;
    private static JWTAuth jwtAuth;

    @BeforeAll
    static void setUp()
    {
        vertx = Vertx.vertx();
        var opts = new JWTAuthOptions()
                .setKeyStore(new KeyStoreOptions()
                        .setPath("src/test/resources/test-keystore.jceks")
                        .setType("jceks")
                        .setPassword("secret"));
        jwtAuth = JWTAuth.create(vertx, opts);
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
    void generateAndVerifyToken()
    {
        String token = jwtAuth.generateToken(
                new JsonObject().put("sub", "testuser").put("name", "Test User"),
                new JWTOptions().setAlgorithm("HS256").setExpiresInSeconds(60));

        assertNotNull(token);
        assertFalse(token.isBlank());

        // Token should have 3 parts (header.payload.signature)
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have header.payload.signature");

        // Verify the token authenticates successfully
        var result = jwtAuth.authenticate(new TokenCredentials(token))
                .toCompletionStage().toCompletableFuture().join();

        assertNotNull(result);
        assertEquals("testuser", result.subject());
    }

    @Test
    void invalidTokenFails()
    {
        assertThrows(Exception.class, () ->
                jwtAuth.authenticate(new TokenCredentials("not.a.valid.token"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void tokenWithCustomClaims()
    {
        var claims = new JsonObject()
                .put("sub", "claimuser")
                .put("roles", new io.vertx.core.json.JsonArray().add("admin").add("user"))
                .put("email", "test@example.com");

        String token = jwtAuth.generateToken(claims,
                new JWTOptions().setAlgorithm("HS256").setExpiresInSeconds(300));

        var user = jwtAuth.authenticate(new TokenCredentials(token))
                .toCompletionStage().toCompletableFuture().join();

        assertNotNull(user);
        assertEquals("claimuser", user.subject());
        assertNotNull(user.principal());
    }

    @Test
    void tokenWithIssuerAndAudience()
    {
        // Create provider with issuer/audience validation
        var strictOpts = new JWTAuthOptions()
                .setKeyStore(new KeyStoreOptions()
                        .setPath("src/test/resources/test-keystore.jceks")
                        .setType("jceks")
                        .setPassword("secret"))
                .setJWTOptions(new JWTOptions()
                        .setIssuer("test-issuer")
                        .addAudience("test-service"));

        var strictAuth = JWTAuth.create(vertx, strictOpts);

        // Generate with matching issuer/audience
        String token = strictAuth.generateToken(
                new JsonObject().put("sub", "aud-user"),
                new JWTOptions()
                        .setAlgorithm("HS256")
                        .setIssuer("test-issuer")
                        .addAudience("test-service")
                        .setExpiresInSeconds(60));

        var user = strictAuth.authenticate(new TokenCredentials(token))
                .toCompletionStage().toCompletableFuture().join();
        assertNotNull(user);
        assertEquals("aud-user", user.subject());
    }
}




package com.guicedee.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.abac.Attribute;
import io.vertx.ext.auth.abac.Policy;
import io.vertx.ext.auth.abac.PolicyBasedAuthorizationProvider;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.WildcardPermissionBasedAuthorization;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ABAC (Attribute-Based Access Control) authorization provider.
 */
class AbacAuthTest
{
    private static Vertx vertx;
    private static JWTAuth jwtAuth;

    @BeforeAll
    static void setUp()
    {
        vertx = Vertx.vertx();
        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .setKeyStore(new KeyStoreOptions()
                        .setPath("src/test/resources/test-keystore.jceks")
                        .setType("jceks")
                        .setPassword("secret")));
    }

    @AfterAll
    static void tearDown()
    {
        if (vertx != null)
        {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    private static User createUser(JsonObject claims)
    {
        String token = jwtAuth.generateToken(claims,
                new JWTOptions().setAlgorithm("HS256").setExpiresInSeconds(300));
        return jwtAuth.authenticate(new TokenCredentials(token))
                .toCompletionStage().toCompletableFuture().join();
    }

    @Test
    void policyFromJson()
    {
        var policyJson = new JsonObject()
                .put("name", "MFA DELETE")
                .put("attributes", new JsonObject()
                        .put("/principal/amr", new JsonObject().put("eq", "mfa")))
                .put("authorizations", new JsonArray()
                        .add(new JsonObject()
                                .put("type", "wildcard")
                                .put("permission", "web:DELETE")));

        Policy policy = new Policy(policyJson);
        assertEquals("MFA DELETE", policy.getName());
        assertNotNull(policy.getAttributes());
        assertNotNull(policy.getAuthorizations());
        assertFalse(policy.getAttributes().isEmpty());
        assertFalse(policy.getAuthorizations().isEmpty());
    }

    @Test
    void policyMatchesUserAttributes()
    {
        var provider = PolicyBasedAuthorizationProvider.create();

        provider.addPolicy(new Policy(new JsonObject()
                .put("name", "Admin policy")
                .put("attributes", new JsonObject()
                        .put("/principal/role", new JsonObject().put("eq", "admin")))
                .put("authorizations", new JsonArray()
                        .add(new JsonObject()
                                .put("type", "wildcard")
                                .put("permission", "admin:*")))));

        var user = createUser(new JsonObject().put("sub", "admin-user").put("role", "admin"));

        provider.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        assertTrue(WildcardPermissionBasedAuthorization.create("admin:read").match(user));
        assertTrue(WildcardPermissionBasedAuthorization.create("admin:write").match(user));
    }

    @Test
    void policyDoesNotMatchWrongAttributes()
    {
        var provider = PolicyBasedAuthorizationProvider.create();

        provider.addPolicy(new Policy(new JsonObject()
                .put("name", "Admin only")
                .put("attributes", new JsonObject()
                        .put("/principal/role", new JsonObject().put("eq", "admin")))
                .put("authorizations", new JsonArray()
                        .add(new JsonObject()
                                .put("type", "permission")
                                .put("permission", "secret:access")))));

        var user = createUser(new JsonObject().put("sub", "viewer-user").put("role", "viewer"));

        provider.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        assertFalse(PermissionBasedAuthorization.create("secret:access").match(user));
    }

    @Test
    void multiplePoliciesAccumulate()
    {
        var provider = PolicyBasedAuthorizationProvider.create();

        provider.addPolicy(new Policy(new JsonObject()
                .put("name", "Read policy")
                .put("authorizations", new JsonArray()
                        .add(new JsonObject()
                                .put("type", "permission")
                                .put("permission", "data:read")))));

        provider.addPolicy(new Policy(new JsonObject()
                .put("name", "Write policy")
                .put("authorizations", new JsonArray()
                        .add(new JsonObject()
                                .put("type", "permission")
                                .put("permission", "data:write")))));

        var user = createUser(new JsonObject().put("sub", "rw-user"));

        provider.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        assertTrue(PermissionBasedAuthorization.create("data:read").match(user));
        assertTrue(PermissionBasedAuthorization.create("data:write").match(user));
    }

    @Test
    void programmaticPolicyWithCustomAttribute()
    {
        var provider = PolicyBasedAuthorizationProvider.create();

        var policy = new Policy()
                .setName("Custom attribute policy")
                .addAttribute(Attribute.create(user ->
                        "localhost".equals(user.principal().getString("origin"))))
                .addAuthorization(PermissionBasedAuthorization.create("local:access"));

        provider.addPolicy(policy);

        // Matching user
        var localUser = createUser(new JsonObject().put("sub", "local").put("origin", "localhost"));

        provider.getAuthorizations(localUser)
                .toCompletionStage().toCompletableFuture().join();

        assertTrue(PermissionBasedAuthorization.create("local:access").match(localUser));

        // Non-matching user
        var remoteUser = createUser(new JsonObject().put("sub", "remote").put("origin", "remote-host"));

        provider.getAuthorizations(remoteUser)
                .toCompletionStage().toCompletableFuture().join();

        assertFalse(PermissionBasedAuthorization.create("local:access").match(remoteUser));
    }

    @Test
    void policyToJsonRoundTrip()
    {
        var original = new Policy()
                .setName("Roundtrip test")
                .addAttribute(Attribute.eq("/principal/status", "active"))
                .addAuthorization(PermissionBasedAuthorization.create("account:view"));

        JsonObject json = original.toJson();
        assertNotNull(json);
        assertEquals("Roundtrip test", json.getString("name"));

        // Reconstruct from JSON
        var reconstructed = new Policy(json);
        assertEquals(original.getName(), reconstructed.getName());
        assertNotNull(reconstructed.getAttributes());
        assertNotNull(reconstructed.getAuthorizations());
    }

    @Test
    void clearRemovesPolicies()
    {
        var provider = PolicyBasedAuthorizationProvider.create();

        provider.addPolicy(new Policy(new JsonObject()
                .put("name", "Temp")
                .put("authorizations", new JsonArray()
                        .add(new JsonObject()
                                .put("type", "permission")
                                .put("permission", "temp:access")))));

        provider.clear();

        var user = createUser(new JsonObject().put("sub", "cleared"));
        provider.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        assertFalse(PermissionBasedAuthorization.create("temp:access").match(user));
    }
}

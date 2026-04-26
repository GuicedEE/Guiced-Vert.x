package com.guicedee.vertx.test;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.authorization.WildcardPermissionBasedAuthorization;
import io.vertx.ext.auth.properties.PropertyFileAuthentication;
import io.vertx.ext.auth.properties.PropertyFileAuthorization;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the property-file authentication and authorization provider.
 * Uses Shiro-format properties file.
 */
class PropertyFileAuthTest
{
    private static Vertx vertx;
    private static PropertyFileAuthentication authn;
    private static PropertyFileAuthorization authz;

    @BeforeAll
    static void setUp()
    {
        vertx = Vertx.vertx();
        authn = PropertyFileAuthentication.create(vertx, "src/test/resources/test-auth.properties");
        authz = PropertyFileAuthorization.create(vertx, "src/test/resources/test-auth.properties");
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
        var user = authn.authenticate(new UsernamePasswordCredentials("tim", "mypassword"))
                .toCompletionStage().toCompletableFuture().join();

        assertNotNull(user);
    }

    @Test
    void wrongPasswordFails()
    {
        assertThrows(Exception.class, () ->
                authn.authenticate(new UsernamePasswordCredentials("tim", "wrongpassword"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void unknownUserFails()
    {
        assertThrows(Exception.class, () ->
                authn.authenticate(new UsernamePasswordCredentials("unknown", "password"))
                        .toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void administratorHasWildcardPermission()
    {
        var user = authn.authenticate(new UsernamePasswordCredentials("tim", "mypassword"))
                .toCompletionStage().toCompletableFuture().join();

        authz.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        // administrator role has wildcard permission (*)
        assertTrue(WildcardPermissionBasedAuthorization.create("anything").match(user));
    }

    @Test
    void developerHasSpecificPermission()
    {
        var user = authn.authenticate(new UsernamePasswordCredentials("bob", "hispassword"))
                .toCompletionStage().toCompletableFuture().join();

        authz.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        assertTrue(PermissionBasedAuthorization.create("do_actual_work").match(user));
    }

    @Test
    void managerPermissions()
    {
        var user = authn.authenticate(new UsernamePasswordCredentials("joe", "anotherpassword"))
                .toCompletionStage().toCompletableFuture().join();

        authz.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        assertTrue(PermissionBasedAuthorization.create("play_golf").match(user));
        assertTrue(PermissionBasedAuthorization.create("say_buzzwords").match(user));
    }

    @Test
    void multipleRolesOnUser()
    {
        // tim has both administrator and developer roles
        var user = authn.authenticate(new UsernamePasswordCredentials("tim", "mypassword"))
                .toCompletionStage().toCompletableFuture().join();

        authz.getAuthorizations(user)
                .toCompletionStage().toCompletableFuture().join();

        // developer permission should also work since tim has developer role
        assertTrue(PermissionBasedAuthorization.create("do_actual_work").match(user));
    }
}


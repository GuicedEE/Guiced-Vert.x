package com.guicedee.vertx.auth;

import com.guicedee.client.services.IDefaultService;
import io.vertx.ext.auth.authorization.AuthorizationProvider;

/**
 * SPI for contributing an {@link AuthorizationProvider} to the authorization system.
 * <p>
 * Implementations are discovered via {@code ServiceLoader} and made available for
 * injection. Multiple providers can be registered — they are all bound into a
 * Guice multibinder so consumers can inject {@code Set<AuthorizationProvider>}.
 * <p>
 * Register in {@code module-info.java}:
 * <pre>
 * provides IGuicedAuthorizationProvider with MyAuthzProvider;
 * </pre>
 */
public interface IGuicedAuthorizationProvider extends IDefaultService<IGuicedAuthorizationProvider>
{
    /**
     * Creates or returns the authorization provider.
     *
     * @return The authorization provider instance.
     */
    AuthorizationProvider getAuthorizationProvider();
}


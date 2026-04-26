package com.guicedee.vertx.auth;

import com.guicedee.client.services.IDefaultService;
import io.vertx.ext.auth.authentication.AuthenticationProvider;

/**
 * SPI for contributing an {@link AuthenticationProvider} to the authentication chain.
 * <p>
 * Implementations are discovered via {@code ServiceLoader} and combined into a
 * {@link io.vertx.ext.auth.ChainAuth} based on the {@link AuthOptions#chainMode()}.
 * <p>
 * Register in {@code module-info.java}:
 * <pre>
 * provides IGuicedAuthenticationProvider with MyAuthProvider;
 * </pre>
 */
public interface IGuicedAuthenticationProvider extends IDefaultService<IGuicedAuthenticationProvider>
{
    /**
     * Creates or returns the authentication provider.
     *
     * @return The authentication provider instance.
     */
    AuthenticationProvider getAuthenticationProvider();
}


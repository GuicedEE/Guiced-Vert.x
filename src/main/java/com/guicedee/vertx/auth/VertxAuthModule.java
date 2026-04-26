package com.guicedee.vertx.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.ext.auth.ChainAuth;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import io.vertx.ext.auth.prng.VertxContextPRNG;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Guice module that binds Vert.x authentication and authorization types.
 * <p>
 * Bindings created:
 * <ul>
 *   <li>{@code AuthenticationProvider} — the primary authentication provider (or ChainAuth if multiple)</li>
 *   <li>{@code ChainAuth} — the authentication chain (if providers exist)</li>
 *   <li>{@code AuthorizationProvider} — primary authorization provider (first registered)</li>
 *   <li>{@code Set<AuthorizationProvider>} — all registered authorization providers via Multibinder</li>
 *   <li>{@code VertxContextPRNG} — the shared PRNG instance</li>
 *   <li>{@code KeyStoreOptions} — if keystore is configured</li>
 * </ul>
 */
@Log4j2
public class VertxAuthModule extends AbstractModule implements IGuiceModule<VertxAuthModule>
{
    @Override
    protected void configure()
    {
        log.info("Configuring Vert.x Auth Guice bindings");

        // Bind VertxContextPRNG as singleton
        bind(VertxContextPRNG.class)
                .toProvider(() -> VertxContextPRNG.current(VertXPreStartup.getVertx()))
                .in(Singleton.class);

        // Bind ChainAuth and AuthenticationProvider
        ChainAuth chainAuth = VertxAuthPreStartup.getChainAuth();
        if (chainAuth != null)
        {
            bind(ChainAuth.class).toInstance(chainAuth);
            bind(AuthenticationProvider.class).toInstance(chainAuth);
            log.info("Bound ChainAuth and AuthenticationProvider ({} provider(s))",
                    VertxAuthPreStartup.getAuthenticationProviders().size());
        }
        else if (!VertxAuthPreStartup.getAuthenticationProviders().isEmpty())
        {
            // Single provider — no chain needed
            AuthenticationProvider singleProvider = VertxAuthPreStartup.getAuthenticationProviders().getFirst();
            bind(AuthenticationProvider.class).toInstance(singleProvider);
            log.info("Bound single AuthenticationProvider");
        }

        // Bind authorization providers via Multibinder
        Multibinder<AuthorizationProvider> authzMultibinder = Multibinder.newSetBinder(binder(), AuthorizationProvider.class);
        List<AuthorizationProvider> authzProviders = VertxAuthPreStartup.getAuthorizationProviders();
        for (AuthorizationProvider provider : authzProviders)
        {
            authzMultibinder.addBinding().toInstance(provider);
        }

        // Also bind the first as the primary AuthorizationProvider
        if (!authzProviders.isEmpty())
        {
            bind(AuthorizationProvider.class).toInstance(authzProviders.getFirst());
            log.info("Bound {} authorization provider(s)", authzProviders.size());
        }

        // Bind KeyStoreOptions if configured
        KeyStoreOptions kso = VertxAuthPreStartup.getKeyStoreOptions();
        if (kso != null)
        {
            bind(KeyStoreOptions.class).toInstance(kso);
            log.info("Bound KeyStoreOptions");
        }

        // Bind PubSecKeyOptions list if configured
        List<PubSecKeyOptions> pskList = VertxAuthPreStartup.getPubSecKeyOptionsList();
        if (!pskList.isEmpty())
        {
            Multibinder<PubSecKeyOptions> pskMultibinder = Multibinder.newSetBinder(binder(), PubSecKeyOptions.class);
            for (PubSecKeyOptions psk : pskList)
            {
                pskMultibinder.addBinding().toInstance(psk);
            }
            log.info("Bound {} PubSecKeyOptions via Multibinder", pskList.size());
        }

        // Bind OAuth2Auth if available (optional module)
        bindOAuth2Auth();
        // Bind JWTAuth if available (optional module)
        bindJwtAuth();
        // Bind PolicyBasedAuthorizationProvider if available (optional module)
        bindAbacProvider();
        // Bind HotpAuth/TotpAuth if available (optional module)
        bindOtpAuth();
        // Bind PropertyFileAuthentication if available (optional module)
        bindPropertyFileAuth();
        // Bind LdapAuthentication if available (optional module)
        bindLdapAuth();
        // Bind HtpasswdAuth if available (optional module)
        bindHtpasswdAuth();
        // Bind HtdigestAuth if available (optional module)
        bindHtdigestAuth();
    }

    private void bindOAuth2Auth()
    {
        try
        {
            Class<?> oauth2AuthClass = Class.forName("io.vertx.ext.auth.oauth2.OAuth2Auth");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.oauth2.OAuth2AuthenticationProvider");
            Object oauth2Auth = providerClass.getMethod("getOauth2Auth").invoke(null);
            if (oauth2Auth != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> oauthType = (Class<Object>) oauth2AuthClass;
                bind(oauthType).toInstance(oauth2Auth);
                log.info("Bound OAuth2Auth");
            }
        }
        catch (ClassNotFoundException _)
        {
            // vertx-auth-oauth2 not on module path — skip
        }
        catch (Exception e)
        {
            log.debug("OAuth2Auth binding skipped: {}", e.getMessage());
        }
    }

    private void bindJwtAuth()
    {
        try
        {
            Class<?> jwtAuthClass = Class.forName("io.vertx.ext.auth.jwt.JWTAuth");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.jwt.JwtAuthenticationProvider");
            Object jwtAuth = providerClass.getMethod("getJwtAuth").invoke(null);
            if (jwtAuth != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> jwtType = (Class<Object>) jwtAuthClass;
                bind(jwtType).toInstance(jwtAuth);
                log.info("Bound JWTAuth");
            }
        }
        catch (ClassNotFoundException _)
        {
            // vertx-auth-jwt not on module path — skip
        }
        catch (Exception e)
        {
            log.debug("JWTAuth binding skipped: {}", e.getMessage());
        }
    }

    private void bindAbacProvider()
    {
        try
        {
            Class<?> abacClass = Class.forName("io.vertx.ext.auth.abac.PolicyBasedAuthorizationProvider");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.abac.AbacAuthorizationProvider");
            Object abacProvider = providerClass.getMethod("getPolicyProvider").invoke(null);
            if (abacProvider != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> abacType = (Class<Object>) abacClass;
                bind(abacType).toInstance(abacProvider);
                log.info("Bound PolicyBasedAuthorizationProvider");
            }
        }
        catch (ClassNotFoundException _)
        {
            // vertx-auth-abac not on module path — skip
        }
        catch (Exception e)
        {
            log.debug("PolicyBasedAuthorizationProvider binding skipped: {}", e.getMessage());
        }
    }

    private void bindOtpAuth()
    {
        try
        {
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.otp.OtpAuthenticationProvider");
            Object otpAuth = providerClass.getMethod("getOtpAuth").invoke(null);
            if (otpAuth != null)
            {
                // Bind to the concrete type (HotpAuth or TotpAuth)
                try
                {
                    Class<?> hotpClass = Class.forName("io.vertx.ext.auth.otp.hotp.HotpAuth");
                    if (hotpClass.isInstance(otpAuth))
                    {
                        @SuppressWarnings("unchecked")
                        Class<Object> hotpType = (Class<Object>) hotpClass;
                        bind(hotpType).toInstance(otpAuth);
                        log.info("Bound HotpAuth");
                    }
                }
                catch (ClassNotFoundException _) { }

                try
                {
                    Class<?> totpClass = Class.forName("io.vertx.ext.auth.otp.totp.TotpAuth");
                    if (totpClass.isInstance(otpAuth))
                    {
                        @SuppressWarnings("unchecked")
                        Class<Object> totpType = (Class<Object>) totpClass;
                        bind(totpType).toInstance(otpAuth);
                        log.info("Bound TotpAuth");
                    }
                }
                catch (ClassNotFoundException _) { }
            }
        }
        catch (ClassNotFoundException _)
        {
            // vertx-auth-otp not on module path — skip
        }
        catch (Exception e)
        {
            log.debug("OTP auth binding skipped: {}", e.getMessage());
        }
    }

    private void bindPropertyFileAuth()
    {
        try
        {
            Class<?> authnClass = Class.forName("io.vertx.ext.auth.properties.PropertyFileAuthentication");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.properties.PropertyFileAuthenticationProvider");
            Object authn = providerClass.getMethod("getPropertyFileAuth").invoke(null);
            if (authn != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> type = (Class<Object>) authnClass;
                bind(type).toInstance(authn);
                log.info("Bound PropertyFileAuthentication");
            }
        }
        catch (ClassNotFoundException _) { }
        catch (Exception e)
        {
            log.debug("PropertyFileAuthentication binding skipped: {}", e.getMessage());
        }

        try
        {
            Class<?> authzClass = Class.forName("io.vertx.ext.auth.properties.PropertyFileAuthorization");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.properties.PropertyFileAuthorizationProvider");
            Object authz = providerClass.getMethod("getPropertyFileAuthz").invoke(null);
            if (authz != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> type = (Class<Object>) authzClass;
                bind(type).toInstance(authz);
                log.info("Bound PropertyFileAuthorization");
            }
        }
        catch (ClassNotFoundException _) { }
        catch (Exception e)
        {
            log.debug("PropertyFileAuthorization binding skipped: {}", e.getMessage());
        }
    }

    private void bindLdapAuth()
    {
        try
        {
            Class<?> ldapClass = Class.forName("io.vertx.ext.auth.ldap.LdapAuthentication");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.ldap.LdapAuthenticationProvider");
            Object ldap = providerClass.getMethod("getLdapAuth").invoke(null);
            if (ldap != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> type = (Class<Object>) ldapClass;
                bind(type).toInstance(ldap);
                log.info("Bound LdapAuthentication");
            }
        }
        catch (ClassNotFoundException _) { }
        catch (Exception e)
        {
            log.debug("LdapAuthentication binding skipped: {}", e.getMessage());
        }
    }

    private void bindHtpasswdAuth()
    {
        try
        {
            Class<?> htpasswdClass = Class.forName("io.vertx.ext.auth.htpasswd.HtpasswdAuth");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.htpasswd.HtpasswdAuthenticationProvider");
            Object htpasswd = providerClass.getMethod("getHtpasswdAuth").invoke(null);
            if (htpasswd != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> type = (Class<Object>) htpasswdClass;
                bind(type).toInstance(htpasswd);
                log.info("Bound HtpasswdAuth");
            }
        }
        catch (ClassNotFoundException _) { }
        catch (Exception e)
        {
            log.debug("HtpasswdAuth binding skipped: {}", e.getMessage());
        }
    }

    private void bindHtdigestAuth()
    {
        try
        {
            Class<?> htdigestClass = Class.forName("io.vertx.ext.auth.htdigest.HtdigestAuth");
            Class<?> providerClass = Class.forName("com.guicedee.vertx.auth.htdigest.HtdigestAuthenticationProvider");
            Object htdigest = providerClass.getMethod("getHtdigestAuth").invoke(null);
            if (htdigest != null)
            {
                @SuppressWarnings("unchecked")
                Class<Object> type = (Class<Object>) htdigestClass;
                bind(type).toInstance(htdigest);
                log.info("Bound HtdigestAuth");
            }
        }
        catch (ClassNotFoundException _) { }
        catch (Exception e)
        {
            log.debug("HtdigestAuth binding skipped: {}", e.getMessage());
        }
    }
}

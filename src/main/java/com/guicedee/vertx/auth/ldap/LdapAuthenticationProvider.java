package com.guicedee.vertx.auth.ldap;

import com.guicedee.client.utils.Discovery;

import com.guicedee.client.utils.ClassLoading;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered LDAP authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link LdapAuthOptions} annotation is found
 * on a class or {@code package-info.java}.
 *
 * <h2>Environment variable overrides</h2>
 * <ul>
 *   <li>{@code VERTX_AUTH_LDAP_URL}</li>
 *   <li>{@code VERTX_AUTH_LDAP_AUTHENTICATION_QUERY}</li>
 *   <li>{@code VERTX_AUTH_LDAP_AUTHENTICATION_MECHANISM}</li>
 *   <li>{@code VERTX_AUTH_LDAP_REFERRAL}</li>
 * </ul>
 */
@Log4j2
public class LdapAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static io.vertx.ext.auth.ldap.LdapAuthentication ldapAuth;

    @Getter
    private static LdapAuthOptions ldapAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = Discovery.scanResult();
        ldapAuthOptions = discoverOptions(scanResult);

        if (ldapAuthOptions == null)
        {
            log.debug("No @LdapAuthOptions annotation found — LDAP provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        try
        {
            String url = resolveString("URL", ldapAuthOptions.url(), "");
            String authQuery = resolveString("AUTHENTICATION_QUERY", ldapAuthOptions.authenticationQuery(), "");
            String mechanism = resolveString("AUTHENTICATION_MECHANISM", ldapAuthOptions.authenticationMechanism(), "simple");
            String referral = resolveString("REFERRAL", ldapAuthOptions.referral(), "follow");

            if (url.isBlank() || authQuery.isBlank())
            {
                log.error("@LdapAuthOptions requires url and authenticationQuery — both must be non-empty");
                return null;
            }

            var opts = new io.vertx.ext.auth.ldap.LdapAuthenticationOptions()
                    .setUrl(url)
                    .setAuthenticationQuery(authQuery)
                    .setAuthenticationMechanism(mechanism)
                    .setReferral(referral);

            ldapAuth = io.vertx.ext.auth.ldap.LdapAuthentication.create(vertx, opts);
            log.info("LDAP authentication provider created: url={}, query={}, mechanism={}, referral={}",
                    url, authQuery, mechanism, referral);
            return ldapAuth;
        }
        catch (Exception e)
        {
            log.error("Failed to create LDAP authentication provider", e);
            return null;
        }
    }

    // ── Annotation discovery ────────────────────────────

    private LdapAuthOptions discoverOptions(ScanResult scanResult)
    {
        return Discovery.singleAnnotation(LdapAuthOptions.class).orElse(null);
    }

    // ── Env / placeholder helpers ───────────────────────

    private static String resolveString(String envKey, String annotationValue, String fallback)
    {
        return Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_LDAP_" + envKey, annotationValue != null && !annotationValue.isBlank() ? annotationValue : fallback);
    }

    public static void reset()
    {
        ldapAuth = null;
        ldapAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 130;
    }
}


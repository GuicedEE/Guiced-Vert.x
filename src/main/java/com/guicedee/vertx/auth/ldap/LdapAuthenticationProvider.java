package com.guicedee.vertx.auth.ldap;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.ldap.LdapAuthentication;
import io.vertx.ext.auth.ldap.LdapAuthenticationOptions;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered LDAP authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link LdapAuthOptions} annotation is found
 * on a class or {@code package-info.java}.
 *
 * <h3>Environment variable overrides</h3>
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
    private static LdapAuthentication ldapAuth;

    @Getter
    private static LdapAuthOptions ldapAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
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

            var opts = new LdapAuthenticationOptions()
                    .setUrl(url)
                    .setAuthenticationQuery(authQuery)
                    .setAuthenticationMechanism(mechanism)
                    .setReferral(referral);

            ldapAuth = LdapAuthentication.create(vertx, opts);
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
        var annotatedClasses = scanResult.getClassesWithAnnotation(LdapAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(LdapAuthOptions.class);
            if (ann != null)
            {
                log.debug("Found @LdapAuthOptions on class: {}", ci.getName());
                return ann;
            }
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(LdapAuthOptions.class);
                if (ann != null)
                {
                    log.debug("Found @LdapAuthOptions on package: {}", pkgInfo.getName());
                    return ann;
                }
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    // ── Env / placeholder helpers ───────────────────────

    private static String resolveString(String envKey, String annotationValue, String fallback)
    {
        String envValue = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_LDAP_" + envKey, null);
        if (envValue != null && !envValue.isBlank())
        {
            return envValue;
        }
        if (annotationValue != null && !annotationValue.isBlank())
        {
            return resolveEnvPlaceholders(annotationValue);
        }
        return fallback;
    }

    private static String resolveEnvPlaceholders(String value)
    {
        if (value == null || !value.contains("${"))
        {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length())
        {
            if (value.charAt(i) == '$' && i + 1 < value.length() && value.charAt(i + 1) == '{')
            {
                int end = value.indexOf('}', i + 2);
                if (end > 0)
                {
                    String varName = value.substring(i + 2, end);
                    String resolved = Environment.getSystemPropertyOrEnvironment(varName, "");
                    result.append(resolved);
                    i = end + 1;
                    continue;
                }
            }
            result.append(value.charAt(i));
            i++;
        }
        return result.toString();
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


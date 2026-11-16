package com.guicedee.vertx.auth.htpasswd;

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
 * Auto-discovered htpasswd authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link HtpasswdAuthOptions} annotation is found.
 * Authentication only — htpasswd provides no authorization.
 *
 * <h2>Environment variable overrides</h2>
 * <ul>
 *   <li>{@code VERTX_AUTH_HTPASSWD_PATH}</li>
 *   <li>{@code VERTX_AUTH_HTPASSWD_PLAIN_TEXT_ENABLED}</li>
 * </ul>
 */
@Log4j2
public class HtpasswdAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static io.vertx.ext.auth.htpasswd.HtpasswdAuth htpasswdAuth;

    @Getter
    private static HtpasswdAuthOptions htpasswdAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = Discovery.scanResult();
        htpasswdAuthOptions = discoverOptions(scanResult);

        if (htpasswdAuthOptions == null)
        {
            log.debug("No @HtpasswdAuthOptions annotation found — htpasswd provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        try
        {
            String path = resolveString("PATH", htpasswdAuthOptions.path(), ".htpasswd");
            boolean plainText = Boolean.parseBoolean(
                    env("PLAIN_TEXT_ENABLED", String.valueOf(htpasswdAuthOptions.plainTextEnabled())));

            var opts = new io.vertx.ext.auth.htpasswd.HtpasswdAuthOptions()
                    .setHtpasswdFile(path)
                    .setPlainTextEnabled(plainText);

            htpasswdAuth = io.vertx.ext.auth.htpasswd.HtpasswdAuth.create(vertx, opts);
            log.info("htpasswd authentication provider created: path={}, plainTextEnabled={}", path, plainText);
            return htpasswdAuth;
        }
        catch (Exception e)
        {
            log.error("Failed to create htpasswd authentication provider", e);
            return null;
        }
    }

    // ── Annotation discovery ────────────────────────────

    private HtpasswdAuthOptions discoverOptions(ScanResult scanResult)
    {
        return Discovery.singleAnnotation(HtpasswdAuthOptions.class).orElse(null);
    }

    // ── Env / placeholder helpers ───────────────────────

    private static String env(String property, String defaultValue)
    {
        return Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_HTPASSWD_" + property, defaultValue);
    }

    private static String resolveString(String envKey, String annotationValue, String fallback)
    {
        return env(envKey, annotationValue != null && !annotationValue.isBlank() ? annotationValue : fallback);
    }

    public static void reset()
    {
        htpasswdAuth = null;
        htpasswdAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 140;
    }
}


package com.guicedee.vertx.auth.htpasswd;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.htpasswd.HtpasswdAuth;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered htpasswd authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link HtpasswdAuthOptions} annotation is found.
 * Authentication only — htpasswd provides no authorization.
 *
 * <h3>Environment variable overrides</h3>
 * <ul>
 *   <li>{@code VERTX_AUTH_HTPASSWD_PATH}</li>
 *   <li>{@code VERTX_AUTH_HTPASSWD_PLAIN_TEXT_ENABLED}</li>
 * </ul>
 */
@Log4j2
public class HtpasswdAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static HtpasswdAuth htpasswdAuth;

    @Getter
    private static HtpasswdAuthOptions htpasswdAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
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

            htpasswdAuth = HtpasswdAuth.create(vertx, opts);
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
        var annotatedClasses = scanResult.getClassesWithAnnotation(HtpasswdAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(HtpasswdAuthOptions.class);
            if (ann != null) return ann;
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(HtpasswdAuthOptions.class);
                if (ann != null) return ann;
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    // ── Env / placeholder helpers ───────────────────────

    private static String env(String property, String defaultValue)
    {
        String value = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_HTPASSWD_" + property, null);
        if (value != null && !value.isBlank()) return value;
        return defaultValue;
    }

    private static String resolveString(String envKey, String annotationValue, String fallback)
    {
        String envValue = env(envKey, null);
        if (envValue != null && !envValue.isBlank()) return envValue;
        if (annotationValue != null && !annotationValue.isBlank()) return resolveEnvPlaceholders(annotationValue);
        return fallback;
    }

    private static String resolveEnvPlaceholders(String value)
    {
        if (value == null || !value.contains("${")) return value;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length())
        {
            if (value.charAt(i) == '$' && i + 1 < value.length() && value.charAt(i + 1) == '{')
            {
                int end = value.indexOf('}', i + 2);
                if (end > 0)
                {
                    result.append(Environment.getSystemPropertyOrEnvironment(value.substring(i + 2, end), ""));
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
        htpasswdAuth = null;
        htpasswdAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 140;
    }
}


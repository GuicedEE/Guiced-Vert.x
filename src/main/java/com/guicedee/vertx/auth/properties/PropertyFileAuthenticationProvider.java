package com.guicedee.vertx.auth.properties;

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
 * Auto-discovered property-file authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when a {@link PropertyFileAuthOptions} annotation is found
 * on a class or {@code package-info.java}.
 *
 * <h3>Environment variable overrides</h3>
 * <ul>
 *   <li>{@code VERTX_AUTH_PROPERTIES_PATH} — overrides the file path</li>
 * </ul>
 */
@Log4j2
public class PropertyFileAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static io.vertx.ext.auth.properties.PropertyFileAuthentication propertyFileAuth;

    @Getter
    private static PropertyFileAuthOptions propertyFileAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        propertyFileAuthOptions = discoverOptions(scanResult);

        if (propertyFileAuthOptions == null)
        {
            log.debug("No @PropertyFileAuthOptions annotation found — property file provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();
        String path = resolvePath(propertyFileAuthOptions);

        if (path.isBlank())
        {
            log.error("@PropertyFileAuthOptions found but path is empty");
            return null;
        }

        try
        {
            propertyFileAuth = io.vertx.ext.auth.properties.PropertyFileAuthentication.create(vertx, path);
            log.info("Property file authentication provider created: path={}", path);
            return propertyFileAuth;
        }
        catch (Exception e)
        {
            log.error("Failed to create property file authentication provider from: {}", path, e);
            return null;
        }
    }

    static String resolvePath(PropertyFileAuthOptions ann)
    {
        String envPath = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_PROPERTIES_PATH", null);
        if (envPath != null && !envPath.isBlank())
        {
            return envPath;
        }
        return resolveEnvPlaceholders(ann.path());
    }

    // ── Annotation discovery ────────────────────────────

    private PropertyFileAuthOptions discoverOptions(ScanResult scanResult)
    {
        var annotatedClasses = scanResult.getClassesWithAnnotation(PropertyFileAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(PropertyFileAuthOptions.class);
            if (ann != null)
            {
                log.debug("Found @PropertyFileAuthOptions on class: {}", ci.getName());
                return ann;
            }
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(PropertyFileAuthOptions.class);
                if (ann != null)
                {
                    log.debug("Found @PropertyFileAuthOptions on package: {}", pkgInfo.getName());
                    return ann;
                }
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    // ── Placeholder helper ──────────────────────────────

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

    /**
     * Resets all state — for testing purposes.
     */
    public static void reset()
    {
        propertyFileAuth = null;
        propertyFileAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 120;
    }
}


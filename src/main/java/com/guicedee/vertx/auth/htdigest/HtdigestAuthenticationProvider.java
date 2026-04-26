package com.guicedee.vertx.auth.htdigest;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.htdigest.HtdigestAuth;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered htdigest authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link HtdigestAuthOptions} annotation is found.
 * Authentication only — htdigest provides no authorization.
 *
 * <h3>Environment variable overrides</h3>
 * <ul>
 *   <li>{@code VERTX_AUTH_HTDIGEST_PATH}</li>
 * </ul>
 */
@Log4j2
public class HtdigestAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static HtdigestAuth htdigestAuth;

    @Getter
    private static HtdigestAuthOptions htdigestAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        htdigestAuthOptions = discoverOptions(scanResult);

        if (htdigestAuthOptions == null)
        {
            log.debug("No @HtdigestAuthOptions annotation found — htdigest provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        try
        {
            String path = resolvePath(htdigestAuthOptions);
            htdigestAuth = HtdigestAuth.create(vertx, path);
            log.info("htdigest authentication provider created: path={}, realm={}", path, htdigestAuth.realm());
            return htdigestAuth;
        }
        catch (Exception e)
        {
            log.error("Failed to create htdigest authentication provider", e);
            return null;
        }
    }

    private static String resolvePath(HtdigestAuthOptions ann)
    {
        String envPath = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_HTDIGEST_PATH", null);
        if (envPath != null && !envPath.isBlank()) return envPath;
        String annotationPath = ann.path();
        if (annotationPath != null && annotationPath.contains("${"))
        {
            StringBuilder result = new StringBuilder();
            int i = 0;
            while (i < annotationPath.length())
            {
                if (annotationPath.charAt(i) == '$' && i + 1 < annotationPath.length() && annotationPath.charAt(i + 1) == '{')
                {
                    int end = annotationPath.indexOf('}', i + 2);
                    if (end > 0)
                    {
                        result.append(Environment.getSystemPropertyOrEnvironment(annotationPath.substring(i + 2, end), ""));
                        i = end + 1;
                        continue;
                    }
                }
                result.append(annotationPath.charAt(i));
                i++;
            }
            return result.toString();
        }
        return annotationPath;
    }

    private HtdigestAuthOptions discoverOptions(ScanResult scanResult)
    {
        var annotatedClasses = scanResult.getClassesWithAnnotation(HtdigestAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(HtdigestAuthOptions.class);
            if (ann != null) return ann;
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(HtdigestAuthOptions.class);
                if (ann != null) return ann;
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    public static void reset()
    {
        htdigestAuth = null;
        htdigestAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 150;
    }
}


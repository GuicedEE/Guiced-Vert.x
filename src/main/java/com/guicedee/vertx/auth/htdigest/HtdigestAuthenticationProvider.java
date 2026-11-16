package com.guicedee.vertx.auth.htdigest;

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
 * Auto-discovered htdigest authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link HtdigestAuthOptions} annotation is found.
 * Authentication only — htdigest provides no authorization.
 *
 * <h2>Environment variable overrides</h2>
 * <ul>
 *   <li>{@code VERTX_AUTH_HTDIGEST_PATH}</li>
 * </ul>
 */
@Log4j2
public class HtdigestAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static io.vertx.ext.auth.htdigest.HtdigestAuth htdigestAuth;

    @Getter
    private static HtdigestAuthOptions htdigestAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = Discovery.scanResult();
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
            htdigestAuth = io.vertx.ext.auth.htdigest.HtdigestAuth.create(vertx, path);
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
        return Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_HTDIGEST_PATH", ann.path());
    }

    private HtdigestAuthOptions discoverOptions(ScanResult scanResult)
    {
        return Discovery.singleAnnotation(HtdigestAuthOptions.class).orElse(null);
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


package com.guicedee.vertx.auth.properties;

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
 * Auto-discovered property-file authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when a {@link PropertyFileAuthOptions} annotation is found
 * on a class or {@code package-info.java}.
 *
 * <h2>Environment variable overrides</h2>
 * <ul>
 *   <li>{@code VERTX_AUTH_PROPERTIES_PATH} — overrides the file path</li>
 * </ul>
 */
@Log4j2
public class PropertyFileAuthenticationProvider implements IGuicedAuthenticationProvider
{
    /**
     * Default Constructor for PropertyFileAuthenticationProvider
     */
    public PropertyFileAuthenticationProvider()
    {
        //No config required
    }

    @Getter
    private static io.vertx.ext.auth.properties.PropertyFileAuthentication propertyFileAuth;

    @Getter
    private static PropertyFileAuthOptions propertyFileAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = Discovery.scanResult();
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
        return Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_PROPERTIES_PATH", ann.path());
    }

    // ── Annotation discovery ────────────────────────────

    private PropertyFileAuthOptions discoverOptions(ScanResult scanResult)
    {
        return Discovery.singleAnnotation(PropertyFileAuthOptions.class).orElse(null);
    }

    // ── Placeholder helper ──────────────────────────────

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


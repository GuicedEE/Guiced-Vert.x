package com.guicedee.vertx.auth.properties;

import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthorizationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import io.vertx.ext.auth.properties.PropertyFileAuthorization;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered property-file authorization provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when a {@link PropertyFileAuthOptions} annotation is found.
 * Reads roles and permissions from the same Shiro-format properties file used
 * by {@link PropertyFileAuthenticationProvider}.
 */
@Log4j2
public class PropertyFileAuthorizationProvider implements IGuicedAuthorizationProvider
{
    @Getter
    private static PropertyFileAuthorization propertyFileAuthz;

    @Override
    public AuthorizationProvider getAuthorizationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        PropertyFileAuthOptions ann = discoverOptions(scanResult);

        if (ann == null)
        {
            log.debug("No @PropertyFileAuthOptions found — property file authorization not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();
        String path = PropertyFileAuthenticationProvider.resolvePath(ann);

        if (path.isBlank())
        {
            return null;
        }

        try
        {
            propertyFileAuthz = PropertyFileAuthorization.create(vertx, path);
            log.info("Property file authorization provider created: path={}", path);
            return propertyFileAuthz;
        }
        catch (Exception e)
        {
            log.error("Failed to create property file authorization provider from: {}", path, e);
            return null;
        }
    }

    private PropertyFileAuthOptions discoverOptions(ScanResult scanResult)
    {
        var annotatedClasses = scanResult.getClassesWithAnnotation(PropertyFileAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(PropertyFileAuthOptions.class);
            if (ann != null) return ann;
        }

        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(PropertyFileAuthOptions.class);
                if (ann != null) return ann;
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    public static void reset()
    {
        propertyFileAuthz = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 120;
    }
}


package com.guicedee.vertx.auth.oauth2;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-discovered OAuth2 authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when an {@link OAuth2Options} annotation is found
 * on a class or {@code package-info.java}. Implements {@link IGuicedAuthenticationProvider}
 * so it is automatically registered into the {@link io.vertx.ext.auth.ChainAuth} chain.
 * <p>
 * Register in {@code module-info.java}:
 * <pre>
 * provides IGuicedAuthenticationProvider with OAuth2AuthenticationProvider;
 * </pre>
 *
 * <h3>Environment variable overrides</h3>
 * Every annotation attribute can be overridden at deploy time with a
 * {@code VERTX_AUTH_OAUTH2_} prefixed environment variable:
 * <ul>
 *   <li>{@code VERTX_AUTH_OAUTH2_CLIENT_ID}</li>
 *   <li>{@code VERTX_AUTH_OAUTH2_CLIENT_SECRET}</li>
 *   <li>{@code VERTX_AUTH_OAUTH2_TENANT}</li>
 *   <li>{@code VERTX_AUTH_OAUTH2_SITE}</li>
 *   <li>{@code VERTX_AUTH_OAUTH2_WELL_KNOWN_PROVIDER}</li>
 *   <li>{@code VERTX_AUTH_OAUTH2_FLOW}</li>
 *   <li>… and all other path/option attributes</li>
 * </ul>
 */
@Log4j2
public class OAuth2AuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static io.vertx.ext.auth.oauth2.OAuth2Auth oauth2Auth;

    @Getter
    private static OAuth2Options oauth2Options;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        oauth2Options = discoverOAuth2Options(scanResult);

        if (oauth2Options == null)
        {
            log.debug("No @OAuth2Options annotation found — OAuth2 provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        String clientId = resolveString("CLIENT_ID", oauth2Options.clientId(), "");
        String clientSecret = resolveString("CLIENT_SECRET", oauth2Options.clientSecret(), "");
        WellKnownProvider provider = resolveWellKnownProvider(oauth2Options);

        try
        {
            io.vertx.ext.auth.oauth2.OAuth2Options vertxOpts = buildVertxOAuth2Options(oauth2Options, clientId, clientSecret);
            oauth2Auth = createProvider(vertx, vertxOpts, provider, clientId, clientSecret);

            // Install a debounced missing-key handler for JWK rotation
            installMissingKeyHandler(oauth2Auth);

            log.info("OAuth2 authentication provider created: provider={}, clientId={}",
                    provider, clientId);
            return oauth2Auth;
        }
        catch (Exception e)
        {
            log.error("Failed to create OAuth2 authentication provider", e);
            return null;
        }
    }

    // ── Provider creation ───────────────────────────────

    private io.vertx.ext.auth.oauth2.OAuth2Auth createProvider(Vertx vertx, io.vertx.ext.auth.oauth2.OAuth2Options opts,
                                      WellKnownProvider provider, String clientId, String clientSecret)
    {
        return switch (provider)
        {
            case GOOGLE -> awaitFuture(io.vertx.ext.auth.oauth2.providers.GoogleAuth.discover(vertx, opts));
            case AZURE_AD -> awaitFuture(io.vertx.ext.auth.oauth2.providers.AzureADAuth.discover(vertx, opts));
            case KEYCLOAK -> awaitFuture(io.vertx.ext.auth.oauth2.providers.KeycloakAuth.discover(vertx, opts));
            case SALESFORCE -> awaitFuture(io.vertx.ext.auth.oauth2.providers.SalesforceAuth.discover(vertx, opts));
            case IBM_CLOUD -> awaitFuture(io.vertx.ext.auth.oauth2.providers.IBMCloudAuth.discover(vertx, opts));
            case AMAZON_COGNITO, OPENID_CONNECT -> awaitFuture(io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth.discover(vertx, opts));
            case GITHUB -> io.vertx.ext.auth.oauth2.providers.GithubAuth.create(vertx, clientId, clientSecret,
                    opts.getHttpClientOptions() != null ? opts.getHttpClientOptions() : new HttpClientOptions());
            case CUSTOM -> io.vertx.ext.auth.oauth2.OAuth2Auth.create(vertx, opts);
        };
    }

    private io.vertx.ext.auth.oauth2.OAuth2Auth awaitFuture(Future<io.vertx.ext.auth.oauth2.OAuth2Auth> future)
    {
        // Blocking during startup is acceptable — this runs inside IGuicePreStartup
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private void installMissingKeyHandler(io.vertx.ext.auth.oauth2.OAuth2Auth auth)
    {
        final AtomicBoolean updating = new AtomicBoolean(false);
        auth.missingKeyHandler(keyId ->
        {
            if (updating.compareAndSet(false, true))
            {
                log.info("Missing JWK key [{}] — refreshing key set", keyId);
                auth.jWKSet()
                        .onComplete(ar ->
                        {
                            updating.set(false);
                            if (ar.failed())
                            {
                                log.warn("Failed to refresh JWK key set: {}", ar.cause().getMessage());
                            }
                        });
            }
        });
    }

    // ── Options builder ─────────────────────────────────

    private io.vertx.ext.auth.oauth2.OAuth2Options buildVertxOAuth2Options(
            OAuth2Options ann, String clientId, String clientSecret)
    {
        var opts = new io.vertx.ext.auth.oauth2.OAuth2Options();
        opts.setClientId(clientId);
        opts.setClientSecret(clientSecret);

        String tenant = resolveString("TENANT", ann.tenant(), "");
        if (!tenant.isBlank())
        {
            opts.setTenant(tenant);
        }

        String site = resolveString("SITE", ann.site(), "");
        if (!site.isBlank())
        {
            opts.setSite(site);
        }

        // Flow → supported grant types
        OAuth2Flow flow = OAuth2Flow.valueOf(env("FLOW", ann.flow().name()));
        io.vertx.ext.auth.oauth2.OAuth2FlowType flowType = mapFlow(flow);
        opts.setSupportedGrantTypes(List.of(flowType.getGrantType()));

        // Endpoint overrides (only set if non-blank to preserve defaults)
        setIfNotBlank(resolveString("AUTHORIZATION_PATH", ann.authorizationPath(), ""), opts::setAuthorizationPath);
        setIfNotBlank(resolveString("TOKEN_PATH", ann.tokenPath(), ""), opts::setTokenPath);
        setIfNotBlank(resolveString("REVOCATION_PATH", ann.revocationPath(), ""), opts::setRevocationPath);
        setIfNotBlank(resolveString("INTROSPECTION_PATH", ann.introspectionPath(), ""), opts::setIntrospectionPath);
        setIfNotBlank(resolveString("JWK_PATH", ann.jwkPath(), ""), opts::setJwkPath);
        setIfNotBlank(resolveString("USERINFO_PATH", ann.userInfoPath(), ""), opts::setUserInfoPath);
        setIfNotBlank(resolveString("LOGOUT_PATH", ann.logoutPath(), ""), opts::setLogoutPath);

        // Scope separator
        String scopeSeparator = resolveString("SCOPE_SEPARATOR", ann.scopeDelimiter(), " ");
        opts.setScopeSeparator(scopeSeparator);

        // JWT options
        OAuth2JwtOptions jwt = ann.jwtOptions();
        var jwtOpts = new JWTOptions();

        String audience = resolveString("JWT_AUDIENCE", jwt.audience(), "");
        if (!audience.isBlank())
        {
            jwtOpts.addAudience(audience);
        }
        String issuer = resolveString("JWT_ISSUER", jwt.issuer(), "");
        if (!issuer.isBlank())
        {
            jwtOpts.setIssuer(issuer);
        }
        int leeway = Integer.parseInt(env("JWT_LEEWAY", String.valueOf(jwt.leeway())));
        if (leeway > 0)
        {
            jwtOpts.setLeeway(leeway);
        }
        boolean ignoreExp = Boolean.parseBoolean(env("JWT_IGNORE_EXPIRATION", String.valueOf(jwt.ignoreExpiration())));
        jwtOpts.setIgnoreExpiration(ignoreExp);

        opts.setJWTOptions(jwtOpts);

        // Extra parameters
        String extra = resolveString("EXTRA_PARAMETERS", ann.extraParameters(), "");
        if (!extra.isBlank())
        {
            JsonObject extraJson = new JsonObject();
            for (String pair : extra.split(","))
            {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2)
                {
                    extraJson.put(kv[0].trim(), kv[1].trim());
                }
            }
            opts.setExtraParameters(extraJson);
        }

        // HTTP client options (timeouts)
        int connectTimeout = Integer.parseInt(env("CONNECT_TIMEOUT", String.valueOf(ann.connectTimeout())));
        int readTimeout = Integer.parseInt(env("READ_TIMEOUT", String.valueOf(ann.readTimeout())));
        if (connectTimeout > 0 || readTimeout > 0)
        {
            HttpClientOptions httpOpts = new HttpClientOptions();
            if (connectTimeout > 0)
            {
                httpOpts.setConnectTimeout(connectTimeout);
            }
            if (readTimeout > 0)
            {
                httpOpts.setIdleTimeout(readTimeout / 1000);
            }
            opts.setHttpClientOptions(httpOpts);
        }

        return opts;
    }

    private io.vertx.ext.auth.oauth2.OAuth2FlowType mapFlow(OAuth2Flow flow)
    {
        return switch (flow)
        {
            case AUTH_CODE -> io.vertx.ext.auth.oauth2.OAuth2FlowType.AUTH_CODE;
            case PASSWORD -> io.vertx.ext.auth.oauth2.OAuth2FlowType.PASSWORD;
            case CLIENT_CREDENTIALS -> io.vertx.ext.auth.oauth2.OAuth2FlowType.CLIENT;
            case AUTH_JWT -> io.vertx.ext.auth.oauth2.OAuth2FlowType.AUTH_JWT;
        };
    }

    private WellKnownProvider resolveWellKnownProvider(OAuth2Options ann)
    {
        String envProvider = env("WELL_KNOWN_PROVIDER", null);
        if (envProvider != null && !envProvider.isBlank())
        {
            return WellKnownProvider.valueOf(envProvider);
        }
        return ann.wellKnownProvider();
    }

    // ── Annotation discovery ────────────────────────────

    private OAuth2Options discoverOAuth2Options(ScanResult scanResult)
    {
        // Check annotated classes
        var annotatedClasses = scanResult.getClassesWithAnnotation(OAuth2Options.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(OAuth2Options.class);
            if (ann != null)
            {
                log.debug("Found @OAuth2Options on class: {}", ci.getName());
                return ann;
            }
        }

        // Check package-info
        var packageInfoList = scanResult.getPackageInfo();
        for (var pkgInfo : packageInfoList)
        {
            try
            {
                Class<?> pkgClass = Class.forName(pkgInfo.getName() + ".package-info");
                var ann = pkgClass.getAnnotation(OAuth2Options.class);
                if (ann != null)
                {
                    log.debug("Found @OAuth2Options on package: {}", pkgInfo.getName());
                    return ann;
                }
            }
            catch (ClassNotFoundException _) { }
        }

        return null;
    }

    // ── Env / placeholder helpers ───────────────────────

    static String env(String property, String defaultValue)
    {
        String value = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_OAUTH2_" + property, null);
        if (value != null && !value.isBlank())
        {
            return value;
        }
        return defaultValue;
    }

    private static String resolveString(String envKey, String annotationValue, String fallback)
    {
        String envValue = env(envKey, null);
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

    private static void setIfNotBlank(String value, java.util.function.Consumer<String> setter)
    {
        if (value != null && !value.isBlank())
        {
            setter.accept(value);
        }
    }

    /**
     * Resets all state — for testing purposes.
     */
    public static void reset()
    {
        if (oauth2Auth != null)
        {
            oauth2Auth.close();
        }
        oauth2Auth = null;
        oauth2Options = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 100;
    }
}

package com.guicedee.vertx.auth.jwt;

import com.guicedee.client.Environment;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.auth.IGuicedAuthenticationProvider;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ScanResult;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered JWT authentication provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when a {@link JwtAuthOptions} annotation is found
 * on a class or {@code package-info.java}. Implements {@link IGuicedAuthenticationProvider}
 * so it is automatically registered into the {@link io.vertx.ext.auth.ChainAuth} chain.
 * <p>
 * Register in {@code module-info.java}:
 * <pre>
 * provides IGuicedAuthenticationProvider with JwtAuthenticationProvider;
 * </pre>
 *
 * <h3>Environment variable overrides</h3>
 * Every annotation attribute can be overridden at deploy time with a
 * {@code VERTX_AUTH_JWT_} prefixed environment variable:
 * <ul>
 *   <li>{@code VERTX_AUTH_JWT_KEYSTORE_PATH}</li>
 *   <li>{@code VERTX_AUTH_JWT_KEYSTORE_TYPE}</li>
 *   <li>{@code VERTX_AUTH_JWT_KEYSTORE_PASSWORD}</li>
 *   <li>{@code VERTX_AUTH_JWT_ALGORITHM}</li>
 *   <li>{@code VERTX_AUTH_JWT_AUDIENCE}</li>
 *   <li>{@code VERTX_AUTH_JWT_ISSUER}</li>
 *   <li>{@code VERTX_AUTH_JWT_SUBJECT}</li>
 *   <li>{@code VERTX_AUTH_JWT_LEEWAY}</li>
 *   <li>{@code VERTX_AUTH_JWT_IGNORE_EXPIRATION}</li>
 *   <li>{@code VERTX_AUTH_JWT_EXPIRES_IN_SECONDS}</li>
 *   <li>{@code VERTX_AUTH_JWT_NO_TIMESTAMP}</li>
 *   <li>{@code VERTX_AUTH_JWT_PERMISSIONS_CLAIM_KEY}</li>
 * </ul>
 */
@Log4j2
public class JwtAuthenticationProvider implements IGuicedAuthenticationProvider
{
    @Getter
    private static JWTAuth jwtAuth;

    @Getter
    private static JwtAuthOptions jwtAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = IGuiceContext.instance().getScanResult();
        jwtAuthOptions = discoverJwtAuthOptions(scanResult);

        if (jwtAuthOptions == null)
        {
            log.debug("No @JwtAuthOptions annotation found — JWT provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        try
        {
            JWTAuthOptions vertxOpts = buildVertxJwtAuthOptions(jwtAuthOptions, vertx);
            jwtAuth = JWTAuth.create(vertx, vertxOpts);

            log.info("JWT authentication provider created: algorithm={}, issuer={}, audience={}",
                    resolveString("ALGORITHM", jwtAuthOptions.algorithm(), "HS256"),
                    resolveString("ISSUER", jwtAuthOptions.issuer(), ""),
                    String.join(",", jwtAuthOptions.audience()));
            return jwtAuth;
        }
        catch (Exception e)
        {
            log.error("Failed to create JWT authentication provider", e);
            return null;
        }
    }

    // ── Options builder ─────────────────────────────────

    private JWTAuthOptions buildVertxJwtAuthOptions(JwtAuthOptions ann, Vertx vertx)
    {
        var opts = new JWTAuthOptions();

        // KeyStore
        String ksPath = resolveString("KEYSTORE_PATH", ann.keystorePath(), "");
        if (!ksPath.isBlank())
        {
            var ks = new KeyStoreOptions()
                    .setPath(ksPath)
                    .setType(resolveString("KEYSTORE_TYPE", ann.keystoreType(), "jceks"))
                    .setPassword(resolveString("KEYSTORE_PASSWORD", ann.keystorePassword(), ""));
            opts.setKeyStore(ks);
            log.info("JWT KeyStore configured: path={}, type={}", ksPath, ks.getType());
        }

        // PEM PubSecKeys
        for (int i = 0; i < ann.pubSecKeys().length; i++)
        {
            JwtPubSecKey psk = ann.pubSecKeys()[i];
            String algorithm = resolveString("PUBSECKEY_" + i + "_ALGORITHM", psk.algorithm(), "");
            String buffer = resolveString("PUBSECKEY_" + i + "_BUFFER", psk.buffer(), "");
            String path = resolveString("PUBSECKEY_" + i + "_PATH", psk.path(), "");
            String id = resolveString("PUBSECKEY_" + i + "_ID", psk.id(), "");

            if (!algorithm.isBlank())
            {
                var keyOpts = new PubSecKeyOptions().setAlgorithm(algorithm);

                if (!id.isBlank())
                {
                    keyOpts.setId(id);
                }

                if (!buffer.isBlank())
                {
                    keyOpts.setBuffer(io.vertx.core.buffer.Buffer.buffer(buffer));
                }
                else if (!path.isBlank())
                {
                    try
                    {
                        var fileContent = vertx.fileSystem().readFileBlocking(path);
                        keyOpts.setBuffer(fileContent);
                        log.info("JWT: Loaded PEM key from file: {}", path);
                    }
                    catch (Exception e)
                    {
                        log.error("JWT: Failed to load PEM key from file: {}", path, e);
                    }
                }

                opts.addPubSecKey(keyOpts);
                log.info("JWT PubSecKey[{}]: algorithm={}", i, algorithm);
            }
        }

        // Inline JWK keys
        for (int i = 0; i < ann.jwks().length; i++)
        {
            String jwkStr = resolveString("JWK_" + i, ann.jwks()[i], "");
            if (!jwkStr.isBlank())
            {
                opts.addJwk(new JsonObject(jwkStr));
                log.info("JWT JWK[{}] loaded", i);
            }
        }

        // JWT validation and generation options
        var jwtOpts = new JWTOptions();

        String algorithm = resolveString("ALGORITHM", ann.algorithm(), "HS256");
        jwtOpts.setAlgorithm(algorithm);

        // Multiple audiences
        String envAudience = env("AUDIENCE", null);
        if (envAudience != null && !envAudience.isBlank())
        {
            for (String aud : envAudience.split(","))
            {
                if (!aud.trim().isBlank())
                {
                    jwtOpts.addAudience(aud.trim());
                }
            }
        }
        else
        {
            for (String aud : ann.audience())
            {
                String resolved = resolveEnvPlaceholders(aud);
                if (!resolved.isBlank())
                {
                    jwtOpts.addAudience(resolved);
                }
            }
        }

        String issuer = resolveString("ISSUER", ann.issuer(), "");
        if (!issuer.isBlank())
        {
            jwtOpts.setIssuer(issuer);
        }

        String subject = resolveString("SUBJECT", ann.subject(), "");
        if (!subject.isBlank())
        {
            jwtOpts.setSubject(subject);
        }

        int leeway = Integer.parseInt(env("LEEWAY", String.valueOf(ann.leeway())));
        if (leeway > 0)
        {
            jwtOpts.setLeeway(leeway);
        }

        boolean ignoreExp = Boolean.parseBoolean(env("IGNORE_EXPIRATION", String.valueOf(ann.ignoreExpiration())));
        jwtOpts.setIgnoreExpiration(ignoreExp);

        int expiresIn = Integer.parseInt(env("EXPIRES_IN_SECONDS", String.valueOf(ann.expiresInSeconds())));
        if (expiresIn > 0)
        {
            jwtOpts.setExpiresInSeconds(expiresIn);
        }

        boolean noTimestamp = Boolean.parseBoolean(env("NO_TIMESTAMP", String.valueOf(ann.noTimestamp())));
        jwtOpts.setNoTimestamp(noTimestamp);

        opts.setJWTOptions(jwtOpts);

        return opts;
    }

    // ── Annotation discovery ────────────────────────────

    private JwtAuthOptions discoverJwtAuthOptions(ScanResult scanResult)
    {
        // Check annotated classes
        var annotatedClasses = scanResult.getClassesWithAnnotation(JwtAuthOptions.class);
        for (var ci : annotatedClasses)
        {
            var ann = ci.loadClass().getAnnotation(JwtAuthOptions.class);
            if (ann != null)
            {
                log.debug("Found @JwtAuthOptions on class: {}", ci.getName());
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
                var ann = pkgClass.getAnnotation(JwtAuthOptions.class);
                if (ann != null)
                {
                    log.debug("Found @JwtAuthOptions on package: {}", pkgInfo.getName());
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
        String value = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_JWT_" + property, null);
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

    /**
     * Resets all state — for testing purposes.
     */
    public static void reset()
    {
        jwtAuth = null;
        jwtAuthOptions = null;
    }

    @Override
    public Integer sortOrder()
    {
        return 90; // slightly before OAuth2 (100)
    }
}




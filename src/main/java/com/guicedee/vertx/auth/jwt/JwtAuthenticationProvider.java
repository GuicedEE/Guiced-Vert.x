package com.guicedee.vertx.auth.jwt;

import com.guicedee.client.utils.Discovery;

import com.guicedee.client.utils.ClassLoading;

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
 * <h2>Environment variable overrides</h2>
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
    private static io.vertx.ext.auth.jwt.JWTAuth jwtAuth;

    @Getter
    private static JwtAuthOptions jwtAuthOptions;

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        ScanResult scanResult = Discovery.scanResult();
        jwtAuthOptions = discoverJwtAuthOptions(scanResult);

        if (jwtAuthOptions == null)
        {
            log.debug("No @JwtAuthOptions annotation found — JWT provider not activated");
            return null;
        }

        Vertx vertx = VertXPreStartup.getVertx();

        try
        {
            io.vertx.ext.auth.jwt.JWTAuthOptions vertxOpts = buildVertxJwtAuthOptions(jwtAuthOptions, vertx);
            jwtAuth = io.vertx.ext.auth.jwt.JWTAuth.create(vertx, vertxOpts);

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

    private io.vertx.ext.auth.jwt.JWTAuthOptions buildVertxJwtAuthOptions(JwtAuthOptions ann, Vertx vertx)
    {
        var opts = new io.vertx.ext.auth.jwt.JWTAuthOptions();

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
        String audiences = Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_JWT_AUDIENCE", ",", ann.audience());
        for (String audience : audiences.split(",")) {
            if (!audience.isBlank()) jwtOpts.addAudience(audience.trim());
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

        int leeway = Integer.parseInt(env("LEEWAY", String.valueOf(ann.leeway() < 0 ? com.guicedee.vertx.auth.VertxAuthPreStartup.resolveLeeway() : ann.leeway())));
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
        return Discovery.singleAnnotation(JwtAuthOptions.class).orElse(null);
    }

    // ── Env / placeholder helpers ───────────────────────

    static String env(String property, String defaultValue)
    {
        return Environment.getSystemPropertyOrEnvironment("VERTX_AUTH_JWT_" + property, defaultValue);
    }

    private static String resolveString(String envKey, String annotationValue, String fallback)
    {
        return env(envKey, annotationValue != null && !annotationValue.isBlank() ? annotationValue : fallback);
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




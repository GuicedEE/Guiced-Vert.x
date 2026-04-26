package com.guicedee.vertx.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares authentication and authorization configuration for the application.
 * Place on a class or {@code package-info.java}.
 * <p>
 * All attributes can be overridden via environment variables with the
 * {@code VERTX_AUTH_} prefix.
 * <p>
 * This annotation configures:
 * <ul>
 *   <li>Authentication provider chaining strategy (ANY or ALL)</li>
 *   <li>JVM KeyStore configuration for certificate-based auth</li>
 *   <li>PEM public/secret key configuration for token-based auth</li>
 *   <li>PRNG (Pseudo Random Number Generator) settings</li>
 *   <li>User token expiration leeway</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface AuthOptions
{
    // ── Chain Strategy ────────────────────────────────

    /**
     * @return How multiple authentication providers are chained.
     *         ANY = at least one must succeed; ALL = every provider must succeed.
     */
    ChainMode chainMode() default ChainMode.ANY;

    // ── KeyStore ─────────────────────────────────────

    /**
     * @return JVM KeyStore configuration. Leave path empty to skip.
     */
    AuthKeyStore keyStore() default @AuthKeyStore;

    // ── PEM Keys ─────────────────────────────────────

    /**
     * @return PEM public/secret key configurations (can declare multiple).
     */
    AuthPubSecKey[] pubSecKeys() default {};

    // ── PRNG Configuration ───────────────────────────

    /**
     * @return PRNG algorithm (e.g. "SHA1PRNG"). Empty = system default.
     *         Maps to system property {@code io.vertx.ext.auth.prng.algorithm}.
     */
    String prngAlgorithm() default "";

    /**
     * @return PRNG seed interval in milliseconds (0 = default 300000ms / 5 minutes).
     *         Maps to system property {@code io.vertx.ext.auth.prng.seed.interval}.
     */
    long prngSeedInterval() default 0;

    /**
     * @return PRNG seed bits (0 = default 64).
     *         Maps to system property {@code io.vertx.ext.auth.prng.seed.bits}.
     */
    int prngSeedBits() default 0;

    // ── User Token Expiration ────────────────────────

    /**
     * @return Clock drift leeway in seconds for token expiration checks (exp, iat, nbf).
     *         0 = no leeway.
     */
    int leeway() default 0;

    /**
     * How multiple authentication providers are combined.
     */
    enum ChainMode
    {
        /**
         * At least one provider must authenticate successfully.
         */
        ANY,
        /**
         * All providers must authenticate successfully.
         */
        ALL
    }
}


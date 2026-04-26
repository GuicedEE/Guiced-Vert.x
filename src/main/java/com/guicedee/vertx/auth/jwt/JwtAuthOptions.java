package com.guicedee.vertx.auth.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares JWT authentication configuration for the application.
 * Place on a class or {@code package-info.java} to opt-in to JWT authentication.
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders resolved at startup,
 * and every attribute can be overridden via environment variables with the
 * {@code VERTX_AUTH_JWT_} prefix (e.g. {@code VERTX_AUTH_JWT_KEYSTORE_PATH}).
 * <p>
 * The JWT provider can load keys from three sources (checked in order):
 * <ol>
 *   <li>Java KeyStore — via {@link #keystorePath()}, {@link #keystoreType()}, {@link #keystorePassword()}</li>
 *   <li>PEM public/secret keys — via {@link #pubSecKeys()}</li>
 *   <li>JWK JSON keys — via {@link #jwks()}</li>
 * </ol>
 * <p>
 * If the existing {@link com.guicedee.vertx.auth.AuthOptions @AuthOptions} annotation is also present,
 * KeyStore and PubSecKey configuration from {@code @AuthOptions} is <strong>not</strong> duplicated —
 * the JWT provider reads its own annotation independently.
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;JwtAuthOptions(
 *     keystorePath = "keystore.jceks",
 *     keystoreType = "jceks",
 *     keystorePassword = "${JWT_KEYSTORE_PASSWORD}",
 *     algorithm = "RS256",
 *     issuer = "my-corp.com",
 *     audience = "my-service",
 *     leeway = 5,
 *     permissionsClaimKey = "realm_access/roles"
 * )
 * package com.example.auth;
 * </pre>
 *
 * @see JwtPubSecKey
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface JwtAuthOptions
{
    // ── KeyStore Configuration ───────────────────────────

    /**
     * @return Path to the Java KeyStore file (e.g. {@code keystore.jceks}).
     *         Leave empty to skip KeyStore loading.
     */
    String keystorePath() default "";

    /**
     * @return KeyStore type: {@code "jceks"}, {@code "jks"}, {@code "pkcs12"}.
     */
    String keystoreType() default "jceks";

    /**
     * @return KeyStore password.
     */
    String keystorePassword() default "";

    // ── PEM Keys ────────────────────────────────────────

    /**
     * @return PEM public/secret key configurations (can declare multiple).
     *         Used when not loading from a KeyStore.
     */
    JwtPubSecKey[] pubSecKeys() default {};

    // ── JWK Keys ────────────────────────────────────────

    /**
     * @return Inline JWK JSON strings. Each entry is a complete JWK JSON object.
     *         Example: {@code {"{\"kty\":\"RSA\",\"n\":\"...\",\"e\":\"AQAB\"}"}}
     */
    String[] jwks() default {};

    // ── JWT Validation Options ──────────────────────────

    /**
     * @return Default signing algorithm (e.g. {@code "HS256"}, {@code "RS256"}, {@code "ES256"}).
     *         Used for token generation when no algorithm is specified.
     */
    String algorithm() default "HS256";

    /**
     * @return Expected audience claims. Empty = no audience check.
     *         Multiple audiences are all required to be present in the token.
     */
    String[] audience() default {};

    /**
     * @return Expected issuer claim. Empty = no issuer check.
     */
    String issuer() default "";

    /**
     * @return Expected subject claim. Empty = no subject check.
     */
    String subject() default "";

    /**
     * @return Leeway in seconds for exp/iat/nbf validation. 0 = strict.
     */
    int leeway() default 0;

    /**
     * @return Whether to ignore token expiration. Default false.
     *         <strong>Only for development — never in production.</strong>
     */
    boolean ignoreExpiration() default false;

    /**
     * @return Token expiration time in seconds for generated tokens.
     *         0 = no expiration set.
     */
    int expiresInSeconds() default 0;

    /**
     * @return Whether generated tokens should omit the {@code iat} field.
     *         Default false.
     */
    boolean noTimestamp() default false;

    // ── Authorization Claim Path ────────────────────────

    /**
     * @return The JSON path within the token to locate permissions/roles.
     *         Default {@code "permissions"}. For Keycloak use {@code "realm_access/roles"}.
     *         Used by {@link io.vertx.ext.auth.jwt.authorization.JWTAuthorization} when
     *         {@link #authorizationType()} is {@code JWT}.
     */
    String permissionsClaimKey() default "permissions";

    /**
     * @return The authorization extraction strategy to use.
     *         {@code JWT} uses {@link #permissionsClaimKey()} to locate claims.
     *         {@code MICROPROFILE} follows the MP-JWT 1.1 {@code groups} claim spec.
     *         {@code NONE} disables automatic authorization provider registration.
     */
    JwtAuthorizationType authorizationType() default JwtAuthorizationType.JWT;
}




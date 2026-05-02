package com.guicedee.vertx.auth.oauth2;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares OAuth2 / OpenID Connect authentication configuration.
 * Place on a class or {@code package-info.java} to opt-in to OAuth2 authentication.
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders resolved at startup,
 * and every attribute can be overridden via environment variables with the
 * {@code VERTX_AUTH_OAUTH2_} prefix (e.g. {@code VERTX_AUTH_OAUTH2_CLIENT_ID}).
 * <p>
 * When a {@link #wellKnownProvider()} other than {@code CUSTOM} is set,
 * OpenID Connect Discovery is used automatically — only {@link #clientId()}
 * and {@link #clientSecret()} are required.
 *
 * <h2>Usage</h2>
 * <pre>
 * // package-info.java
 * &#64;OAuth2Options(
 *     clientId = "${OAUTH2_CLIENT_ID}",
 *     clientSecret = "${OAUTH2_CLIENT_SECRET}",
 *     wellKnownProvider = WellKnownProvider.KEYCLOAK,
 *     site = "https://keycloak.example.com/auth/realms/myrealm",
 *     flow = OAuth2Flow.AUTH_CODE
 * )
 * package com.example.auth;
 * </pre>
 *
 * @see OAuth2Flow
 * @see WellKnownProvider
 * @see OAuth2JwtOptions
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface OAuth2Options
{
    // ── Client Credentials ──────────────────────────────

    /**
     * The OAuth2 client ID.
     *
     * @return OAuth2 client ID. Required.
     */
    String clientId() default "";

    /**
     * The OAuth2 client secret.
     *
     * @return OAuth2 client secret. Required for confidential clients.
     */
    String clientSecret() default "";

    /**
     * The tenant or realm identifier.
     *
     * @return Tenant / realm identifier (used by Keycloak, Azure AD, IBM Cloud).
     */
    String tenant() default "";

    // ── Provider Selection ──────────────────────────────

    /**
     * The well-known OAuth2/OIDC provider.
     *
     * @return A well-known OAuth2/OIDC provider.
     *         When not {@code CUSTOM}, discovery is used automatically.
     */
    WellKnownProvider wellKnownProvider() default WellKnownProvider.CUSTOM;

    /**
     * The base site URL for the provider.
     *
     * @return Base site URL for custom providers or Keycloak/IBM Cloud
     *         (e.g. {@code https://keycloak.example.com/auth/realms/myrealm}).
     *         Ignored for well-known providers that derive the URL themselves (Google, Azure, etc.).
     */
    String site() default "";

    // ── Flow ────────────────────────────────────────────

    /**
     * The OAuth2 grant flow.
     *
     * @return The OAuth2 grant flow to use. Default is Authorization Code.
     */
    OAuth2Flow flow() default OAuth2Flow.AUTH_CODE;

    // ── Endpoint Overrides (custom providers) ───────────

    /**
     * The authorization endpoint path.
     *
     * @return Path to the authorization endpoint (e.g. {@code /oauth/authorize}).
     *         Only needed for {@code CUSTOM} providers.
     */
    String authorizationPath() default "";

    /**
     * The token endpoint path.
     *
     * @return Path to the token endpoint (e.g. {@code /oauth/access_token}).
     */
    String tokenPath() default "";

    /**
     * The token revocation endpoint path.
     *
     * @return Path to the token revocation endpoint.
     */
    String revocationPath() default "";

    /**
     * The token introspection endpoint path.
     *
     * @return Path to the token introspection endpoint (RFC 7662).
     */
    String introspectionPath() default "";

    /**
     * The JWK key set endpoint path.
     *
     * @return Path to the JWK key set endpoint.
     */
    String jwkPath() default "";

    /**
     * The userinfo endpoint path.
     *
     * @return Path to the userinfo endpoint.
     */
    String userInfoPath() default "";

    /**
     * The logout endpoint path.
     *
     * @return Path to the end-session / logout endpoint.
     */
    String logoutPath() default "";

    // ── Scope ───────────────────────────────────────────

    /**
     * The default scopes to request.
     *
     * @return Default scopes to request, comma-separated (e.g. {@code "openid,profile,email"}).
     */
    String scopes() default "";

    /**
     * The scope delimiter character.
     *
     * @return Scope delimiter character. Default is a single space per OAuth2 spec.
     */
    String scopeDelimiter() default " ";

    // ── JWT Options ─────────────────────────────────────

    /**
     * The JWT validation options.
     *
     * @return JWT validation options (audience, issuer, leeway, etc.).
     */
    OAuth2JwtOptions jwtOptions() default @OAuth2JwtOptions;

    // ── Extra Parameters ────────────────────────────────

    /**
     * The extra query parameters.
     *
     * @return Extra query parameters as {@code key=value} pairs, comma-separated.
     *         E.g. {@code "prompt=consent,access_type=offline"}.
     */
    String extraParameters() default "";

    // ── Callback / Redirect ─────────────────────────────

    /**
     * The redirect URI.
     *
     * @return The redirect URI registered with the OAuth2 provider.
     *         Used in Authorization Code flow.
     */
    String redirectUri() default "";

    // ── Timeouts ────────────────────────────────────────

    /**
     * The HTTP connect timeout.
     *
     * @return HTTP connect timeout in milliseconds for OAuth2 HTTP requests. 0 = default.
     */
    int connectTimeout() default 0;

    /**
     * The HTTP read/idle timeout.
     *
     * @return HTTP read/idle timeout in milliseconds for OAuth2 HTTP requests. 0 = default.
     */
    int readTimeout() default 0;
}


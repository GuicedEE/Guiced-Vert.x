package com.guicedee.vertx.auth.oauth2;

/**
 * Well-known OAuth2 / OpenID Connect providers with built-in discovery support.
 * <p>
 * When a provider other than {@link #CUSTOM} is selected, the system uses
 * OpenID Connect Discovery (or provider-specific helpers) to auto-configure
 * endpoints, JWK keys, and token validation — only client ID and secret are required.
 */
public enum WellKnownProvider
{
    /**
     * Custom provider — all endpoints must be configured manually via
     * {@link OAuth2Options#site()}, {@link OAuth2Options#authorizationPath()}, etc.
     */
    CUSTOM,

    /**
     * Google — uses {@code GoogleAuth.discover()}.
     */
    GOOGLE,

    /**
     * Microsoft Azure Active Directory — uses {@code AzureADAuth.discover()}.
     * Requires {@link OAuth2Options#tenant()}.
     */
    AZURE_AD,

    /**
     * Keycloak — uses {@code KeycloakAuth.discover()}.
     * Requires {@link OAuth2Options#site()} pointing to
     * {@code https://host:port/auth/realms/{realm}} and {@link OAuth2Options#tenant()}.
     */
    KEYCLOAK,

    /**
     * Salesforce — uses {@code SalesforceAuth.discover()}.
     */
    SALESFORCE,

    /**
     * GitHub — uses custom OAuth2 configuration (GitHub does not support OIDC discovery).
     */
    GITHUB,

    /**
     * IBM Cloud App ID — uses {@code IBMCloudAuth.discover()}.
     * Requires {@link OAuth2Options#site()} and {@link OAuth2Options#tenant()}.
     */
    IBM_CLOUD,

    /**
     * Amazon Cognito — uses OpenID Connect Discovery.
     * Requires {@link OAuth2Options#site()} pointing to
     * {@code https://cognito-idp.<region>.amazonaws.com/<user-pool-id>}.
     */
    AMAZON_COGNITO,

    /**
     * Generic OpenID Connect provider — uses {@code OpenIDConnectAuth.discover()}.
     * Requires {@link OAuth2Options#site()}.
     */
    OPENID_CONNECT
}


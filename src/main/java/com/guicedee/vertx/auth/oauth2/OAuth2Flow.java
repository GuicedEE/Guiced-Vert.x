package com.guicedee.vertx.auth.oauth2;

/**
 * OAuth2 grant flow types supported by the provider.
 */
public enum OAuth2Flow
{
    /**
     * Authorization Code flow — for server-side applications that can
     * maintain a client secret and handle redirects.
     */
    AUTH_CODE,

    /**
     * Resource Owner Password Credentials flow — the client collects
     * username/password directly. Use only when other flows are not viable.
     */
    PASSWORD,

    /**
     * Client Credentials flow — machine-to-machine authentication using
     * only the client ID and secret.
     */
    CLIENT_CREDENTIALS,

    /**
     * JWT Bearer / On-Behalf-Of flow (RFC 7523) — the client authenticates
     * with a signed JWT assertion.
     */
    AUTH_JWT
}


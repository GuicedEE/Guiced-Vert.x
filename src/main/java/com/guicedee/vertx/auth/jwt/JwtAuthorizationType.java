package com.guicedee.vertx.auth.jwt;

/**
 * Strategy for extracting authorization claims from JWT tokens.
 */
public enum JwtAuthorizationType
{
    /**
     * Uses {@link io.vertx.ext.auth.jwt.authorization.JWTAuthorization} with a configurable
     * {@link JwtAuthOptions#permissionsClaimKey()} (default {@code "permissions"}).
     */
    JWT,

    /**
     * Uses {@link io.vertx.ext.auth.jwt.authorization.MicroProfileAuthorization} which
     * follows the MicroProfile JWT 1.1 spec ({@code groups} claim).
     */
    MICROPROFILE,

    /**
     * No authorization provider is automatically registered.
     * Use this if you handle authorization manually.
     */
    NONE
}


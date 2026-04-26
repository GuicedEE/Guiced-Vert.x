package com.guicedee.vertx.auth.jwt;

import com.guicedee.vertx.auth.IGuicedAuthorizationProvider;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import io.vertx.ext.auth.jwt.authorization.JWTAuthorization;
import io.vertx.ext.auth.jwt.authorization.MicroProfileAuthorization;
import lombok.extern.log4j.Log4j2;

/**
 * Auto-discovered JWT authorization provider for GuicedEE.
 * <p>
 * Activated <strong>only</strong> when a {@link JwtAuthOptions} annotation is found
 * and {@link JwtAuthOptions#authorizationType()} is not {@code NONE}.
 * <p>
 * Creates either a {@link JWTAuthorization} (using the configured
 * {@link JwtAuthOptions#permissionsClaimKey()}) or a {@link MicroProfileAuthorization}
 * depending on the configured type.
 */
@Log4j2
public class JwtAuthorizationProvider implements IGuicedAuthorizationProvider
{
    @Override
    public AuthorizationProvider getAuthorizationProvider()
    {
        JwtAuthOptions opts = JwtAuthenticationProvider.getJwtAuthOptions();
        if (opts == null)
        {
            log.debug("No @JwtAuthOptions found — JWT authorization provider not activated");
            return null;
        }

        String authzTypeStr = JwtAuthenticationProvider.env("AUTHORIZATION_TYPE", opts.authorizationType().name());
        JwtAuthorizationType authzType = JwtAuthorizationType.valueOf(authzTypeStr);

        if (authzType == JwtAuthorizationType.NONE)
        {
            log.debug("JWT authorization type is NONE — skipping");
            return null;
        }

        return switch (authzType)
        {
            case JWT ->
            {
                String claimKey = JwtAuthenticationProvider.env("PERMISSIONS_CLAIM_KEY", opts.permissionsClaimKey());
                log.info("JWT authorization provider created: type=JWT, permissionsClaimKey={}", claimKey);
                yield JWTAuthorization.create(claimKey);
            }
            case MICROPROFILE ->
            {
                log.info("JWT authorization provider created: type=MicroProfile (groups claim)");
                yield MicroProfileAuthorization.create();
            }
            case NONE -> null;
        };
    }

    @Override
    public Integer sortOrder()
    {
        return 90;
    }
}


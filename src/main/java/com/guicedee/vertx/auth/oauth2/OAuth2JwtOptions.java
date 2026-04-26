package com.guicedee.vertx.auth.oauth2;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JWT validation options for OAuth2 token verification.
 * Nested within {@link OAuth2Options}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface OAuth2JwtOptions
{
    /**
     * @return Expected audience claim. Empty = no audience check.
     */
    String audience() default "";

    /**
     * @return Expected issuer claim. Empty = no issuer check.
     */
    String issuer() default "";

    /**
     * @return Leeway in seconds for exp/iat/nbf validation. 0 = strict.
     */
    int leeway() default 0;

    /**
     * @return Whether to ignore token expiration. Default false.
     *         <strong>Only for development — never in production.</strong>
     */
    boolean ignoreExpiration() default false;
}


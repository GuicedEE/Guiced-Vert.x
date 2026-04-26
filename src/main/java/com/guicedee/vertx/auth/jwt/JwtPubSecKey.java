package com.guicedee.vertx.auth.jwt;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nested annotation for PEM-based public/secret key configuration within {@link JwtAuthOptions}.
 * Maps to Vert.x {@code PubSecKeyOptions}.
 * <p>
 * PEM files must be in PKCS8 format. Convert with:
 * <pre>openssl pkcs8 -topk8 -inform PEM -in private.pem -out private_key.pem -nocrypt</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface JwtPubSecKey
{
    /**
     * @return Algorithm for the key (e.g. {@code "RS256"}, {@code "ES256"}, {@code "HS256"}).
     */
    String algorithm() default "";

    /**
     * @return The PEM-encoded key content (inline).
     *         If empty, use {@link #path()} to load from a file.
     */
    String buffer() default "";

    /**
     * @return Path to a PEM file to load the key from.
     *         Ignored if {@link #buffer()} is set.
     */
    String path() default "";

    /**
     * @return Key ID (kid) for JWK matching. Optional.
     */
    String id() default "";
}


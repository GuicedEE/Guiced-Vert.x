package com.guicedee.vertx.auth.ldap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares LDAP authentication configuration.
 * Place on a class or {@code package-info.java} to opt-in to LDAP authentication.
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders and can be overridden
 * via environment variables with the {@code VERTX_AUTH_LDAP_} prefix.
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;LdapAuthOptions(
 *     url = "ldap://ldap.example.com:389",
 *     authenticationQuery = "uid={0},ou=users,dc=example,dc=com"
 * )
 * package com.example.auth;
 * </pre>
 *
 * @see io.vertx.ext.auth.ldap.LdapAuthentication
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface LdapAuthOptions
{
    /**
     * @return The LDAP server URL. Must start with {@code ldap://} and include a port.
     *         Example: {@code ldap://myldapserver.mycompany.com:10389}
     */
    String url();

    /**
     * @return The authentication query template. The {@code {0}} placeholder is substituted
     *         with the user identifier. Example: {@code uid={0},ou=users,dc=foo,dc=com}
     */
    String authenticationQuery();

    /**
     * @return The LDAP authentication mechanism. Default {@code "simple"}.
     *         Other values depend on the LDAP server (e.g. {@code "DIGEST-MD5"}, {@code "GSSAPI"}).
     */
    String authenticationMechanism() default "simple";

    /**
     * @return The LDAP referral behavior. Default {@code "follow"}.
     *         See <a href="http://java.sun.com/products/jndi/tutorial/ldap/referral/jndi.html">JNDI Referral docs</a>.
     */
    String referral() default "follow";
}


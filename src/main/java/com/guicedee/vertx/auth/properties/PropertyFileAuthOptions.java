package com.guicedee.vertx.auth.properties;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares property-file-based authentication and authorization configuration.
 * Place on a class or {@code package-info.java} to opt-in.
 * <p>
 * Uses the Apache Shiro properties file format:
 * <pre>
 * user.tim = mypassword,administrator,developer
 * user.bob = hispassword,developer
 * role.administrator = *
 * role.developer = do_actual_work
 * </pre>
 * <p>
 * This provider supplies <strong>both</strong> authentication (username/password)
 * and authorization (role/permission lookup) from the same file.
 * <p>
 * All string attributes support {@code ${ENV_VAR}} placeholders and can be overridden
 * via environment variables with the {@code VERTX_AUTH_PROPERTIES_} prefix.
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;PropertyFileAuthOptions(path = "auth.properties")
 * package com.example.auth;
 * </pre>
 *
 * @see io.vertx.ext.auth.properties.PropertyFileAuthentication
 * @see io.vertx.ext.auth.properties.PropertyFileAuthorization
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface PropertyFileAuthOptions
{
    /**
     * @return Path to the properties file. Resolved via the Vert.x filesystem
     *         (classpath or absolute path). Required.
     */
    String path();
}


package com.guicedee.vertx.auth;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nested annotation for JVM KeyStore configuration within {@link AuthOptions}.
 * Maps to Vert.x {@code KeyStoreOptions}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface AuthKeyStore
{
    /**
     * @return Path to the keystore file.
     */
    String path() default "";

    /**
     * @return Keystore type (e.g. "pkcs12", "jks"). Default follows JVM version conventions.
     */
    String type() default "pkcs12";

    /**
     * @return Keystore password.
     */
    String password() default "";

    /**
     * @return Alias for the key within the keystore.
     */
    String alias() default "";

    /**
     * @return Password for the specific key alias (if different from keystore password).
     */
    String aliasPassword() default "";
}



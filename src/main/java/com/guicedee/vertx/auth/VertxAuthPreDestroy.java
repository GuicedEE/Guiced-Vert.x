package com.guicedee.vertx.auth;

import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import lombok.extern.log4j.Log4j2;

/**
 * Cleans up auth state on application shutdown.
 */
@Log4j2
public class VertxAuthPreDestroy implements IGuicePreDestroy<VertxAuthPreDestroy>
{
    @Override
    public void onDestroy()
    {
        log.info("Cleaning up Vert.x auth state");
        // Clean up OAuth2 provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.oauth2.OAuth2AuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up JWT provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.jwt.JwtAuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up ABAC provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.abac.AbacAuthorizationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up OTP provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.otp.OtpAuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up property file providers if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.properties.PropertyFileAuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        try
        {
            Class.forName("com.guicedee.vertx.auth.properties.PropertyFileAuthorizationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up LDAP provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.ldap.LdapAuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up htpasswd provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.htpasswd.HtpasswdAuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        // Clean up htdigest provider if present
        try
        {
            Class.forName("com.guicedee.vertx.auth.htdigest.HtdigestAuthenticationProvider")
                    .getMethod("reset").invoke(null);
        }
        catch (Exception _) { }
        VertxAuthPreStartup.reset();
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MAX_VALUE - 50;
    }
}


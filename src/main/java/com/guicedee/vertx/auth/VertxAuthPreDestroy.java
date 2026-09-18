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
        String[][] providers = {
                {"oauth2.OAuth2Auth", "oauth2.OAuth2AuthenticationProvider"},
                {"jwt.JWTAuth", "jwt.JwtAuthenticationProvider"},
                {"abac.PolicyBasedAuthorizationProvider", "abac.AbacAuthorizationProvider"},
                {"otp.totp.TotpAuth", "otp.OtpAuthenticationProvider"},
                {"properties.PropertyFileAuthentication", "properties.PropertyFileAuthenticationProvider"},
                {"properties.PropertyFileAuthorization", "properties.PropertyFileAuthorizationProvider"},
                {"ldap.LdapAuthentication", "ldap.LdapAuthenticationProvider"},
                {"htpasswd.HtpasswdAuth", "htpasswd.HtpasswdAuthenticationProvider"},
                {"htdigest.HtdigestAuth", "htdigest.HtdigestAuthenticationProvider"}
        };
        IllegalStateException failure = null;
        for (String[] provider : providers)
        {
            try
            {
                Class.forName("io.vertx.ext.auth." + provider[0], false, getClass().getClassLoader());
            }
            catch (ClassNotFoundException absent)
            {
                continue;
            }
            try
            {
                Class.forName("com.guicedee.vertx.auth." + provider[1]).getMethod("reset").invoke(null);
            }
            catch (ReflectiveOperationException | LinkageError failed)
            {
                if (failure == null) failure = new IllegalStateException("Vert.x auth cleanup failed");
                failure.addSuppressed(failed);
            }
        }
        VertxAuthPreStartup.reset();
        if (failure != null) throw failure;
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MAX_VALUE - 50;
    }
}


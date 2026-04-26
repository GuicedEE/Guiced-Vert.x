import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePostStartup;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.vertx.*;
import com.guicedee.vertx.auth.*;
import com.guicedee.vertx.auth.oauth2.OAuth2AuthenticationProvider;
import com.guicedee.vertx.auth.jwt.JwtAuthenticationProvider;
import com.guicedee.vertx.auth.jwt.JwtAuthorizationProvider;
import com.guicedee.vertx.auth.abac.AbacAuthorizationProvider;
import com.guicedee.vertx.auth.otp.OtpAuthenticationProvider;
import com.guicedee.vertx.auth.properties.PropertyFileAuthenticationProvider;
import com.guicedee.vertx.auth.properties.PropertyFileAuthorizationProvider;
import com.guicedee.vertx.auth.ldap.LdapAuthenticationProvider;
import com.guicedee.vertx.auth.htpasswd.HtpasswdAuthenticationProvider;
import com.guicedee.vertx.auth.htdigest.HtdigestAuthenticationProvider;
import com.guicedee.vertx.implementations.VertxClassScanConfig;
import com.guicedee.vertx.spi.*;

module com.guicedee.vertx {
    requires transitive io.vertx.core;
    requires transitive io.vertx.auth.common;

    requires static io.vertx.auth.oauth2;
    requires static io.vertx.auth.jwt;
    requires static io.vertx.auth.abac;
    requires static io.vertx.auth.otp;
    requires static io.vertx.auth.properties;
    requires static io.vertx.auth.ldap;
    requires static io.vertx.auth.htpasswd;
    requires static io.vertx.auth.htdigest;

    requires transitive com.guicedee.client;
    requires transitive com.guicedee.jsonrepresentation;

    requires transitive org.apache.logging.log4j;

    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.core;

    requires transitive io.smallrye.mutiny;
    requires transitive io.vertx.mutiny;

    requires transitive jakarta.cdi;

    requires io.github.classgraph;

    requires static lombok;

    exports com.guicedee.vertx.spi;
    exports com.guicedee.vertx;
    exports com.guicedee.vertx.auth;
    exports com.guicedee.vertx.auth.oauth2;
    exports com.guicedee.vertx.auth.jwt;
    exports com.guicedee.vertx.auth.abac;
    exports com.guicedee.vertx.auth.otp;
    exports com.guicedee.vertx.auth.properties;
    exports com.guicedee.vertx.auth.ldap;
    exports com.guicedee.vertx.auth.htpasswd;
    exports com.guicedee.vertx.auth.htdigest;

    opens com.guicedee.vertx to com.google.guice;
    opens com.guicedee.vertx.spi to com.google.guice;
    opens com.guicedee.vertx.auth to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.oauth2 to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.jwt to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.abac to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.otp to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.properties to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.ldap to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.htpasswd to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.vertx.auth.htdigest to com.google.guice, com.fasterxml.jackson.databind;

    provides IGuicePreStartup with VertXPreStartup, VertxAuthPreStartup;
    provides IGuicePostStartup with VertxVerticlePostStartup;
    provides IGuicePreDestroy with VertXPostStartup, VertxAuthPreDestroy;
    provides IGuiceModule with VertXModule, VertxAuthModule;
    provides com.guicedee.client.services.lifecycle.IGuiceConfigurator with VertxClassScanConfig;

    uses com.guicedee.vertx.spi.VertxConfigurator;
    uses VerticleStartup;
    uses IGuicedAuthenticationProvider;
    uses IGuicedAuthorizationProvider;

    provides VerticleStartup with com.guicedee.vertx.spi.VertxConsumersStartup;
    provides IGuicedAuthenticationProvider with OAuth2AuthenticationProvider, JwtAuthenticationProvider, OtpAuthenticationProvider, PropertyFileAuthenticationProvider, LdapAuthenticationProvider, HtpasswdAuthenticationProvider, HtdigestAuthenticationProvider;
    provides IGuicedAuthorizationProvider with JwtAuthorizationProvider, AbacAuthorizationProvider, PropertyFileAuthorizationProvider;

    uses com.guicedee.vertx.auth.abac.IAbacPolicyProvider;
    uses com.guicedee.vertx.auth.otp.IOtpAuthenticatorService;
}
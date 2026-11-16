package com.guicedee.vertx.auth.oauth2;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import java.util.List;

/** Applies deployment defaults to OAuth requests without modifying caller-owned objects. */
final class ConfiguredOAuth2Auth implements OAuth2Auth {
    private final OAuth2Auth delegate;
    private final List<String> scopes;
    private final String redirectUri;

    ConfiguredOAuth2Auth(OAuth2Auth delegate, String scopes, String redirectUri) {
        this.delegate = delegate;
        this.scopes = java.util.Arrays.stream(scopes.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toList();
        this.redirectUri = redirectUri;
    }

    @Override public String authorizeURL(OAuth2AuthorizationURL request) {
        var resolved = new OAuth2AuthorizationURL(request);
        if (resolved.getScopes() == null && !scopes.isEmpty()) resolved.setScopes(scopes);
        if (resolved.getRedirectUri() == null && !redirectUri.isBlank()) resolved.setRedirectUri(redirectUri);
        return delegate.authorizeURL(resolved);
    }

    @Override public Future<User> authenticate(Credentials credentials) {
        if (credentials instanceof Oauth2Credentials request) {
            var resolved = new Oauth2Credentials(request.toJson().copy());
            if (resolved.getScopes() == null) scopes.forEach(resolved::addScope);
            if (resolved.getRedirectUri() == null && !redirectUri.isBlank()) resolved.setRedirectUri(redirectUri);
            return delegate.authenticate(resolved);
        }
        return delegate.authenticate(credentials);
    }

    @Override public Future<Void> jWKSet() { return delegate.jWKSet(); }
    @Override public OAuth2Auth missingKeyHandler(Handler<String> handler) { delegate.missingKeyHandler(handler); return this; }
    @Override public Future<User> refresh(User user) { return delegate.refresh(user); }
    @Override public Future<Void> revoke(User user, String tokenType) { return delegate.revoke(user, tokenType); }
    @Override public Future<JsonObject> userInfo(User user) { return delegate.userInfo(user); }
    @Override public String endSessionURL(User user, JsonObject params) { return delegate.endSessionURL(user, params); }
    @Override public void close() { delegate.close(); }
}

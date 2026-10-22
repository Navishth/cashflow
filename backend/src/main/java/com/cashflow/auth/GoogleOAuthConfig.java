package com.cashflow.auth;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthConfig {
    private final ClientRegistration registration;
    private final String authorizationUrl;
    private static final String CALLBACK = "/login/oauth2/code/google";

    public GoogleOAuthConfig(@Value("${app.google-client-id}") String id,
            @Value("${app.google-client-secret}") String secret,
            @Value("${app.google-redirect-uri:}") String redirect,
            @Value("${app.frontend-url}") String frontend,
            @Value("${app.production}") boolean production) {
        id = id.trim(); secret = secret.trim();
        if (id.isBlank() != secret.isBlank()) {
            throw new IllegalStateException("Set both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET, or leave both empty.");
        }
        if (id.isBlank()) { registration = null; authorizationUrl = null; return; }
        String callback = redirect.isBlank() ? frontend.replaceAll("/+$", "") + CALLBACK : redirect.trim();
        URI uri;
        try { uri = URI.create(callback); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("GOOGLE_REDIRECT_URI must be a valid absolute callback URL."); }
        boolean localHttp = "http".equals(uri.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost() == null ? "" : uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().endsWith(CALLBACK) || !("https".equals(uri.getScheme()) || (!production && localHttp))) {
            throw new IllegalStateException("GOOGLE_REDIRECT_URI must end with /login/oauth2/code/google and use HTTPS (HTTP loopback is allowed locally).");
        }
        registration = CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(id).clientSecret(secret)
                .scope("openid", "profile", "email").redirectUri(callback).build();
        // Start and finish on the same configured host so the OAuth state cookie survives localhost/127.0.0.1 aliases.
        authorizationUrl = callback.substring(0, callback.length() - CALLBACK.length()) + "/oauth2/authorization/google";
    }

    public boolean enabled() { return registration != null; }
    public ClientRegistration registration() { return registration; }
    public String authorizationUrl() { return authorizationUrl; }
}

package com.cashflow;

import com.cashflow.auth.GoogleOAuthConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GoogleOAuthConfigTest {
    private static final String ID = "test-only.apps.googleusercontent.com";
    private static final String SECRET = "test-only-not-a-real-secret";

    @Test void disablesGoogleWhenBothCredentialsAreMissingAndRejectsPartialConfiguration() {
        assertThat(config("", "", "", false).enabled()).isFalse();
        assertThatThrownBy(() -> config(ID, "", "", false)).hasMessageContaining("Set both GOOGLE_CLIENT_ID");
        assertThatThrownBy(() -> config("", SECRET, "", false)).hasMessageContaining("Set both GOOGLE_CLIENT_ID");
    }

    @Test void keepsAuthorizationAndCallbackOnTheSameCanonicalOrigin() {
        var google = config(ID, SECRET, "", false);
        assertThat(google.registration().getRedirectUri()).isEqualTo("http://localhost:5173/login/oauth2/code/google");
        assertThat(google.authorizationUrl()).isEqualTo("http://localhost:5173/oauth2/authorization/google");
        assertThat(google.registration().getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
    }

    @Test void acceptsAnExplicitHttpsBackendCallbackForDeployment() {
        var google = config(ID, SECRET, "https://api.example.test/login/oauth2/code/google", true);
        assertThat(google.authorizationUrl()).isEqualTo("https://api.example.test/oauth2/authorization/google");
        assertThat(google.registration().getRedirectUri()).isEqualTo("https://api.example.test/login/oauth2/code/google");
    }

    @Test void rejectsUnsafeOrIncorrectCallbackConfiguration() {
        for (String invalid : new String[]{"http://example.test/login/oauth2/code/google", "https://example.test/wrong",
                "https://example.test/login/oauth2/code/google?next=evil", "https://user@example.test/login/oauth2/code/google",
                "/login/oauth2/code/google", "https://example.test/login/oauth2/code/google#token"}) {
            assertThatThrownBy(() -> config(ID, SECRET, invalid, false)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> config(ID, SECRET, "http://localhost:5173/login/oauth2/code/google", true)).isInstanceOf(IllegalStateException.class);
    }

    private GoogleOAuthConfig config(String id, String secret, String redirect, boolean production) {
        return new GoogleOAuthConfig(id, secret, redirect, "http://localhost:5173/", production);
    }
}

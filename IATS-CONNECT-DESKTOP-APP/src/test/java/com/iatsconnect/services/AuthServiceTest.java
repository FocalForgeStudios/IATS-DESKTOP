package com.iatsconnect.services;

import static com.iatsconnect.services.ServiceTestSupport.reply;
import static org.junit.jupiter.api.Assertions.*;

import com.iatsconnect.services.ApiException.Kind;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final String TOKEN_BODY = "{\"access_token\":\"%s\",\"refresh_token\":\"r1\",\"user\":{\"id\":\"u1\",\"email\":\"a@b.co\"}}";

    @Test void validatesInputBeforeUsingTheNetwork() throws Exception {
        try (var s = new ServiceTestSupport()) {
            var auth = new AuthService(s.client(), s.config()); // no handlers registered: any request would fail differently
            assertEquals(Kind.VALIDATION, assertThrows(ApiException.class, () -> auth.signIn("not-an-email", "x")).kind());
            assertEquals(Kind.VALIDATION, assertThrows(ApiException.class, () -> auth.signIn("a@b.co", "")).kind());
            assertEquals(Kind.VALIDATION, assertThrows(ApiException.class, () -> auth.signUp("Ann", "a@b.co", "short")).kind());
        }
    }

    @Test void wrongPasswordGivesFriendlyUnauthorizedMessage() throws Exception {
        try (var s = new ServiceTestSupport()) {
            s.on("/auth/v1/token", ex -> reply(ex, 400, "{\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"}"));
            var auth = new AuthService(s.client(), s.config());
            var e = assertThrows(ApiException.class, () -> auth.signIn("a@b.co", "wrong"));
            assertEquals(Kind.UNAUTHORIZED, e.kind());
            assertEquals("Incorrect email or password.", e.userMessage());
        }
    }

    @Test void expiredTokenIsRefreshedTransparentlyAndRequestRetried() throws Exception {
        try (var s = new ServiceTestSupport()) {
            AtomicInteger refreshes = new AtomicInteger();
            s.on("/auth/v1/token", ex -> {
                if (ex.getRequestURI().getQuery().contains("refresh_token")) { refreshes.incrementAndGet(); reply(ex, 200, TOKEN_BODY.formatted("fresh")); }
                else reply(ex, 200, TOKEN_BODY.formatted("stale"));
            });
            s.on("/rest/v1/student_profiles", ex -> {
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                if ("Bearer fresh".equals(auth)) reply(ex, 200, "[{\"id\":\"u1\"}]"); else reply(ex, 401, "{\"code\":\"PGRST301\",\"message\":\"JWT expired\"}");
            });
            var client = s.client();
            var auth = new AuthService(client, s.config());
            auth.signIn("a@b.co", "pw");
            var json = client.rest("GET", "student_profiles", null);
            assertEquals("u1", json.get(0).get("id").asText());
            assertEquals(1, refreshes.get());
            assertEquals("fresh", client.getAccessToken());
        }
    }

    @Test void failedRefreshSignsUserOutAndNotifiesListener() throws Exception {
        try (var s = new ServiceTestSupport()) {
            s.on("/auth/v1/token", ex -> {
                if (ex.getRequestURI().getQuery().contains("refresh_token")) reply(ex, 400, "{\"error_code\":\"refresh_token_not_found\",\"msg\":\"Invalid Refresh Token\"}");
                else reply(ex, 200, TOKEN_BODY.formatted("stale"));
            });
            s.on("/rest/v1/groups", ex -> reply(ex, 401, "{\"message\":\"JWT expired\"}"));
            var client = s.client();
            var auth = new AuthService(client, s.config());
            AtomicReference<Boolean> notified = new AtomicReference<>(false);
            auth.setSessionExpiredListener(() -> notified.set(true));
            auth.signIn("a@b.co", "pw");
            var e = assertThrows(ApiException.class, () -> client.rest("GET", "groups", null));
            assertEquals(Kind.UNAUTHORIZED, e.kind());
            assertTrue(notified.get());
            assertNull(client.getAccessToken());
        }
    }

    @Test void sessionToStringNeverLeaksTokens() {
        var u = new AuthService.UserSession("SECRET-ACCESS", "SECRET-REFRESH", "u1", "a@b.co", false, false);
        assertFalse(u.toString().contains("SECRET"));
        assertFalse(new com.iatsconnect.models.Session("SECRET-ACCESS", "SECRET-REFRESH", "u1", "a@b.co").toString().contains("SECRET"));
    }
}

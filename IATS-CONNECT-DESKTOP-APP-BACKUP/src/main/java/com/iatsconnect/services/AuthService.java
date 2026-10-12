package com.iatsconnect.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.iatsconnect.config.AppConfig;
import com.iatsconnect.services.ApiException.Kind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sign-in, registration, password reset and token refresh. Validates input before touching the
 * network and reports every failure as an {@link ApiException} with a user-friendly message.
 */
public final class AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SupabaseRestClient client;
    private final AppConfig config;
    private volatile String refreshToken;

    public AuthService(SupabaseRestClient client, AppConfig config) {
        this.client = client;
        this.config = config;
        client.setTokenRefresher(this::refreshQuietly);
    }

    /** Called (on a background thread) when the session cannot be renewed and the user must sign in again. */
    public void setSessionExpiredListener(Runnable listener) {
        client.setSessionExpiredListener(() -> { clearLocal(); listener.run(); });
    }

    public UserSession signIn(String email, String password) throws ApiException {
        requireEmail(email);
        if (password == null || password.isEmpty()) throw new ApiException(Kind.VALIDATION, "Enter your password.");
        JsonNode n = client.auth("/token?grant_type=password", client.json(Map.of("email", email.trim(), "password", password)));
        if (n.path("access_token").asText("").isBlank()) {
            throw new ApiException(Kind.PARSE, "Sign-in succeeded but the server returned no session. Please try again.");
        }
        return session(n, email);
    }

    public UserSession signUp(String name, String email, String password) throws ApiException {
        if (name == null || name.isBlank()) throw new ApiException(Kind.VALIDATION, "Enter your full name.");
        requireEmail(email);
        if (password == null || password.length() < 8) {
            throw new ApiException(Kind.VALIDATION, "Choose a password of at least 8 characters.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email.trim());
        payload.put("password", password);
        payload.put("data", Map.of("full_name", name.trim()));
        JsonNode n = client.auth("/signup", client.json(payload));
        String token = n.path("access_token").asText("");
        if (!token.isBlank()) { client.setAccessToken(token); refreshToken = n.path("refresh_token").asText(""); }
        String uid = n.path("user").path("id").asText(n.path("id").asText(""));
        String actualEmail = n.path("user").path("email").asText(n.path("email").asText(email.trim()));
        return new UserSession(token, n.path("refresh_token").asText(""), uid, actualEmail,
            actualEmail.equalsIgnoreCase(config.adminEmail()), token.isBlank());
    }

    public void requestPasswordReset(String email) throws ApiException {
        requireEmail(email);
        client.auth("/recover", client.json(Map.of("email", email.trim())));
    }

    /** Exchanges the stored refresh token for a new access token. */
    public synchronized UserSession refresh() throws ApiException {
        String rt = refreshToken;
        if (rt == null || rt.isBlank()) throw new ApiException(Kind.UNAUTHORIZED, "Your session has expired. Please sign in again.");
        JsonNode n = client.auth("/token?grant_type=refresh_token", client.json(Map.of("refresh_token", rt)));
        if (n.path("access_token").asText("").isBlank()) {
            throw new ApiException(Kind.UNAUTHORIZED, "Your session has expired. Please sign in again.");
        }
        return session(n, n.path("user").path("email").asText(""));
    }

    /** Clears the local session immediately; the server-side logout is best effort and never blocks or throws. */
    public void signOut() {
        String token = client.getAccessToken();
        clearLocal();
        if (token == null || token.isBlank() || !config.isSupabaseConfigured()) return;
        Thread t = new Thread(() -> {
            // Use a throwaway token swap so the logout call carries the old token without re-authenticating the app.
            try { client.authWithToken("/logout?scope=local", token); } catch (ApiException ignored) { /* already signed out locally */ }
        }, "iats-logout");
        t.setDaemon(true);
        t.start();
    }

    private boolean refreshQuietly() {
        try { refresh(); return true; } catch (ApiException e) { return false; }
    }

    private void clearLocal() { client.setAccessToken(null); refreshToken = null; }

    private UserSession session(JsonNode n, String fallbackEmail) {
        String token = n.path("access_token").asText("");
        client.setAccessToken(token);
        refreshToken = n.path("refresh_token").asText("");
        String email = n.path("user").path("email").asText(fallbackEmail);
        return new UserSession(token, refreshToken, n.path("user").path("id").asText(""), email,
            email.equalsIgnoreCase(config.adminEmail()), false);
    }

    private static void requireEmail(String email) throws ApiException {
        if (email == null || email.isBlank()) throw new ApiException(Kind.VALIDATION, "Enter your email address.");
        if (!EMAIL.matcher(email.trim()).matches()) throw new ApiException(Kind.VALIDATION, "That doesn't look like a valid email address.");
    }

    public record UserSession(String accessToken, String refreshToken, String userId, String email, boolean admin, boolean emailConfirmationRequired) {
        /** Tokens are credentials: never let them leak into logs via the default record toString. */
        @Override public String toString() {
            return "UserSession[userId=" + userId + ", email=" + email + ", admin=" + admin
                + ", emailConfirmationRequired=" + emailConfirmationRequired + ", tokens=<redacted>]";
        }
    }
}

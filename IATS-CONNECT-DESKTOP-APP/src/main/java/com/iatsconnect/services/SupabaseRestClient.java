package com.iatsconnect.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iatsconnect.config.AppConfig;
import com.iatsconnect.services.ApiException.Kind;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.function.Function;
import javax.net.ssl.SSLException;

/**
 * Thin HTTP client for Supabase Auth and PostgREST.
 *
 * <p>Every failure is reported as an {@link ApiException}; nothing else escapes. Behaviour:
 * <ul>
 *   <li>connect + per-request timeouts, so a dead network can never hang a worker forever;</li>
 *   <li>automatic retry with exponential back-off for idempotent requests (GET/DELETE) on
 *       connectivity problems, timeouts, HTTP 429 and 5xx;</li>
 *   <li>one transparent token refresh on HTTP 401 (see {@link TokenRefresher});</li>
 *   <li>a session-expired callback when the token can no longer be renewed;</li>
 *   <li>interrupt-safe: an interrupted call restores the interrupt flag and reports CANCELLED.</li>
 * </ul>
 * Methods block, so call them from a background thread, never from the JavaFX application thread.
 */
public final class SupabaseRestClient {

    /** Renews the access token. Returns true when a new token was installed via {@link #setAccessToken}. */
    @FunctionalInterface
    public interface TokenRefresher { boolean refresh(); }

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 400;

    private final AppConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long backoffMs;
    private final Duration requestTimeout;
    private volatile String accessToken;
    private volatile TokenRefresher tokenRefresher;
    private volatile Runnable sessionExpiredListener;

    public SupabaseRestClient(AppConfig config) { this(config, CONNECT_TIMEOUT, REQUEST_TIMEOUT, BASE_BACKOFF_MS); }

    /** Package-private so tests can use short timeouts and no back-off. */
    SupabaseRestClient(AppConfig config, Duration connectTimeout, Duration requestTimeout, long backoffMs) {
        this.config = config;
        this.requestTimeout = requestTimeout;
        this.backoffMs = backoffMs;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public void setAccessToken(String token) { accessToken = token; }
    public String getAccessToken() { return accessToken; }
    public void setTokenRefresher(TokenRefresher refresher) { this.tokenRefresher = refresher; }
    public void setSessionExpiredListener(Runnable listener) { this.sessionExpiredListener = listener; }

    // ---------------------------------------------------------------- public API

    /** POST to {@code /auth/v1<endpoint>} using only the publishable key (sign-in, sign-up, recover, refresh). */
    public JsonNode auth(String endpoint, String json) throws ApiException {
        requireConfigured();
        return execute(token -> baseRequest("/auth/v1" + endpoint, config.supabaseAnonKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json)).build(),
            false, false, true);
    }

    /** POST to {@code /auth/v1<endpoint>} authenticated with an explicit user token (used for server-side logout). */
    public JsonNode authWithToken(String endpoint, String userToken) throws ApiException {
        requireConfigured();
        return execute(ignored -> baseRequest("/auth/v1" + endpoint, bearer(userToken))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            false, false, true);
    }

    /** Calls PostgREST at {@code /rest/v1/<path>}. GET and DELETE are retried automatically on transient failures. */
    public JsonNode rest(String method, String path, String json) throws ApiException {
        requireConfigured();
        String m = method == null ? "" : method.toUpperCase();
        if (!m.equals("GET") && !m.equals("POST") && !m.equals("PATCH") && !m.equals("DELETE")) {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
        boolean idempotent = m.equals("GET") || m.equals("DELETE");
        return execute(token -> {
            HttpRequest.Builder b = baseRequest("/rest/v1/" + path, bearer(token)).header("Accept", "application/json");
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json);
            if (json != null) b.header("Content-Type", "application/json");
            switch (m) {
                case "GET" -> b.GET();
                case "DELETE" -> b.DELETE();
                case "POST" -> { b.header("Prefer", "resolution=merge-duplicates,return=minimal"); b.POST(body); }
                default -> b.method("PATCH", body);
            }
            return b.build();
        }, idempotent, true, false);
    }

    public String json(Object value) throws ApiException {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new ApiException(Kind.PARSE, "Could not prepare the request data.", e); }
    }

    public JsonNode parse(String body) throws ApiException {
        try { return body == null || body.isBlank() ? mapper.createArrayNode() : mapper.readTree(body); }
        catch (JsonProcessingException e) { throw new ApiException(Kind.PARSE, "The server sent a response the app could not read.", e); }
    }

    public ObjectMapper mapper() { return mapper; }

    // ---------------------------------------------------------------- internals

    private HttpRequest.Builder baseRequest(String path, String bearerValue) {
        URI uri;
        try { uri = URI.create(config.supabaseUrl() + path); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("Invalid supabase.url in config.properties", e); }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout)
            .header("apikey", config.supabaseAnonKey());
        // Modern sb_publishable_* keys are opaque API keys, not JWTs. Never send them
        // as Bearer tokens; Authorization is reserved for a real user access token.
        if (bearerValue != null && !bearerValue.isBlank()
                && !bearerValue.equals(config.supabaseAnonKey())) {
            builder.header("Authorization", "Bearer " + bearerValue);
        }
        return builder;
    }

    private String bearer(String token) { return token == null || token.isBlank() ? "" : token; }

    private JsonNode execute(Function<String, HttpRequest> requestFactory, boolean retryable, boolean allowRefresh, boolean authEndpoint)
            throws ApiException {
        boolean refreshed = false;
        int attempt = 0;
        while (true) {
            attempt++;
            ApiException failure;
            try {
                HttpRequest req = requestFactory.apply(accessToken);
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                if (status >= 200 && status < 300) return parse(resp.body());
                failure = ApiException.fromResponse(status, resp.body(), mapper, authEndpoint);
            } catch (ApiException e) {
                throw e; // parse errors on a successful response are not retryable
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(Kind.CANCELLED, "The request was cancelled.", e);
            } catch (HttpTimeoutException e) {
                failure = new ApiException(Kind.TIMEOUT, "The server took too long to respond. Check your connection and try again.", e);
            } catch (ConnectException | UnknownHostException | UnresolvedAddressException e) {
                failure = new ApiException(Kind.OFFLINE, "Can't reach IATS Connect. Check your internet connection.", e);
            } catch (SSLException e) {
                throw new ApiException(Kind.OFFLINE, "A secure connection could not be established. Check your network, proxy or system clock.", e);
            } catch (IOException e) {
                // Resets, premature EOF, broken pipe, etc. All indicate a flaky network.
                failure = new ApiException(Kind.OFFLINE, "The connection was interrupted. Check your internet connection.", e);
            }

            if (failure.kind() == Kind.UNAUTHORIZED && allowRefresh && accessToken != null && !accessToken.isBlank()) {
                if (!refreshed && refreshToken()) { refreshed = true; attempt--; continue; }
                notifySessionExpired();
                throw failure;
            }
            if (retryable && failure.isTransient() && attempt < MAX_ATTEMPTS) {
                try { Thread.sleep(backoffMs * (1L << (attempt - 1))); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(Kind.CANCELLED, "The request was cancelled.", e);
                }
                continue;
            }
            throw failure;
        }
    }

    private boolean refreshToken() {
        TokenRefresher r = tokenRefresher;
        if (r == null) return false;
        try { return r.refresh(); } catch (RuntimeException e) { return false; }
    }

    private void notifySessionExpired() {
        Runnable l = sessionExpiredListener;
        if (l != null) { try { l.run(); } catch (RuntimeException ignored) { /* listener bugs must not mask the real error */ } }
    }

    private void requireConfigured() throws ApiException {
        if (!config.isSupabaseConfigured()) {
            throw new ApiException(Kind.NOT_CONFIGURED,
                "Supabase is not configured yet. Copy config.properties.example to config.properties and add your publishable/anon key.");
        }
    }
}

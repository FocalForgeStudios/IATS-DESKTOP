package com.iatsconnect.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Single checked exception type thrown by the service layer. Every instance carries a
 * {@link Kind} (so callers can react programmatically) and a {@link #userMessage()} that is
 * safe and understandable to show directly to end users.
 */
public final class ApiException extends IOException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        NOT_CONFIGURED("Setup required"),
        OFFLINE("No connection"),
        TIMEOUT("Connection timed out"),
        UNAUTHORIZED("Sign-in required"),
        FORBIDDEN("Permission denied"),
        NOT_FOUND("Not found"),
        CONFLICT("Conflict"),
        RATE_LIMITED("Too many requests"),
        VALIDATION("Invalid request"),
        SERVER("Server problem"),
        PARSE("Unexpected response"),
        CANCELLED("Cancelled"),
        UNKNOWN("Something went wrong");

        private final String title;
        Kind(String title) { this.title = title; }
        public String title() { return title; }
    }

    private final Kind kind;
    private final int status;
    private final String code;
    private final String userMessage;

    public ApiException(Kind kind, String userMessage) { this(kind, userMessage, -1, null, null); }

    public ApiException(Kind kind, String userMessage, Throwable cause) { this(kind, userMessage, -1, null, cause); }

    public ApiException(Kind kind, String userMessage, int status, String code, Throwable cause) {
        super(userMessage + (status > 0 ? " (HTTP " + status + ")" : ""), cause);
        this.kind = kind;
        this.userMessage = userMessage;
        this.status = status;
        this.code = code;
    }

    public Kind kind() { return kind; }
    /** HTTP status, or -1 when the request never produced a response. */
    public int status() { return status; }
    /** Machine-readable code from Supabase (e.g. {@code invalid_credentials}), or null. */
    public String code() { return code; }
    /** Friendly, non-technical message suitable for dialogs and status labels. */
    public String userMessage() { return userMessage; }
    public String title() { return kind.title(); }

    /** True when stale cached data is an acceptable substitute for a live response. */
    public boolean isRecoverableWithCache() {
        return kind == Kind.OFFLINE || kind == Kind.TIMEOUT || kind == Kind.SERVER || kind == Kind.RATE_LIMITED;
    }

    /** Errors that are worth retrying automatically for idempotent requests. */
    public boolean isTransient() { return isRecoverableWithCache(); }

    /** Walks a throwable and returns an ApiException for it, wrapping anything unexpected. */
    public static ApiException wrap(Throwable t) {
        if (t instanceof ApiException a) return a;
        if (t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException) {
            if (t.getCause() != null) return wrap(t.getCause());
        }
        return new ApiException(Kind.UNKNOWN, "An unexpected error occurred. Please try again.", t);
    }

    /** Builds an exception from a non-2xx HTTP response (Supabase Auth or PostgREST body formats). */
    static ApiException fromResponse(int status, String body, ObjectMapper mapper, boolean authEndpoint) {
        String code = null;
        String serverMessage = null;
        try {
            JsonNode n = mapper.readTree(body == null ? "" : body);
            if (n != null && n.isObject()) {
                code = firstText(n, "error_code", "code");
                serverMessage = firstText(n, "msg", "message", "error_description", "error");
            }
        } catch (Exception ignored) { /* body was not JSON; fall back to status mapping */ }

        Kind kind;
        String message;
        if (status == 401) {
            kind = Kind.UNAUTHORIZED;
            message = "Your session has expired. Please sign in again.";
        } else if (status == 403) {
            kind = Kind.FORBIDDEN;
            message = "You don't have permission to do that.";
        } else if (status == 404) {
            kind = Kind.NOT_FOUND;
            message = "The requested item could not be found.";
        } else if (status == 409) {
            kind = Kind.CONFLICT;
            message = "That item already exists or was changed by someone else.";
        } else if (status == 429) {
            kind = Kind.RATE_LIMITED;
            message = "Too many requests. Please wait a moment and try again.";
        } else if (status >= 500) {
            kind = Kind.SERVER;
            message = "The IATS Connect server is having trouble. Please try again shortly.";
        } else {
            kind = Kind.VALIDATION;
            message = serverMessage != null && !serverMessage.isBlank()
                ? serverMessage : "The request could not be completed.";
        }

        // Specific, friendlier wording for the common Supabase Auth / PostgREST error codes.
        if (code != null) {
            switch (code) {
                case "invalid_credentials" -> { kind = Kind.UNAUTHORIZED; message = "Incorrect email or password."; }
                case "email_not_confirmed" -> { kind = Kind.UNAUTHORIZED; message = "Please confirm your email address first, then sign in."; }
                case "user_already_exists", "email_exists" -> { kind = Kind.CONFLICT; message = "An account with that email already exists. Try signing in instead."; }
                case "weak_password" -> { kind = Kind.VALIDATION; message = "That password is too weak. Use at least 8 characters with a mix of letters and numbers."; }
                case "signup_disabled" -> { kind = Kind.FORBIDDEN; message = "New registrations are currently disabled."; }
                case "over_request_rate_limit", "over_email_send_rate_limit" -> { kind = Kind.RATE_LIMITED; message = "Too many attempts. Please wait a few minutes and try again."; }
                case "validation_failed" -> { kind = Kind.VALIDATION; message = "Please check the email address and password you entered."; }
                case "42501" -> { kind = Kind.FORBIDDEN; message = "You don't have permission to do that."; }
                case "23505" -> { kind = Kind.CONFLICT; message = "That item already exists."; }
                case "PGRST301", "PGRST303" -> { kind = Kind.UNAUTHORIZED; message = "Your session has expired. Please sign in again."; }
                default -> { /* keep status-based message */ }
            }
        }
        // A bad password on the auth endpoint comes back as 400 without a code on older GoTrue versions.
        if (authEndpoint && status == 400 && serverMessage != null
                && serverMessage.toLowerCase().contains("invalid login credentials")) {
            kind = Kind.UNAUTHORIZED;
            message = "Incorrect email or password.";
        }
        return new ApiException(kind, message, status, code, null);
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isValueNode() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }
}

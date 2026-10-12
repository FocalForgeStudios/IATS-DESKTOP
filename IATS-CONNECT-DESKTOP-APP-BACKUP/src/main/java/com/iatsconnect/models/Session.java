package com.iatsconnect.models;

/** Authentication session details. Never persisted to disk or the local cache. */
public record Session(String accessToken, String refreshToken, String userId, String email) {
    /** Tokens are credentials: keep them out of logs and error reports. */
    @Override public String toString() { return "Session[userId=" + userId + ", email=" + email + ", tokens=<redacted>]"; }
}

package com.iatsconnect.services;

import static com.iatsconnect.services.ServiceTestSupport.reply;
import static org.junit.jupiter.api.Assertions.*;

import com.iatsconnect.services.ApiException.Kind;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupabaseRestClientTest {

    @Test void getRetriesTransientServerErrorsThenSucceeds() throws Exception {
        try (var s = new ServiceTestSupport()) {
            AtomicInteger calls = new AtomicInteger();
            s.on("/rest/v1/groups", ex -> {
                if (calls.incrementAndGet() < 3) reply(ex, 503, "{\"message\":\"unavailable\"}"); else reply(ex, 200, "[{\"id\":\"1\"}]");
            });
            var json = s.client().rest("GET", "groups", null);
            assertEquals(3, calls.get());
            assertEquals("1", json.get(0).get("id").asText());
        }
    }

    @Test void getGivesUpAfterMaxAttemptsWithServerKind() throws Exception {
        try (var s = new ServiceTestSupport()) {
            AtomicInteger calls = new AtomicInteger();
            s.on("/rest/v1/groups", ex -> { calls.incrementAndGet(); reply(ex, 500, "oops"); });
            var e = assertThrows(ApiException.class, () -> s.client().rest("GET", "groups", null));
            assertEquals(Kind.SERVER, e.kind());
            assertEquals(SupabaseRestClient.MAX_ATTEMPTS, calls.get());
            assertTrue(e.isRecoverableWithCache());
        }
    }

    @Test void postIsNeverRetried() throws Exception {
        try (var s = new ServiceTestSupport()) {
            AtomicInteger calls = new AtomicInteger();
            s.on("/rest/v1/hub_posts", ex -> { calls.incrementAndGet(); reply(ex, 503, ""); });
            assertThrows(ApiException.class, () -> s.client().rest("POST", "hub_posts", "{}"));
            assertEquals(1, calls.get());
        }
    }

    @Test void slowServerTimesOut() throws Exception {
        try (var s = new ServiceTestSupport()) {
            s.on("/rest/v1/news", ex -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) { } reply(ex, 200, "[]"); });
            long start = System.currentTimeMillis();
            var e = assertThrows(ApiException.class, () -> s.client().rest("POST", "news", "{}"));
            assertEquals(Kind.TIMEOUT, e.kind());
            assertTrue(System.currentTimeMillis() - start < 1400, "must not wait for the slow response");
        }
    }

    @Test void unreachableServerIsReportedAsOffline() throws Exception {
        int deadPort;
        try (var s = new ServiceTestSupport()) { deadPort = s.port(); }
        var client = new SupabaseRestClient(ServiceTestSupport.configFor("http://127.0.0.1:" + deadPort),
            java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), 5);
        var e = assertThrows(ApiException.class, () -> client.rest("GET", "groups", null));
        assertEquals(Kind.OFFLINE, e.kind());
        assertTrue(e.isRecoverableWithCache());
    }

    @Test void forbiddenIsNotRecoverableAndHasFriendlyMessage() throws Exception {
        try (var s = new ServiceTestSupport()) {
            s.on("/rest/v1/catchups", ex -> reply(ex, 403, "{\"code\":\"42501\",\"message\":\"new row violates row-level security policy\"}"));
            var e = assertThrows(ApiException.class, () -> s.client().rest("POST", "catchups", "{}"));
            assertEquals(Kind.FORBIDDEN, e.kind());
            assertFalse(e.isRecoverableWithCache());
            assertFalse(e.userMessage().contains("row-level"), "raw database text must not reach users");
        }
    }

    @Test void malformedSuccessBodyIsAParseErrorNotARuntimeException() throws Exception {
        try (var s = new ServiceTestSupport()) {
            s.on("/rest/v1/groups", ex -> reply(ex, 200, "<html>captive portal</html>"));
            assertEquals(Kind.PARSE, assertThrows(ApiException.class, () -> s.client().rest("GET", "groups", null)).kind());
        }
    }

    @Test void unconfiguredClientFailsFastWithSetupMessage() {
        var client = new SupabaseRestClient(new com.iatsconnect.config.AppConfig(new java.util.Properties()));
        assertEquals(Kind.NOT_CONFIGURED, assertThrows(ApiException.class, () -> client.rest("GET", "groups", null)).kind());
    }

    @Test void interruptedCallReportsCancelledAndKeepsInterruptFlag() throws Exception {
        try (var s = new ServiceTestSupport()) {
            s.on("/rest/v1/groups", ex -> { try { Thread.sleep(300); } catch (InterruptedException ignored) { } reply(ex, 200, "[]"); });
            AtomicReference<ApiException> err = new AtomicReference<>();
            AtomicReference<Boolean> flag = new AtomicReference<>();
            Thread t = new Thread(() -> {
                try { s.client().rest("GET", "groups", null); } catch (ApiException e) { err.set(e); }
                flag.set(Thread.currentThread().isInterrupted());
            });
            t.start();
            Thread.sleep(80);
            t.interrupt();
            t.join(3000);
            assertNotNull(err.get());
            assertEquals(Kind.CANCELLED, err.get().kind());
            assertTrue(flag.get());
        }
    }
}

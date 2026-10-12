package com.iatsconnect.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iatsconnect.config.AppConfig;
import com.iatsconnect.models.StudentProfile;
import com.iatsconnect.services.ApiException;
import com.iatsconnect.services.ApiException.Kind;
import com.iatsconnect.services.SupabaseRestClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test void roundTripAndMissingEntry(@TempDir Path dir) throws Exception {
        var cache = new LocalCache(dir, true);
        assertTrue(cache.get("public", "news").isEmpty());
        cache.put("public", "news", om.readTree("[{\"title\":\"Hi\"}]"));
        var hit = cache.get("public", "news").orElseThrow();
        assertEquals("Hi", hit.data().get(0).get("title").asText());
        assertNotNull(hit.fetchedAt());
    }

    @Test void corruptFileIsTreatedAsMissAndRemoved(@TempDir Path dir) throws Exception {
        var cache = new LocalCache(dir, true);
        cache.put("public", "news", om.readTree("[]"));
        Path f = dir.resolve("public/news.json");
        Files.writeString(f, "{ not json");
        assertTrue(cache.get("public", "news").isEmpty());
        assertFalse(Files.exists(f));
    }

    @Test void unsafeKeysAndScopesAreRejected(@TempDir Path dir) throws Exception {
        var cache = new LocalCache(dir, true);
        cache.put("public", "../../evil", om.readTree("[]"));
        cache.put("../escape", "k", om.readTree("[]"));
        assertTrue(cache.get("public", "../../evil").isEmpty());
        try (var files = Files.walk(dir)) { assertEquals(0L, files.filter(Files::isRegularFile).count()); }
    }

    @Test void clearScopeRemovesOnlyThatUser(@TempDir Path dir) throws Exception {
        var cache = new LocalCache(dir, true);
        cache.put("user-1", "profile", om.readTree("[]"));
        cache.put("user-2", "profile", om.readTree("[]"));
        cache.put("public", "news", om.readTree("[]"));
        cache.clearScope("user-1");
        assertTrue(cache.get("user-1", "profile").isEmpty());
        assertTrue(cache.get("user-2", "profile").isPresent());
        cache.clearScope("public"); // public content is never wiped by sign-out
        assertTrue(cache.get("public", "news").isPresent());
    }

    @Test void disabledCacheStoresNothing(@TempDir Path dir) throws Exception {
        var cache = new LocalCache(dir.resolve("x"), false);
        cache.put("public", "news", om.readTree("[]"));
        assertTrue(cache.get("public", "news").isEmpty());
        assertFalse(Files.exists(dir.resolve("x")));
    }

    @Test void repositoryServesCachedGroupsWhenOfflineButNeverMasksPermissionErrors(@TempDir Path dir) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger mode = new AtomicInteger(200);
        server.createContext("/rest/v1/groups", ex -> {
            int m = mode.get();
            byte[] body = (m == 200 ? "[{\"id\":\"g1\",\"name\":\"ICT\",\"parent_group_id\":null,\"created_by\":\"u1\",\"visibility\":\"public\",\"unexpected\":1}]" : "{\"message\":\"x\"}").getBytes();
            ex.sendResponseHeaders(m, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            Properties p = new Properties();
            p.setProperty("supabase.url", "http://127.0.0.1:" + server.getAddress().getPort());
            p.setProperty("supabase.anon.key", "k");
            var client = new SupabaseRestClient(new AppConfig(p));
            var repo = new DataRepository(client, new LocalCache(dir, true));

            var live = repo.groups(null);
            assertFalse(live.fromCache());
            assertEquals("ICT", live.data().get(0).name());
            assertEquals("u1", live.data().get(0).createdBy()); // snake_case mapping

            mode.set(403);
            assertEquals(Kind.FORBIDDEN, assertThrows(ApiException.class, () -> repo.groups(null)).kind());

            server.stop(0); // now truly unreachable
            var offline = repo.groups(null);
            assertTrue(offline.fromCache());
            assertEquals("ICT", offline.data().get(0).name());
        } finally { server.stop(0); }
    }

    @Test void savingProfileUpdatesOfflineCopyAndSignOutWipesIt(@TempDir Path dir) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/student_profiles", ex -> { ex.sendResponseHeaders(201, -1); ex.close(); });
        server.start();
        try {
            Properties p = new Properties();
            p.setProperty("supabase.url", "http://127.0.0.1:" + server.getAddress().getPort());
            p.setProperty("supabase.anon.key", "k");
            var cache = new LocalCache(dir, true);
            var repo = new DataRepository(new SupabaseRestClient(new AppConfig(p)), cache);
            repo.saveProfile(new StudentProfile("u1", "Ann", "ICT", "1.2", "evenings", null, "🎓"));
            var cached = cache.get("u1", "profile").orElseThrow();
            assertEquals("evenings", cached.data().get(0).get("study_schedule").asText()); // stored in API (snake_case) shape
            assertEquals("Ann", cached.data().get(0).get("name").asText());
            repo.forgetUser("u1");
            assertTrue(cache.get("u1", "profile").isEmpty());
        } finally { server.stop(0); }
    }
}

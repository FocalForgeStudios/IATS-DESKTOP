package com.iatsconnect.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Small disk-backed JSON cache used to show previously loaded data when the network is unavailable.
 *
 * <p>Layout: {@code <dir>/<scope>/<key>.json}. The <em>scope</em> separates data by audience:
 * {@code "public"} for content anyone can read, or a user id for data that belongs to one account.
 * User scopes are deleted on sign-out so private data does not linger on shared computers.
 * Writes are atomic and every failure is swallowed: the cache is an optimisation and must never
 * break the app. Authentication tokens must never be stored here.
 */
public final class LocalCache {
    public static final String PUBLIC_SCOPE = "public";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    /** A cached payload and when it was fetched from the server. */
    public record Entry(JsonNode data, Instant fetchedAt) {}

    private final Path dir;
    private final boolean enabled;
    private final ObjectMapper mapper = new ObjectMapper();

    public LocalCache(Path dir, boolean enabled) { this.dir = dir; this.enabled = enabled; }

    public static LocalCache disabled() { return new LocalCache(Path.of("."), false); }

    public void put(String scope, String key, JsonNode data) {
        if (!enabled || data == null) return;
        try {
            Path file = file(scope, key);
            Files.createDirectories(file.getParent());
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("fetchedAt", Instant.now().toString());
            wrapper.set("data", data);
            Path tmp = Files.createTempFile(file.getParent(), key, ".tmp");
            try {
                restrict(tmp);
                Files.write(tmp, mapper.writeValueAsBytes(wrapper));
                try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
        } catch (IOException | RuntimeException ignored) { /* caching is best effort */ }
    }

    public Optional<Entry> get(String scope, String key) {
        if (!enabled) return Optional.empty();
        Path file;
        try { file = file(scope, key); } catch (IllegalArgumentException e) { return Optional.empty(); }
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            JsonNode wrapper = mapper.readTree(Files.readAllBytes(file));
            JsonNode data = wrapper.get("data");
            if (data == null) throw new IOException("missing data");
            return Optional.of(new Entry(data, Instant.parse(wrapper.path("fetchedAt").asText())));
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) { /* corrupt entry; try to discard it */ }
            return Optional.empty();
        }
    }

    /** Deletes everything cached for one scope (e.g. a user's data on sign-out). */
    public void clearScope(String scope) {
        if (!enabled || scope == null || PUBLIC_SCOPE.equals(scope)) return;
        Path folder;
        try { folder = scopeDir(scope); } catch (IllegalArgumentException e) { return; }
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        } catch (IOException ignored) { /* best effort */ }
    }

    private Path scopeDir(String scope) {
        if (scope == null || !SAFE.matcher(scope).matches()) throw new IllegalArgumentException("bad cache scope");
        return dir.resolve(scope);
    }

    private Path file(String scope, String key) {
        if (key == null || !SAFE.matcher(key).matches()) throw new IllegalArgumentException("bad cache key");
        return scopeDir(scope).resolve(key + ".json");
    }

    private static void restrict(Path p) {
        try { Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException | IOException ignored) { /* non-POSIX file system */ }
    }
}

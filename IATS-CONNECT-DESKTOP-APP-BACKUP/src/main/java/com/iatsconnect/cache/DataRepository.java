package com.iatsconnect.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.iatsconnect.models.Group;
import com.iatsconnect.models.StudentProfile;
import com.iatsconnect.services.ApiException;
import com.iatsconnect.services.SupabaseRestClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Network-first data access with an offline fallback.
 *
 * <p>Reads try Supabase first and write the response through to {@link LocalCache}. If the network is
 * unreachable (offline, timeout, 5xx, rate-limited) the last cached copy is returned instead, flagged
 * with {@link Result#fromCache()} so the UI can say "showing saved data from ...". Authorization and
 * validation errors are never masked by the cache. Calls block: use them off the FX thread.
 */
public final class DataRepository {

    /** Data plus provenance. {@code fetchedAt} is when the data was last downloaded from the server. */
    public record Result<T>(T data, boolean fromCache, Instant fetchedAt) {}

    public static final String GROUPS_PATH = "groups?select=id,name,description,course,cohort,parent_group_id,created_by,visibility&order=name.asc";

    private final SupabaseRestClient api;
    private final LocalCache cache;
    private final ObjectMapper mapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DataRepository(SupabaseRestClient api, LocalCache cache) { this.api = api; this.cache = cache; }

    // ----------------------------------------------------------------- generic feeds (news, events)

    /** Public content such as news and events. {@code cacheKey} must be [A-Za-z0-9_-]. */
    public Result<JsonNode> feed(String cacheKey, String restPath) throws ApiException {
        return load(LocalCache.PUBLIC_SCOPE, cacheKey, restPath);
    }

    // ----------------------------------------------------------------- Group

    /** Groups visible to the current viewer. Cached per user because private groups depend on membership. */
    public Result<List<Group>> groups(String userId) throws ApiException {
        Result<JsonNode> raw = load(scope(userId), "groups", GROUPS_PATH);
        return new Result<>(convert(raw.data(), new TypeReference<List<Group>>() {}), raw.fromCache(), raw.fetchedAt());
    }

    // ----------------------------------------------------------------- StudentProfile

    public Result<Optional<StudentProfile>> profile(String userId) throws ApiException {
        Result<JsonNode> raw = load(scope(userId), "profile",
            "student_profiles?select=id,name,course,cohort,study_schedule,avatar_url,emoji&id=eq." + userId + "&limit=1");
        List<StudentProfile> rows = convert(raw.data(), new TypeReference<List<StudentProfile>>() {});
        return new Result<>(rows.stream().findFirst(), raw.fromCache(), raw.fetchedAt());
    }

    /** Upserts the profile on the server, then refreshes the cached copy. Requires a connection. */
    public void saveProfile(StudentProfile p) throws ApiException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.id());
        row.put("name", p.name());
        row.put("course", p.course());
        row.put("cohort", p.cohort());
        row.put("study_schedule", p.studySchedule());
        row.put("emoji", p.emoji());
        row.put("avatar_url", p.avatarUrl());
        api.rest("POST", "student_profiles?on_conflict=id", api.json(row));
        cache.put(scope(p.id()), "profile", mapper.createArrayNode().add(mapper.valueToTree(p)));
    }

    // ----------------------------------------------------------------- lifecycle

    /** Removes cached private data for a user (call on sign-out). */
    public void forgetUser(String userId) { if (userId != null) cache.clearScope(userId); }

    // ----------------------------------------------------------------- internals

    private Result<JsonNode> load(String scope, String key, String restPath) throws ApiException {
        try {
            JsonNode fresh = api.rest("GET", restPath, null);
            cache.put(scope, key, fresh);
            return new Result<>(fresh, false, Instant.now());
        } catch (ApiException e) {
            if (e.isRecoverableWithCache()) {
                Optional<LocalCache.Entry> hit = cache.get(scope, key);
                if (hit.isPresent()) return new Result<>(hit.get().data(), true, hit.get().fetchedAt());
            }
            throw e;
        }
    }

    private <T> List<T> convert(JsonNode node, TypeReference<List<T>> type) throws ApiException {
        if (node == null || !node.isArray()) return new ArrayList<>();
        try { return mapper.readValue(mapper.treeAsTokens(node), type); }
        catch (java.io.IOException e) { throw new ApiException(ApiException.Kind.PARSE, "The data from the server was not in the expected format.", e); }
    }

    private static String scope(String userId) { return userId == null || userId.isBlank() ? LocalCache.PUBLIC_SCOPE : userId; }
}

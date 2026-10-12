package com.iatsconnect.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppConfig {
    private final Properties values = new Properties();
    public AppConfig() {
        Path local = Path.of("config.properties");
        if (Files.exists(local)) {
            try (InputStream in = Files.newInputStream(local)) { values.load(in); }
            catch (IOException e) { throw new IllegalStateException("Could not read config.properties", e); }
        }
        values.putIfAbsent("supabase.url", "https://ojobzjornniyjrqszglk.supabase.co");
        values.putIfAbsent("supabase.anon.key", "");
        values.putIfAbsent("admin.email", "focalforges@gmail.com");
    }
    /** For tests and embedding: use explicit values instead of reading config.properties. */
    public AppConfig(Properties explicit) {
        values.putAll(explicit);
        values.putIfAbsent("supabase.url", "https://ojobzjornniyjrqszglk.supabase.co");
        values.putIfAbsent("supabase.anon.key", "");
        values.putIfAbsent("admin.email", "focalforges@gmail.com");
    }
    public String supabaseUrl() { return values.getProperty("supabase.url").replaceAll("/$", ""); }
    public String supabaseAnonKey() { return values.getProperty("supabase.anon.key", "").trim(); }
    public String adminEmail() { return values.getProperty("admin.email", "focalforges@gmail.com").trim().toLowerCase(); }
    /** Folder for the offline data cache. Override with {@code cache.dir}; defaults to ~/.iats-connect/cache. */
    public Path cacheDir() {
        String configured = values.getProperty("cache.dir", "").trim();
        return configured.isEmpty() ? Path.of(System.getProperty("user.home"), ".iats-connect", "cache") : Path.of(configured);
    }
    /** Set {@code cache.enabled=false} to disable offline caching entirely. */
    public boolean cacheEnabled() { return !"false".equalsIgnoreCase(values.getProperty("cache.enabled", "true").trim()); }
    public boolean isSupabaseConfigured() { return !supabaseAnonKey().isBlank() && !supabaseAnonKey().contains("PASTE_YOUR"); }
}

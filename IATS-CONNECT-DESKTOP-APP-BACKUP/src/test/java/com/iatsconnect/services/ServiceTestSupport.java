package com.iatsconnect.services;

import com.iatsconnect.config.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/** Local fake Supabase for tests: register handlers per path prefix, no real network involved. */
final class ServiceTestSupport implements AutoCloseable {
    final HttpServer server;

    ServiceTestSupport() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    int port() { return server.getAddress().getPort(); }

    AppConfig config() { return configFor("http://127.0.0.1:" + port()); }

    static AppConfig configFor(String url) {
        Properties p = new Properties();
        p.setProperty("supabase.url", url);
        p.setProperty("supabase.anon.key", "test-anon-key");
        return new AppConfig(p);
    }

    SupabaseRestClient client() { return new SupabaseRestClient(config(), Duration.ofSeconds(2), Duration.ofMillis(400), 5); }

    void on(String path, HttpHandler h) { server.createContext(path, h); }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
        ex.close();
    }

    @Override public void close() { server.stop(0); }
}

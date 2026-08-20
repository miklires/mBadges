package io.github.miklires.mbadges.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import io.github.miklires.mbadges.api.model.BadgeResult;
import io.github.miklires.mbadges.service.BadgeService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RestApiService implements AutoCloseable {
    private final MBadgesPlugin plugin;
    private final BadgeService service;
    private final ConcurrentHashMap<String, Window> limits = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    public RestApiService(MBadgesPlugin plugin, BadgeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("rest-api.enabled", false)) return;
        String token = plugin.getConfig().getString("rest-api.token", "");
        if (token.length() < 24) {
            plugin.getLogger().warning("REST API disabled because rest-api.token must contain at least 24 characters");
            return;
        }
        String bind = plugin.getConfig().getString("rest-api.bind", "127.0.0.1");
        int port = Math.max(1, Math.min(65_535, plugin.getConfig().getInt("rest-api.port", 8766)));
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 32);
            server.createContext("/v1/players", this::handle);
            executor = Executors.newFixedThreadPool(4, Thread.ofPlatform().name("mBadges-rest-", 0).factory());
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("REST API listening on " + bind + ":" + port);
        } catch (IOException exception) {
            plugin.getLogger().warning("REST API could not start: " + exception.getMessage());
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!allowedAddress(exchange)) {
                respond(exchange, 403, "{\"error\":\"address_not_allowed\"}");
                return;
            }
            if (!withinRateLimit(exchange)) {
                respond(exchange, 429, "{\"error\":\"rate_limit\"}");
                return;
            }
            if (!authorized(exchange)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                respond(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            String relative = exchange.getRequestURI().getPath().substring("/v1/players".length());
            String[] parts = relative.split("/");
            if (parts.length < 3 || !parts[2].equals("badges")) {
                respond(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                respond(exchange, 400, "{\"error\":\"invalid_uuid\"}");
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (parts.length == 3 && method.equals("GET")) {
                getBadges(exchange, playerId);
                return;
            }
            if (parts.length == 4 && (method.equals("POST") || method.equals("DELETE"))) {
                String badgeId = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);
                mutate(exchange, method, playerId, badgeId);
                return;
            }
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("REST request failed: " + exception.getMessage());
            if (exchange.getResponseCode() < 0) respond(exchange, 500, "{\"error\":\"internal_error\"}");
        }
    }

    private void getBadges(HttpExchange exchange, UUID playerId) throws IOException {
        var owned = service.getOwnedBadges(playerId).join();
        var equipped = service.getEquippedBadges(playerId).join();
        StringBuilder json = new StringBuilder("{\"player\":\"").append(playerId).append("\",\"owned\":[");
        for (int index = 0; index < owned.size(); index++) {
            if (index > 0) json.append(',');
            var badge = owned.get(index);
            json.append("{\"id\":\"").append(escape(badge.badgeId())).append("\",\"obtained_at\":\"")
                    .append(badge.obtainedAt()).append("\",\"expires_at\":")
                    .append(badge.expiresAt() == null ? "null" : "\"" + badge.expiresAt() + "\"")
                    .append(",\"source\":\"").append(escape(badge.source())).append("\"}");
        }
        json.append("],\"equipped\":[");
        for (int index = 0; index < equipped.size(); index++) {
            if (index > 0) json.append(',');
            var badge = equipped.get(index);
            json.append("{\"id\":\"").append(escape(badge.badgeId())).append("\",\"slot\":")
                    .append(badge.slot()).append('}');
        }
        respond(exchange, 200, json.append("]}").toString());
    }

    private void mutate(HttpExchange exchange, String method, UUID playerId, String badgeId) throws IOException {
        var future = method.equals("POST")
                ? service.giveBadge(playerId, badgeId, expiry(exchange), "rest", "", BadgeOperationReason.REST)
                : service.removeBadge(playerId, badgeId, "rest", BadgeOperationReason.REST);
        BadgeResult result = future.join();
        int status = result.successful() ? 200 : switch (result.status()) {
            case UNKNOWN_BADGE -> 404;
            case ALREADY_OWNED, NOT_OWNED -> 409;
            default -> 400;
        };
        respond(exchange, status, "{\"status\":\"" + result.status().name().toLowerCase(Locale.ROOT)
                + "\",\"badge\":\"" + escape(result.badgeId()) + "\"}");
    }

    private Instant expiry(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] entry = part.split("=", 2);
            if (entry.length != 2 || !entry[0].equals("duration_seconds")) continue;
            try {
                long seconds = Long.parseLong(entry[1]);
                if (seconds > 0 && seconds <= Duration.ofDays(3650).toSeconds()) return Instant.now().plusSeconds(seconds);
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private boolean authorized(HttpExchange exchange) {
        String expected = "Bearer " + plugin.getConfig().getString("rest-api.token", "");
        String actual = exchange.getRequestHeaders().getFirst("Authorization");
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean allowedAddress(HttpExchange exchange) {
        List<String> configured = plugin.getConfig().getStringList("rest-api.allowed-addresses");
        if (configured.isEmpty()) return false;
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        return configured.stream().anyMatch(value -> value.equalsIgnoreCase(address));
    }

    private boolean withinRateLimit(HttpExchange exchange) {
        int maximum = Math.max(1, plugin.getConfig().getInt("rest-api.requests-per-minute", 60));
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        long minute = System.currentTimeMillis() / 60_000L;
        Window window = limits.compute(address, (key, old) -> old == null || old.minute() != minute
                ? new Window(minute, 1) : new Window(minute, old.requests() + 1));
        if (limits.size() > 10_000) limits.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
        return window.requests() <= maximum;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    @Override
    public void close() {
        if (server != null) server.stop(1);
        if (executor != null) executor.shutdown();
    }

    private record Window(long minute, int requests) { }
}

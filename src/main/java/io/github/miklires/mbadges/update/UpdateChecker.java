package io.github.miklires.mbadges.update;

import io.github.miklires.mbadges.MBadgesPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final Pattern VERSION = Pattern.compile("\\\"version_number\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final MBadgesPlugin plugin;

    public UpdateChecker(MBadgesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;
        String projectId = plugin.getConfig().getString("updates.modrinth-project-id", "").trim();
        if (projectId.isEmpty()) return;
        int hours = Math.max(1, Math.min(720, plugin.getConfig().getInt("updates.interval-hours", 24)));
        plugin.scheduler().async(this::check);
        plugin.scheduler().asyncTimer(this::check, hours, hours, TimeUnit.HOURS);
    }

    private void check() {
        String projectId = plugin.getConfig().getString("updates.modrinth-project-id", "").trim();
        int timeout = Math.max(500, Math.min(60_000, plugin.getConfig().getInt("updates.timeout-millis", 8_000)));
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build()) {
            String encoded = URLEncoder.encode(projectId, StandardCharsets.UTF_8);
            URI uri = URI.create("https://api.modrinth.com/v2/project/" + encoded + "/version");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeout))
                    .header("User-Agent", "miklires/mBadges/" + plugin.getPluginMeta().getVersion())
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("Modrinth update check returned HTTP " + response.statusCode());
                return;
            }
            SemVer current = SemVer.parse(plugin.getPluginMeta().getVersion());
            latestStable(response.body()).filter(latest -> latest.compareTo(current) > 0)
                    .ifPresent(latest -> plugin.getLogger().info("mBadges " + latest.major() + "." + latest.minor()
                            + "." + latest.patch() + " is available on Modrinth"));
        } catch (Exception exception) {
            plugin.getLogger().warning("Modrinth update check failed: " + exception.getMessage());
        }
    }

    private java.util.Optional<SemVer> latestStable(String json) {
        Matcher matcher = VERSION.matcher(json);
        List<SemVer> versions = new ArrayList<>();
        while (matcher.find()) {
            try {
                SemVer version = SemVer.parse(matcher.group(1));
                if (version.stable()) versions.add(version);
            } catch (IllegalArgumentException ignored) { }
        }
        return versions.stream().max(SemVer::compareTo);
    }
}

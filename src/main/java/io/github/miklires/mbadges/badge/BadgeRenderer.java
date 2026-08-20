package io.github.miklires.mbadges.badge;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.cache.BadgeCache;

import java.time.Instant;
import java.util.UUID;

public final class BadgeRenderer {
    private final MBadgesPlugin plugin;
    private final BadgeRegistry registry;
    private final BadgeCache cache;

    public BadgeRenderer(MBadgesPlugin plugin, BadgeRegistry registry, BadgeCache cache) {
        this.plugin = plugin;
        this.registry = registry;
        this.cache = cache;
    }

    public String render(UUID playerId) {
        String separator = plugin.getConfig().getString("display.separator", " ");
        StringBuilder result = new StringBuilder();
        var snapshot = cache.getOrEmpty(playerId);
        var player = plugin.getServer().getPlayer(playerId);
        for (var equipped : snapshot.equipped()) {
            var badge = registry.get(equipped.badgeId()).orElse(null);
            var owned = snapshot.owned(equipped.badgeId()).orElse(null);
            if (badge == null || owned == null || !badge.enabled() || owned.expired(Instant.now())) continue;
            if (!badge.permission().isBlank() && (player == null || !player.hasPermission(badge.permission()))) continue;
            if (!result.isEmpty()) result.append(separator);
            result.append(badge.glyphText());
        }
        if (!result.isEmpty() && plugin.getConfig().getBoolean("display.trailing-space", true)) result.append(' ');
        return result.toString();
    }
}

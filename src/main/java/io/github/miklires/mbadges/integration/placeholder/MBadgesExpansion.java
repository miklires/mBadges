package io.github.miklires.mbadges.integration.placeholder;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.service.BadgeService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MBadgesExpansion extends PlaceholderExpansion {
    private final MBadgesPlugin plugin;
    private final BadgeService service;

    public MBadgesExpansion(MBadgesPlugin plugin, BadgeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override public @NotNull String getIdentifier() { return "mbadge"; }
    @Override public @NotNull String getAuthor() { return "miklires"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "";
        var playerId = player.getUniqueId();
        var snapshot = service.cached(playerId);
        return switch (params.toLowerCase()) {
            case "equipped" -> service.render(playerId);
            case "count" -> Integer.toString(snapshot.equipped().size());
            case "owned_count" -> Integer.toString(snapshot.owned().size());
            case "slots" -> Integer.toString(service.getSlotLimit(playerId));
            default -> slot(params, snapshot);
        };
    }

    private String slot(String params, io.github.miklires.mbadges.cache.PlayerBadgeSnapshot snapshot) {
        if (!params.startsWith("slot_")) return null;
        try {
            int requested = Integer.parseInt(params.substring("slot_".length()));
            return snapshot.equipped().stream().filter(value -> value.slot() == requested).findFirst()
                    .flatMap(value -> service.getBadge(value.badgeId()))
                    .map(badge -> badge.enabled() ? badge.glyphText() : "")
                    .orElse("");
        } catch (NumberFormatException ignored) {
            return "";
        }
    }
}

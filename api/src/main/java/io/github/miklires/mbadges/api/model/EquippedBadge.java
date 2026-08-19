package io.github.miklires.mbadges.api.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EquippedBadge(UUID playerId, String badgeId, int slot, Instant equippedAt) {
    public EquippedBadge {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(badgeId, "badgeId");
        Objects.requireNonNull(equippedAt, "equippedAt");
        if (slot < 1) throw new IllegalArgumentException("slot must be positive");
    }
}

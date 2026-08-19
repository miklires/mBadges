package io.github.miklires.mbadges.api.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OwnedBadge(
        UUID playerId,
        String badgeId,
        Instant obtainedAt,
        Instant expiresAt,
        String source,
        String metadata
) {
    public OwnedBadge {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(badgeId, "badgeId");
        Objects.requireNonNull(obtainedAt, "obtainedAt");
        source = source == null || source.isBlank() ? "unknown" : source;
        metadata = metadata == null ? "" : metadata;
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}

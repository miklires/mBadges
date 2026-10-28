package io.github.miklires.mbadges.database;

import io.github.miklires.mbadges.api.model.OwnedBadge;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

final class DatabaseRowMapper {
    private DatabaseRowMapper() {
    }

    static OwnedBadge ownedBadge(ResultSet result, UUID playerId) throws SQLException {
        String badgeId = result.getString("badge_id");
        long obtainedAt = result.getLong("obtained_at");
        long expiresAt = result.getLong("expires_at");
        boolean permanent = result.wasNull();
        String source = result.getString("source");
        String metadata = result.getString("metadata");
        return new OwnedBadge(playerId, badgeId, Instant.ofEpochMilli(obtainedAt),
                permanent ? null : Instant.ofEpochMilli(expiresAt), source, metadata);
    }
}

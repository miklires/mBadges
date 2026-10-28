package io.github.miklires.mbadges.database;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DatabaseRowMapperTest {
    @Test
    void nullExpiryRemainsPermanentAfterOtherColumnsAreRead() throws Exception {
        UUID playerId = UUID.randomUUID();
        var row = row(null);

        var badge = DatabaseRowMapper.ownedBadge(row, playerId);

        assertNull(badge.expiresAt());
        assertFalse(badge.expired(Instant.now()));
        assertEquals("developer", badge.badgeId());
    }

    @Test
    void nonNullExpiryIsPreserved() throws Exception {
        long expiry = 2_000_000_000_000L;
        var badge = DatabaseRowMapper.ownedBadge(row(expiry), UUID.randomUUID());
        assertEquals(Instant.ofEpochMilli(expiry), badge.expiresAt());
    }

    private static javax.sql.rowset.CachedRowSet row(Long expiry) throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(5);
        column(metadata, 1, "badge_id", Types.VARCHAR);
        column(metadata, 2, "obtained_at", Types.BIGINT);
        column(metadata, 3, "expires_at", Types.BIGINT);
        column(metadata, 4, "source", Types.VARCHAR);
        column(metadata, 5, "metadata", Types.VARCHAR);

        var rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateString("badge_id", "developer");
        rows.updateLong("obtained_at", 1_700_000_000_000L);
        if (expiry == null) rows.updateNull("expires_at");
        else rows.updateLong("expires_at", expiry);
        rows.updateString("source", "console");
        rows.updateString("metadata", "");
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();
        return rows;
    }

    private static void column(RowSetMetaDataImpl metadata, int index, String name, int type) throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, type);
        metadata.setNullable(index, RowSetMetaDataImpl.columnNullable);
    }
}

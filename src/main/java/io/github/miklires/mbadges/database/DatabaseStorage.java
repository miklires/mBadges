package io.github.miklires.mbadges.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.api.model.EquippedBadge;
import io.github.miklires.mbadges.api.model.OwnedBadge;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class DatabaseStorage implements AutoCloseable {
    private static final int SCHEMA_VERSION = 1;

    private final MBadgesPlugin plugin;
    private final String serverId = UUID.randomUUID().toString();
    private HikariDataSource dataSource;

    public DatabaseStorage(MBadgesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        YamlConfiguration config = plugin.configFiles().file("database.yml");
        String type = config.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("mBadges-storage");
        hikari.setJdbcUrl(jdbcUrl(config, type));
        if (!type.equals("sqlite")) {
            hikari.setUsername(config.getString("username", "mbadges"));
            hikari.setPassword(config.getString("password", ""));
        }
        hikari.setMaximumPoolSize(bounded(config.getInt("pool-size", 10), 1, 64));
        hikari.setConnectionTimeout(bounded(config.getLong("connection-timeout-millis", 10_000), 250, 120_000));
        hikari.setMaxLifetime(bounded(config.getLong("max-lifetime-millis", 1_800_000), 30_000, 7_200_000));
        if (type.equals("sqlite")) hikari.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hikari);
        migrate();
        plugin.getLogger().info("connected to " + type + " storage");
    }

    public void syncDefinitions(Collection<Badge> badges) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM badges");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO badges (id, name, category, glyph, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                long now = System.currentTimeMillis();
                for (Badge badge : badges) {
                    statement.setString(1, badge.id());
                    statement.setString(2, badge.name());
                    statement.setString(3, badge.category());
                    statement.setInt(4, badge.glyph());
                    statement.setLong(5, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw failure("sync badge definitions", exception);
        }
    }

    public void rememberPlayer(UUID playerId, String name) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM players WHERE player_uuid = ?")) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO players (player_uuid, last_name, updated_at) VALUES (?, ?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setString(2, name == null ? "" : name);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw failure("remember player", exception);
        }
    }

    public UUID findPlayer(String name) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid FROM players WHERE LOWER(last_name) = LOWER(?)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UUID.fromString(result.getString(1)) : null;
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw failure("find player", exception);
        }
    }

    public StoredPlayerData load(UUID playerId) {
        List<OwnedBadge> owned = new ArrayList<>();
        List<EquippedBadge> equipped = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement ownedQuery = connection.prepareStatement(
                     "SELECT badge_id, obtained_at, expires_at, source, metadata FROM player_badges WHERE player_uuid = ?");
             PreparedStatement equippedQuery = connection.prepareStatement(
                     "SELECT badge_id, slot, equipped_at FROM equipped_badges WHERE player_uuid = ? ORDER BY slot")) {
            ownedQuery.setString(1, playerId.toString());
            try (ResultSet result = ownedQuery.executeQuery()) {
                while (result.next()) {
                    long expiry = result.getLong("expires_at");
                    owned.add(new OwnedBadge(playerId, result.getString("badge_id"),
                            Instant.ofEpochMilli(result.getLong("obtained_at")),
                            result.wasNull() ? null : Instant.ofEpochMilli(expiry),
                            result.getString("source"), result.getString("metadata")));
                }
            }
            equippedQuery.setString(1, playerId.toString());
            try (ResultSet result = equippedQuery.executeQuery()) {
                while (result.next()) {
                    equipped.add(new EquippedBadge(playerId, result.getString("badge_id"), result.getInt("slot"),
                            Instant.ofEpochMilli(result.getLong("equipped_at"))));
                }
            }
            return new StoredPlayerData(owned, equipped);
        } catch (SQLException exception) {
            throw failure("load player badges", exception);
        }
    }

    public boolean give(OwnedBadge badge) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO player_badges (player_uuid, badge_id, obtained_at, expires_at, source, metadata, updated_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, badge.playerId().toString());
            statement.setString(2, badge.badgeId());
            statement.setLong(3, badge.obtainedAt().toEpochMilli());
            if (badge.expiresAt() == null) statement.setNull(4, java.sql.Types.BIGINT);
            else statement.setLong(4, badge.expiresAt().toEpochMilli());
            statement.setString(5, badge.source());
            statement.setString(6, badge.metadata());
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
            recordChange(connection, badge.playerId());
            return true;
        } catch (SQLException exception) {
            if (constraint(exception)) return false;
            throw failure("give badge", exception);
        }
    }

    public boolean remove(UUID playerId, String badgeId) {
        return transaction(connection -> {
            try (PreparedStatement equipped = connection.prepareStatement(
                    "DELETE FROM equipped_badges WHERE player_uuid = ? AND badge_id = ?")) {
                equipped.setString(1, playerId.toString());
                equipped.setString(2, badgeId);
                equipped.executeUpdate();
            }
            int changed;
            try (PreparedStatement owned = connection.prepareStatement(
                    "DELETE FROM player_badges WHERE player_uuid = ? AND badge_id = ?")) {
                owned.setString(1, playerId.toString());
                owned.setString(2, badgeId);
                changed = owned.executeUpdate();
            }
            if (changed > 0) recordChange(connection, playerId);
            return changed > 0;
        }, "remove badge");
    }

    public boolean equip(EquippedBadge badge) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO equipped_badges (player_uuid, badge_id, slot, equipped_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, badge.playerId().toString());
            statement.setString(2, badge.badgeId());
            statement.setInt(3, badge.slot());
            statement.setLong(4, badge.equippedAt().toEpochMilli());
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            recordChange(connection, badge.playerId());
            return true;
        } catch (SQLException exception) {
            if (constraint(exception)) return false;
            throw failure("equip badge", exception);
        }
    }

    public boolean unequip(UUID playerId, String badgeId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM equipped_badges WHERE player_uuid = ? AND badge_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, badgeId);
            int changed = statement.executeUpdate();
            if (changed > 0) recordChange(connection, playerId);
            return changed > 0;
        } catch (SQLException exception) {
            throw failure("unequip badge", exception);
        }
    }

    public boolean clear(UUID playerId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM equipped_badges WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            int changed = statement.executeUpdate();
            if (changed > 0) recordChange(connection, playerId);
            return changed > 0;
        } catch (SQLException exception) {
            throw failure("clear equipped badges", exception);
        }
    }

    public boolean swapSlots(UUID playerId, int first, int second) {
        if (first == second) return true;
        return transaction(connection -> {
            updateSlot(connection, playerId, first, 0);
            updateSlot(connection, playerId, second, first);
            updateSlot(connection, playerId, 0, second);
            recordChange(connection, playerId);
            return true;
        }, "reorder badges");
    }

    public Set<UUID> changesSince(long timestamp) {
        Set<UUID> result = new HashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT DISTINCT player_uuid FROM badge_changes WHERE changed_at > ? AND server_id <> ?")) {
            statement.setLong(1, timestamp);
            statement.setString(2, serverId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    try { result.add(UUID.fromString(rows.getString(1))); }
                    catch (IllegalArgumentException ignored) { }
                }
            }
            return result;
        } catch (SQLException exception) {
            throw failure("poll badge changes", exception);
        }
    }

    private void migrate() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at BIGINT NOT NULL)");
            int version = 0;
            try (ResultSet result = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
                if (result.next()) version = result.getInt(1);
            }
            if (version > SCHEMA_VERSION) throw new IllegalStateException("database schema is newer than this plugin");
            if (version < 1) {
                connection.setAutoCommit(false);
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (player_uuid VARCHAR(36) PRIMARY KEY, last_name VARCHAR(64) NOT NULL, updated_at BIGINT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS badges (id VARCHAR(64) PRIMARY KEY, name VARCHAR(256) NOT NULL, category VARCHAR(64) NOT NULL, glyph INTEGER NOT NULL, updated_at BIGINT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_badges (player_uuid VARCHAR(36) NOT NULL, badge_id VARCHAR(64) NOT NULL, obtained_at BIGINT NOT NULL, expires_at BIGINT NULL, source VARCHAR(128) NOT NULL, metadata TEXT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, badge_id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS equipped_badges (player_uuid VARCHAR(36) NOT NULL, badge_id VARCHAR(64) NOT NULL, slot INTEGER NOT NULL, equipped_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, slot), UNIQUE (player_uuid, badge_id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS badge_changes (change_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, server_id VARCHAR(36) NOT NULL, changed_at BIGINT NOT NULL)");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, 1);
                    insert.setLong(2, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                connection.commit();
                plugin.getLogger().info("database migrated to schema version 1");
            }
        } catch (SQLException exception) {
            throw failure("migrate database", exception);
        }
    }

    private void updateSlot(Connection connection, UUID playerId, int from, int to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE equipped_badges SET slot = ?, updated_at = ? WHERE player_uuid = ? AND slot = ?")) {
            statement.setInt(1, to);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, playerId.toString());
            statement.setInt(4, from);
            statement.executeUpdate();
        }
    }

    private void recordChange(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO badge_changes (change_id, player_uuid, server_id, changed_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, serverId);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private String jdbcUrl(YamlConfiguration config, String type) {
        String custom = config.getString("jdbc-url", "").trim();
        if (!custom.isEmpty()) return custom;
        String host = config.getString("host", "localhost");
        String name = config.getString("name", "mbadges");
        int defaultPort = type.equals("postgresql") ? 5432 : 3306;
        int port = bounded(config.getInt("port", defaultPort), 1, 65_535);
        return switch (type) {
            case "sqlite" -> {
                String fileName = config.getString("file", "mbadges").replaceAll("[^A-Za-z0-9_-]", "");
                if (fileName.isBlank()) fileName = "mbadges";
                yield "jdbc:sqlite:" + new File(plugin.getDataFolder(), fileName + ".db").getAbsolutePath();
            }
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + name;
            default -> throw new IllegalArgumentException("unsupported database type: " + type);
        };
    }

    private Connection connection() throws SQLException {
        if (dataSource == null) throw new SQLException("storage is not started");
        return dataSource.getConnection();
    }

    private boolean constraint(SQLException exception) {
        return exception.getSQLState() != null && exception.getSQLState().startsWith("23");
    }

    private RuntimeException failure(String operation, Exception exception) {
        return new IllegalStateException("could not " + operation + ": " + exception.getMessage(), exception);
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private <T> T transaction(SqlWork<T> work, String operation) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                T value = work.run(connection);
                connection.commit();
                return value;
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sql) throw sql;
                throw exception;
            }
        } catch (Exception exception) {
            throw failure(operation, exception);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) dataSource.close();
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }
}

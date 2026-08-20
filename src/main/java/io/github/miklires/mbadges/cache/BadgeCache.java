package io.github.miklires.mbadges.cache;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BadgeCache {
    private final ConcurrentHashMap<UUID, PlayerBadgeSnapshot> players = new ConcurrentHashMap<>();

    public Optional<PlayerBadgeSnapshot> get(UUID playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    public PlayerBadgeSnapshot getOrEmpty(UUID playerId) {
        return players.getOrDefault(playerId, PlayerBadgeSnapshot.empty());
    }

    public void put(UUID playerId, PlayerBadgeSnapshot snapshot) {
        players.put(playerId, snapshot);
    }

    public void remove(UUID playerId) {
        players.remove(playerId);
    }

    public Set<UUID> players() {
        return Set.copyOf(players.keySet());
    }
}

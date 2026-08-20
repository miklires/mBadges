package io.github.miklires.mbadges.cache;

import io.github.miklires.mbadges.api.model.EquippedBadge;
import io.github.miklires.mbadges.api.model.OwnedBadge;
import io.github.miklires.mbadges.database.StoredPlayerData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PlayerBadgeSnapshot(Map<String, OwnedBadge> owned, List<EquippedBadge> equipped) {
    public PlayerBadgeSnapshot {
        owned = Map.copyOf(owned);
        equipped = equipped.stream().sorted(Comparator.comparingInt(EquippedBadge::slot)).toList();
    }

    public static PlayerBadgeSnapshot from(StoredPlayerData data) {
        Map<String, OwnedBadge> owned = new LinkedHashMap<>();
        data.owned().forEach(badge -> owned.put(badge.badgeId(), badge));
        return new PlayerBadgeSnapshot(owned, data.equipped());
    }

    public static PlayerBadgeSnapshot empty() {
        return new PlayerBadgeSnapshot(Map.of(), List.of());
    }

    public Optional<OwnedBadge> owned(String badgeId) {
        return Optional.ofNullable(owned.get(badgeId));
    }

    public Optional<EquippedBadge> equipped(String badgeId) {
        return equipped.stream().filter(value -> value.badgeId().equals(badgeId)).findFirst();
    }
}

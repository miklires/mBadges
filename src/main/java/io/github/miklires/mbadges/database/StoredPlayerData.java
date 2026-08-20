package io.github.miklires.mbadges.database;

import io.github.miklires.mbadges.api.model.EquippedBadge;
import io.github.miklires.mbadges.api.model.OwnedBadge;

import java.util.List;

public record StoredPlayerData(List<OwnedBadge> owned, List<EquippedBadge> equipped) {
    public StoredPlayerData {
        owned = List.copyOf(owned);
        equipped = List.copyOf(equipped);
    }
}

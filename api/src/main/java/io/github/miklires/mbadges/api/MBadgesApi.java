package io.github.miklires.mbadges.api;

import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import io.github.miklires.mbadges.api.model.BadgeResult;
import io.github.miklires.mbadges.api.model.EquippedBadge;
import io.github.miklires.mbadges.api.model.OwnedBadge;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MBadgesApi {
    Optional<Badge> getBadge(String id);

    Collection<Badge> getBadges();

    CompletableFuture<List<OwnedBadge>> getOwnedBadges(UUID playerId);

    CompletableFuture<List<EquippedBadge>> getEquippedBadges(UUID playerId);

    CompletableFuture<Boolean> hasBadge(UUID playerId, String badgeId);

    CompletableFuture<BadgeResult> giveBadge(UUID playerId, String badgeId, Instant expiresAt,
                                             String source, String metadata, BadgeOperationReason reason);

    CompletableFuture<BadgeResult> removeBadge(UUID playerId, String badgeId, String source,
                                               BadgeOperationReason reason);

    CompletableFuture<BadgeResult> equipBadge(UUID playerId, String badgeId, String source,
                                              BadgeOperationReason reason);

    CompletableFuture<BadgeResult> unequipBadge(UUID playerId, String badgeId, String source,
                                                BadgeOperationReason reason);

    CompletableFuture<BadgeResult> clearEquippedBadges(UUID playerId, String source,
                                                       BadgeOperationReason reason);

    int getSlotLimit(UUID playerId);

    String render(UUID playerId);
}

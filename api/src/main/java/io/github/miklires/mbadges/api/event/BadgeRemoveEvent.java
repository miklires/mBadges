package io.github.miklires.mbadges.api.event;

import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class BadgeRemoveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final Badge badge;
    private final BadgeOperationReason reason;
    private final String source;

    public BadgeRemoveEvent(boolean async, UUID playerId, Badge badge, BadgeOperationReason reason, String source) {
        super(async);
        this.playerId = Objects.requireNonNull(playerId);
        this.badge = Objects.requireNonNull(badge);
        this.reason = Objects.requireNonNull(reason);
        this.source = source == null ? "unknown" : source;
    }

    public UUID getPlayerId() { return playerId; }
    public Badge getBadge() { return badge; }
    public BadgeOperationReason getReason() { return reason; }
    public String getSource() { return source; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

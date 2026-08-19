package io.github.miklires.mbadges.api.event;

import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class BadgeUnequipEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final Badge badge;
    private final BadgeOperationReason reason;
    private final String source;
    private boolean cancelled;

    public BadgeUnequipEvent(boolean async, UUID playerId, Badge badge,
                             BadgeOperationReason reason, String source) {
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
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

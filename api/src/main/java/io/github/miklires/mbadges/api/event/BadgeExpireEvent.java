package io.github.miklires.mbadges.api.event;

import io.github.miklires.mbadges.api.model.Badge;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class BadgeExpireEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final Badge badge;

    public BadgeExpireEvent(boolean async, UUID playerId, Badge badge) {
        super(async);
        this.playerId = Objects.requireNonNull(playerId);
        this.badge = Objects.requireNonNull(badge);
    }

    public UUID getPlayerId() { return playerId; }
    public Badge getBadge() { return badge; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

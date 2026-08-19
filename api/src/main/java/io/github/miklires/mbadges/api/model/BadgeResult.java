package io.github.miklires.mbadges.api.model;

public record BadgeResult(Status status, String badgeId, int slot) {
    public enum Status {
        SUCCESS,
        UNKNOWN_BADGE,
        NOT_OWNED,
        ALREADY_OWNED,
        ALREADY_EQUIPPED,
        NOT_EQUIPPED,
        DISABLED,
        EXPIRED,
        MISSING_PERMISSION,
        SLOT_LIMIT,
        CANCELLED,
        STORAGE_ERROR
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public static BadgeResult of(Status status, String badgeId) {
        return new BadgeResult(status, badgeId, 0);
    }
}

package io.github.miklires.mbadges.resourcepack;

import java.util.Locale;

public enum ResourcePackMode {
    INTERNAL,
    ORAXEN,
    ITEMSADDER,
    EXTERNAL;

    public static ResourcePackMode parse(String value) {
        try {
            return valueOf(value == null ? "INTERNAL" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return INTERNAL;
        }
    }
}

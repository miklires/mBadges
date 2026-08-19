package io.github.miklires.mbadges.api.model;

import java.util.Objects;

public record Badge(
        String id,
        String name,
        String description,
        String texture,
        int glyph,
        String permission,
        String category,
        int priority,
        boolean hidden,
        boolean enabled,
        boolean temporary
) {
    public Badge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(category, "category");
    }

    public String glyphText() {
        return glyph <= 0 ? "" : new String(Character.toChars(glyph));
    }
}

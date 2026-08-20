package io.github.miklires.mbadges.badge;

import java.util.Collection;

public final class GlyphAllocator {
    private GlyphAllocator() {
    }

    public static int next(int start, int end, Collection<Integer> used) {
        if (start < 0 || end < start || end > Character.MAX_CODE_POINT) {
            throw new IllegalArgumentException("invalid glyph range");
        }
        for (int codePoint = start; codePoint <= end; codePoint++) {
            if (!Character.isValidCodePoint(codePoint) || Character.isSurrogate((char) codePoint)) continue;
            if (!used.contains(codePoint)) return codePoint;
        }
        throw new IllegalStateException("configured glyph range is exhausted");
    }

    public static int parseHex(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().replaceFirst("(?i)^U\\+", "");
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

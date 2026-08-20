package io.github.miklires.mbadges.badge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlyphAllocatorTest {
    @Test
    void findsFirstUnusedGlyph() {
        assertEquals(0xE002, GlyphAllocator.next(0xE000, 0xE005, List.of(0xE000, 0xE001)));
    }

    @Test
    void failsWhenRangeIsExhausted() {
        assertThrows(IllegalStateException.class,
                () -> GlyphAllocator.next(0xE000, 0xE001, List.of(0xE000, 0xE001)));
    }

    @Test
    void parsesConfiguredHexValues() {
        assertEquals(0xF100, GlyphAllocator.parseHex("U+F100", 0));
        assertEquals(7, GlyphAllocator.parseHex("invalid", 7));
    }
}

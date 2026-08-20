package io.github.miklires.mbadges.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
    @Test
    void parsesCombinedDuration() {
        assertEquals(Duration.ofSeconds(93_900), DurationParser.parse("1d2h5m"));
    }

    @Test
    void rejectsGapsAndUnknownUnits() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1h 5m"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("5x"));
    }
}

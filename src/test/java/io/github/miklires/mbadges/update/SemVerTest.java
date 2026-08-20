package io.github.miklires.mbadges.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SemVerTest {
    @Test
    void stableReleaseSortsAfterPrerelease() {
        assertTrue(SemVer.parse("1.0.0").compareTo(SemVer.parse("1.0.0-beta.1")) > 0);
    }

    @Test
    void comparesNumericCore() {
        assertTrue(SemVer.parse("1.10.0").compareTo(SemVer.parse("1.9.9")) > 0);
    }
}

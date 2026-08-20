package io.github.miklires.mbadges.resourcepack;

import java.nio.file.Path;

public record ResourcePackBuild(Path file, String sha1, String fingerprint, int badgeCount, boolean rebuilt) {
}

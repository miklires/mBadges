package io.github.miklires.mbadges.update;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemVer(int major, int minor, int patch, String prerelease) implements Comparable<SemVer> {
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    public SemVer {
        Objects.requireNonNull(prerelease);
    }

    public static SemVer parse(String value) {
        Matcher matcher = PATTERN.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("invalid semantic version: " + value);
        return new SemVer(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), matcher.group(4) == null ? "" : matcher.group(4));
    }

    public boolean stable() {
        return prerelease.isEmpty();
    }

    @Override
    public int compareTo(SemVer other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        if (result != 0) return result;
        if (prerelease.isEmpty() && !other.prerelease.isEmpty()) return 1;
        if (!prerelease.isEmpty() && other.prerelease.isEmpty()) return -1;
        return prerelease.compareTo(other.prerelease);
    }
}

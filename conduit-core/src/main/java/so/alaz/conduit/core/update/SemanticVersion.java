package so.alaz.conduit.core.update;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal semantic version parser/comparator used by the update checker.
 * Pre-release suffixes are compared as lower precedence than the corresponding
 * release, matching SemVer ordering for the common cases Conduit ships.
 *
 * @param major   major version
 * @param minor   minor version
 * @param patch   patch version
 * @param preRelease pre-release identifier, or empty for a release build
 */
@ApiStatus.Internal
public record SemanticVersion(int major, int minor, int patch, @NotNull String preRelease)
        implements Comparable<SemanticVersion> {

    private static final Pattern PATTERN =
            Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$");

    /**
     * Parse a version string such as {@code 1.0.0} or {@code 1.2.0-RC1}.
     *
     * @param raw the version text (an optional leading {@code v} is tolerated)
     * @return the parsed version
     * @throws IllegalArgumentException if the string is not a valid version
     */
    public static @NotNull SemanticVersion parse(@NotNull String raw) {
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a semantic version: " + raw);
        }
        String pre = matcher.group(4);
        return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                pre == null ? "" : pre);
    }

    /**
     * @param other the version to compare against
     * @return {@code true} if this version is strictly newer than {@code other}
     */
    public boolean isNewerThan(@NotNull SemanticVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(@NotNull SemanticVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) return byMajor;
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) return byMinor;
        int byPatch = Integer.compare(patch, other.patch);
        if (byPatch != 0) return byPatch;
        // A release (empty pre-release) outranks any pre-release of the same core.
        if (preRelease.isEmpty() && !other.preRelease.isEmpty()) return 1;
        if (!preRelease.isEmpty() && other.preRelease.isEmpty()) return -1;
        return preRelease.compareTo(other.preRelease);
    }
}

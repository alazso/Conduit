package so.alaz.conduit.core.update;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticVersionTest {

    @Test
    void parses_plain_and_v_prefixed_and_prerelease() {
        assertThat(SemanticVersion.parse("1.2.3")).isEqualTo(new SemanticVersion(1, 2, 3, ""));
        assertThat(SemanticVersion.parse("v2.0.0")).isEqualTo(new SemanticVersion(2, 0, 0, ""));
        assertThat(SemanticVersion.parse("1.0.0-RC1")).isEqualTo(new SemanticVersion(1, 0, 0, "RC1"));
    }

    @Test
    void rejects_garbage() {
        assertThatThrownBy(() -> SemanticVersion.parse("not-a-version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orders_by_core_then_prerelease() {
        assertThat(SemanticVersion.parse("1.1.0").isNewerThan(SemanticVersion.parse("1.0.9"))).isTrue();
        assertThat(SemanticVersion.parse("2.0.0").isNewerThan(SemanticVersion.parse("1.9.9"))).isTrue();
        assertThat(SemanticVersion.parse("1.0.1").isNewerThan(SemanticVersion.parse("1.0.0"))).isTrue();
    }

    @Test
    void release_outranks_prerelease_of_same_core() {
        assertThat(SemanticVersion.parse("1.0.0").isNewerThan(SemanticVersion.parse("1.0.0-RC1"))).isTrue();
        assertThat(SemanticVersion.parse("1.0.0-RC1").isNewerThan(SemanticVersion.parse("1.0.0"))).isFalse();
    }

    @Test
    void equal_versions_are_not_newer() {
        assertThat(SemanticVersion.parse("1.0.0").isNewerThan(SemanticVersion.parse("1.0.0"))).isFalse();
    }
}

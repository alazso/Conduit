package so.alaz.conduit.api.result;

import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.exception.OperationException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTypesTest {

    @Test
    void success_exposes_value_and_optional() {
        Result<String> r = new Result.Success<>("ok");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOrThrow()).isEqualTo("ok");
        assertThat(r.getOrDefault("fallback")).isEqualTo("ok");
        assertThat(r.toOptional()).contains("ok");
    }

    @Test
    void failure_throws_on_getOrThrow_and_falls_back() {
        Result<String> r = new Result.Failure<>("boom", null);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getOrDefault("fallback")).isEqualTo("fallback");
        assertThat(r.toOptional()).isEmpty();
        assertThatThrownBy(r::getOrThrow).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void operationResult_success_orThrow_is_noop() {
        OperationResult r = OperationResult.success();
        assertThat(r.isSuccess()).isTrue();
        r.orThrow();
    }

    @Test
    void operationResult_failure_orThrow_throws_operationException() {
        OperationResult r = OperationResult.failure("denied");
        assertThat(r.isSuccess()).isFalse();
        assertThatThrownBy(r::orThrow)
                .isInstanceOf(OperationException.class)
                .hasMessageContaining("denied");
    }
}

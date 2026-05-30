package so.alaz.conduit.api.result;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * A typed success/failure result for operations that return a value on success.
 *
 * @param <T> the success value type
 */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface Result<T> permits Result.Success, Result.Failure {

    /**
     * A successful result carrying a value.
     *
     * @param value the success value
     * @param <T>   the value type
     */
    record Success<T>(T value) implements Result<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getOrThrow() {
            return value;
        }

        @Override
        public T getOrDefault(T fallback) {
            return value;
        }

        @Override
        public @NotNull Optional<T> toOptional() {
            return Optional.ofNullable(value);
        }
    }

    /**
     * A failed result carrying a reason and optional cause.
     *
     * @param reason a human-readable failure reason
     * @param cause  the underlying cause, or {@code null}
     * @param <T>    the value type that would have been produced on success
     */
    record Failure<T>(@NotNull String reason, @Nullable Throwable cause) implements Result<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T getOrThrow() {
            throw new NoSuchElementException("Result failed: " + reason);
        }

        @Override
        public T getOrDefault(T fallback) {
            return fallback;
        }

        @Override
        public @NotNull Optional<T> toOptional() {
            return Optional.empty();
        }
    }

    /**
     * @return {@code true} if this is a {@link Success}
     */
    boolean isSuccess();

    /**
     * @return the success value
     * @throws NoSuchElementException if this is a {@link Failure}
     */
    T getOrThrow();

    /**
     * @param fallback the value to return on failure
     * @return the success value, or {@code fallback} on failure
     */
    T getOrDefault(T fallback);

    /**
     * @return an {@link Optional} of the success value, empty on failure
     */
    @NotNull Optional<T> toOptional();
}

package so.alaz.conduit.api.result;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.Transaction;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The outcome of an economy mutation. All monetary values are {@link BigDecimal}.
 *
 * <p>This is a sealed type; consumers exhaustively pattern-match its cases.
 * Invalid <em>input</em> (null/negative/zero magnitude, scale overflow) is a
 * programming error thrown synchronously at the call boundary and is never
 * represented here — these cases describe runtime money outcomes only.
 */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface EconomyResult permits
        EconomyResult.Success,
        EconomyResult.InsufficientFunds,
        EconomyResult.AccountNotFound,
        EconomyResult.CurrencyNotSupported,
        EconomyResult.Rejected,
        EconomyResult.ProviderError {

    /**
     * The operation committed successfully.
     *
     * @param account     the account affected
     * @param currency    the currency involved
     * @param newBalance  the resulting balance
     * @param transaction the recorded transaction, or {@code null} when the
     *                    provider does not record this operation as a
     *                    {@link Transaction} (e.g. {@code set()})
     */
    record Success(
            @NotNull UUID account,
            @NotNull Currency currency,
            @NotNull BigDecimal newBalance,
            @Nullable Transaction transaction
    ) implements EconomyResult {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * The account lacked sufficient funds for a withdrawal/transfer.
     *
     * @param balance   the current balance
     * @param requested the amount requested
     * @param currency  the currency involved
     */
    record InsufficientFunds(
            @NotNull BigDecimal balance,
            @NotNull BigDecimal requested,
            @NotNull Currency currency
    ) implements EconomyResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * No account exists for the given UUID.
     *
     * @param uuid the account UUID that was not found
     */
    record AccountNotFound(@NotNull UUID uuid) implements EconomyResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * The account does not support the requested currency.
     *
     * @param currency the unsupported currency
     */
    record CurrencyNotSupported(@NotNull Currency currency) implements EconomyResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * The operation was vetoed by a pre-authorisation interceptor (policy), not
     * by the provider. Distinct from {@link ProviderError}: the backend was
     * never asked to execute, no funds moved, and no
     * {@link so.alaz.conduit.api.event.EconomyTransactionEvent} fired. Consumers
     * and metrics should treat this as an intentional rejection rather than a
     * backend failure.
     *
     * @param reason a human-readable reason for the veto
     */
    record Rejected(@NotNull String reason) implements EconomyResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * The provider failed to complete the operation.
     *
     * @param message a human-readable error message
     * @param cause   the underlying cause, or {@code null}
     */
    record ProviderError(@NotNull String message, @Nullable Throwable cause) implements EconomyResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * @return {@code true} if this is a {@link Success}
     */
    boolean isSuccess();

    /**
     * @return the {@link Success} case if this is one, else empty
     */
    default @NotNull Optional<Success> success() {
        return this instanceof Success success ? Optional.of(success) : Optional.empty();
    }

    /**
     * Run {@code consumer} with the success case if this committed.
     *
     * @param consumer the success consumer
     * @return this result, for chaining
     */
    default @NotNull EconomyResult ifSuccess(@NotNull Consumer<Success> consumer) {
        if (this instanceof Success success) {
            consumer.accept(success);
        }
        return this;
    }

    /**
     * Experimental sugar: the resulting balance, present only on {@link Success}.
     * Named distinctly from {@link Success#newBalance()} (which is a non-optional
     * record accessor) so it can be called on the sealed type without a cast.
     *
     * @return the new balance if this committed, else empty
     */
    @ApiStatus.Experimental
    default @NotNull Optional<BigDecimal> committedBalance() {
        return this instanceof Success success ? Optional.of(success.newBalance()) : Optional.empty();
    }

    /**
     * Experimental sugar: run {@code consumer} for any non-{@link Success} outcome.
     * Complements {@link #ifSuccess(Consumer)} so a caller can chain both arms.
     *
     * @param consumer the failure consumer, invoked with this result
     * @return this result, for chaining
     */
    @ApiStatus.Experimental
    default @NotNull EconomyResult ifFailure(@NotNull Consumer<EconomyResult> consumer) {
        if (!(this instanceof Success)) {
            consumer.accept(this);
        }
        return this;
    }

    /**
     * Experimental sugar: a short, non-localized description of this outcome for
     * logging. Not player-facing and not intended for end-user display.
     *
     * @return a concise human-readable summary of this result
     */
    @ApiStatus.Experimental
    default @NotNull String describe() {
        return switch (this) {
            case Success s -> "Success: new balance " + s.newBalance() + " for " + s.account();
            case InsufficientFunds f ->
                    "InsufficientFunds: balance " + f.balance() + ", requested " + f.requested();
            case AccountNotFound f -> "AccountNotFound: " + f.uuid();
            case CurrencyNotSupported f -> "CurrencyNotSupported: " + f.currency().id();
            case Rejected r -> "Rejected: " + r.reason();
            case ProviderError e -> "ProviderError: " + e.message();
        };
    }
}

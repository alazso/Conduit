package so.alaz.conduit.api.result;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

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
}

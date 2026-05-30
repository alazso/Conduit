package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy providers that support multiple currencies on a single account.
 *
 * <h3>Default-currency semantics</h3>
 * The inherited no-currency operations from {@link Economy} are contractually
 * equivalent to invoking the currency-bearing overload below with
 * {@link Economy#defaultCurrency()}. A provider MUST implement both such that
 * this equivalence holds.
 *
 * <h3>Unsupported-currency semantics</h3>
 * If a target account does not support the requested currency
 * ({@link #accountSupportsCurrency} returns {@code false}), the operation
 * resolves to {@link EconomyResult.CurrencyNotSupported}. For {@link #transfer}
 * this applies symmetrically: if either endpoint lacks the currency, the future
 * resolves to {@code CurrencyNotSupported} and the source is not debited.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface MultiCurrencyEconomy extends Economy {

    /**
     * @return the immutable set of currencies this provider supports
     */
    @NotNull Set<Currency> supportedCurrencies();

    /**
     * @param uuid     the account UUID
     * @param currency the currency to test
     * @return a future resolving whether the account supports the currency
     */
    @NotNull CompletableFuture<Boolean> accountSupportsCurrency(@NotNull UUID uuid, @NotNull Currency currency);

    /**
     * @param uuid     the account UUID
     * @param currency the currency to query
     * @return a future resolving the account's balance in the given currency
     */
    @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid, @NotNull Currency currency);

    /**
     * @param uuid     the account to credit
     * @param amount   the positive magnitude to deposit
     * @param currency the currency to deposit
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull Currency currency);

    /**
     * @param uuid     the account to debit
     * @param amount   the positive magnitude to withdraw
     * @param currency the currency to withdraw
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull Currency currency);

    /**
     * @param from     the source account
     * @param to       the destination account
     * @param amount   the positive magnitude to transfer
     * @param currency the currency to transfer
     * @return a future resolving the result; source not debited on failure
     */
    @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull Currency currency);
}

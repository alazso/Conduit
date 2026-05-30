package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.model.TransactionFilter;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy providers that maintain an append-only transaction ledger and support
 * idempotent execution.
 *
 * <h3>Idempotency contract (normative)</h3>
 * <ol>
 *   <li><b>Uniqueness scope: per-account.</b> {@code operationId} is unique
 *       within the scope of the primary account the operation targets
 *       ({@code uuid} for deposit/withdraw, {@code from} for transfer). A fresh
 *       {@link UUID#randomUUID()} satisfies this trivially.</li>
 *   <li><b>Same id + same parameters ⇒ return original.</b> Resubmitting an
 *       {@code operationId} with matching parameters returns the original
 *       {@link EconomyResult} verbatim without re-executing or re-appending.</li>
 *   <li><b>Same id + different parameters ⇒ throw.</b> The provider MUST fail
 *       the returned future with
 *       {@link so.alaz.conduit.api.exception.IdempotencyMismatchException}.</li>
 * </ol>
 * Parameter equality is computed over the operation's intrinsic arguments
 * ({@code amount} by {@link BigDecimal#compareTo}, the account UUIDs, and
 * currency where applicable); {@code reason} and metadata are descriptive, not
 * definitional.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface TransactionalEconomy extends Economy {

    /**
     * @param uuid  the account UUID
     * @param limit the maximum number of transactions to return
     * @return a future resolving the most recent transactions, newest first
     */
    @NotNull CompletableFuture<List<Transaction>> getTransactionHistory(@NotNull UUID uuid, int limit);

    /**
     * @param uuid   the account UUID
     * @param filter the filter criteria
     * @return a future resolving the matching transactions, newest first
     */
    @NotNull CompletableFuture<List<Transaction>> getTransactionHistory(@NotNull UUID uuid, @NotNull TransactionFilter filter);

    /**
     * @param uuid        the account to credit
     * @param amount      the positive magnitude to deposit
     * @param operationId the idempotency key
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> depositIdempotent(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull UUID operationId);

    /**
     * @param uuid        the account to debit
     * @param amount      the positive magnitude to withdraw
     * @param operationId the idempotency key
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> withdrawIdempotent(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull UUID operationId);

    /**
     * @param from        the source account
     * @param to          the destination account
     * @param amount      the positive magnitude to transfer
     * @param operationId the idempotency key
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> transferIdempotent(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull UUID operationId);
}

package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.capability.Capable;
import so.alaz.conduit.api.capability.RequiresCapability;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The canonical, async-only economy surface.
 *
 * <p>{@link CompletableFuture} is the only return type for operations that touch
 * persistent storage; there are no synchronous overloads and no {@code double}
 * anywhere in this interface.
 *
 * <h3>Amount semantics (normative)</h3>
 * All {@code amount} parameters on {@link #deposit}, {@link #withdraw}, and
 * {@link #transfer} are <strong>magnitudes</strong>: the sign is implied by the
 * method name. A {@code null}, negative, or zero amount, or an amount whose
 * scale exceeds {@link Currency#decimalPlaces()}, is a programming error and
 * throws {@link IllegalArgumentException} synchronously at the call boundary —
 * the returned future does not carry these failures. {@link #set(UUID, BigDecimal)}
 * additionally permits zero (a legitimate balance state) but rejects negatives.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface Economy extends Capable {

    /**
     * @return the provider's display name
     */
    @NotNull String getName();

    /**
     * @return the currency used by all no-currency overloads on this interface
     */
    @NotNull Currency defaultCurrency();

    /**
     * @return the minimum Conduit API version this provider requires
     */
    default @NotNull String requiredApiVersion() {
        return "1.0";
    }

    // --- Account Management ---

    /**
     * @param uuid the account UUID
     * @return a future resolving {@code true} if the account exists
     */
    @NotNull CompletableFuture<Boolean> hasAccount(@NotNull UUID uuid);

    /**
     * @param uuid the account UUID to create
     * @return a future resolving the creation result
     */
    @NotNull CompletableFuture<EconomyResult> createAccount(@NotNull UUID uuid);

    /**
     * @param uuid the account UUID to delete
     * @return a future resolving the deletion result
     */
    @NotNull CompletableFuture<EconomyResult> deleteAccount(@NotNull UUID uuid);

    /**
     * @param uuid    the account UUID to rename
     * @param newName the new display name
     * @return a future resolving the rename result
     */
    @NotNull CompletableFuture<EconomyResult> renameAccount(@NotNull UUID uuid, @NotNull String newName);

    /**
     * @param uuid the owner UUID
     * @return a future resolving the accounts this UUID owns
     */
    @NotNull CompletableFuture<Set<UUID>> accountsWithOwnerOf(@NotNull UUID uuid);

    /**
     * @param uuid the member UUID
     * @return a future resolving the accounts this UUID is a member of
     */
    @NotNull CompletableFuture<Set<UUID>> accountsWithMembershipTo(@NotNull UUID uuid);

    /**
     * @param uuid the UUID to query
     * @return a future resolving accounts this UUID has any access level to
     */
    @NotNull CompletableFuture<Set<UUID>> accountsWithAccessTo(@NotNull UUID uuid);

    // --- Balance Queries ---

    /**
     * @param uuid the account UUID
     * @return a future resolving the account's balance in the default currency
     */
    @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid);

    // --- Pre-flight Checks ---

    /**
     * @param uuid   the account UUID
     * @param amount the positive magnitude to test
     * @return a future resolving whether a deposit would be permitted
     */
    @RequiresCapability(Capability.ECONOMY_PREFLIGHT)
    @NotNull CompletableFuture<Boolean> canDeposit(@NotNull UUID uuid, @NotNull BigDecimal amount);

    /**
     * @param uuid   the account UUID
     * @param amount the positive magnitude to test
     * @return a future resolving whether a withdrawal would be permitted
     */
    @RequiresCapability(Capability.ECONOMY_PREFLIGHT)
    @NotNull CompletableFuture<Boolean> canWithdraw(@NotNull UUID uuid, @NotNull BigDecimal amount);

    // --- Mutations ---

    /**
     * @param uuid   the account to credit
     * @param amount the positive magnitude to deposit
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount);

    /**
     * @param uuid   the account to credit
     * @param amount the positive magnitude to deposit
     * @param reason a human-readable reason
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason);

    /**
     * @param uuid   the account to debit
     * @param amount the positive magnitude to withdraw
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount);

    /**
     * @param uuid   the account to debit
     * @param amount the positive magnitude to withdraw
     * @param reason a human-readable reason
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason);

    /**
     * Overwrite an account's balance.
     *
     * @param uuid   the account to set
     * @param amount the non-negative absolute balance (zero permitted)
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount);

    // --- Atomic Transfer ---

    /**
     * @param from   the source account
     * @param to     the destination account
     * @param amount the positive magnitude to transfer
     * @return a future resolving the result; the source is not debited on failure
     */
    @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount);

    /**
     * @param from   the source account
     * @param to     the destination account
     * @param amount the positive magnitude to transfer
     * @param reason a human-readable reason
     * @return a future resolving the result; the source is not debited on failure
     */
    @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason);

    // --- Format & Builder ---

    /**
     * @param amount the amount to format
     * @return the amount formatted in the default currency
     */
    default @NotNull String format(@NotNull BigDecimal amount) {
        return defaultCurrency().format(amount);
    }

    /**
     * @return a fresh fluent transaction builder bound to this economy
     */
    default @NotNull TransactionBuilder transaction() {
        return new DefaultTransactionBuilder(this);
    }
}

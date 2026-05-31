package so.alaz.conduit.bridge.essentialsx;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Conduit {@link Economy} provider backed by EssentialsX.
 *
 * <p>EssentialsX is single-currency and exposes no first-class bank, history,
 * or idempotency API, so this bridge implements only the base economy contract.
 * EssentialsX operations are blocking, so every method is dispatched onto the
 * supplied {@link Executor} (an async worker in production).
 *
 * <p>EssentialsX has no transactional API, so each balance change is a
 * read-modify-write serialized per account via {@link AccountLocks}; transfers
 * lock both endpoints and roll back the debit if the credit fails, so a partial
 * failure cannot destroy money. {@link Capability#ECONOMY_OFFLINE_PLAYERS} is
 * honoured for offline players that already have an EssentialsX account;
 * operations targeting a never-seen UUID resolve to
 * {@link EconomyResult.AccountNotFound} rather than throwing.
 */
public final class EssentialsXEconomy implements Economy {

    private final EssentialsEconomyBackend backend;
    private final Executor executor;
    private final Currency currency;
    private final AccountLocks locks = new AccountLocks();

    /**
     * @param backend  the EssentialsX seam
     * @param executor the executor blocking calls are dispatched onto
     */
    public EssentialsXEconomy(@NotNull EssentialsEconomyBackend backend, @NotNull Executor executor) {
        this.backend = backend;
        this.executor = executor;
        this.currency = new SimpleCurrency("essentials", "money", "money",
                backend.currencySymbol(), backend.fractionalDigits(), true);
    }

    @Override
    public @NotNull String getName() {
        return "EssentialsX";
    }

    @Override
    public @NotNull Currency defaultCurrency() {
        return currency;
    }

    @Override
    public @NotNull String requiredApiVersion() {
        return "1.0.0";
    }

    @Override
    public @NotNull Set<Capability> capabilities() {
        return EnumSet.of(Capability.ECONOMY_OFFLINE_PLAYERS, Capability.ECONOMY_FRACTIONAL_BALANCES, Capability.ECONOMY_PREFLIGHT);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasAccount(@NotNull UUID uuid) {
        return async(() -> backend.hasAccount(uuid));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> createAccount(@NotNull UUID uuid) {
        return async(() -> {
            backend.ensureAccount(uuid);
            return success(uuid, backend.balance(uuid), null);
        });
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteAccount(@NotNull UUID uuid) {
        return async(() -> {
            if (!backend.hasAccount(uuid)) {
                return new EconomyResult.AccountNotFound(uuid);
            }
            BigDecimal last = backend.balance(uuid);
            backend.removeAccount(uuid);
            return success(uuid, last, null);
        });
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> renameAccount(@NotNull UUID uuid, @NotNull String newName) {
        // EssentialsX stores no display name for an economy account; treat as a no-op success.
        return async(() -> success(uuid, backend.balance(uuid), null));
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid) {
        return async(() -> new Balance(uuid, currency, backend.balance(uuid)));
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithOwnerOf(@NotNull UUID uuid) {
        return async(() -> backend.hasAccount(uuid) ? Set.of(uuid) : Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithMembershipTo(@NotNull UUID uuid) {
        return CompletableFuture.completedFuture(Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithAccessTo(@NotNull UUID uuid) {
        return async(() -> backend.hasAccount(uuid) ? Set.of(uuid) : Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canDeposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canWithdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return async(() -> backend.balance(uuid).compareTo(amount) >= 0);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return depositInternal(uuid, amount, null);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return depositInternal(uuid, amount, reason);
    }

    private CompletableFuture<EconomyResult> depositInternal(UUID uuid, BigDecimal amount, @Nullable String reason) {
        return async(() -> locks.withLock(uuid, () -> {
            if (!backend.hasAccount(uuid)) {
                return new EconomyResult.AccountNotFound(uuid);
            }
            BigDecimal before = backend.balance(uuid);
            BigDecimal after = before.add(amount);
            backend.setBalance(uuid, after);
            return success(uuid, after, txn(TransactionType.DEPOSIT, uuid, amount, before, after, reason));
        }));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return withdrawInternal(uuid, amount, null);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return withdrawInternal(uuid, amount, reason);
    }

    private CompletableFuture<EconomyResult> withdrawInternal(UUID uuid, BigDecimal amount, @Nullable String reason) {
        return async(() -> locks.withLock(uuid, () -> {
            if (!backend.hasAccount(uuid)) {
                return new EconomyResult.AccountNotFound(uuid);
            }
            BigDecimal before = backend.balance(uuid);
            if (before.compareTo(amount) < 0) {
                return new EconomyResult.InsufficientFunds(before, amount, currency);
            }
            BigDecimal after = before.subtract(amount);
            backend.setBalance(uuid, after);
            return success(uuid, after, txn(TransactionType.WITHDRAWAL, uuid, amount, before, after, reason));
        }));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return async(() -> locks.withLock(uuid, () -> {
            if (!backend.hasAccount(uuid)) {
                return new EconomyResult.AccountNotFound(uuid);
            }
            backend.setBalance(uuid, amount);
            return success(uuid, amount, null);
        }));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        return transferInternal(from, to, amount, null);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason) {
        return transferInternal(from, to, amount, reason);
    }

    private CompletableFuture<EconomyResult> transferInternal(UUID from, UUID to, BigDecimal amount, @Nullable String reason) {
        return async(() -> locks.withLocks(from, to, () -> {
            if (!backend.hasAccount(from)) {
                return new EconomyResult.AccountNotFound(from);
            }
            if (!backend.hasAccount(to)) {
                return new EconomyResult.AccountNotFound(to);
            }
            BigDecimal fromBefore = backend.balance(from);
            if (fromBefore.compareTo(amount) < 0) {
                return new EconomyResult.InsufficientFunds(fromBefore, amount, currency);
            }
            BigDecimal toBefore = backend.balance(to);
            BigDecimal fromAfter = fromBefore.subtract(amount);
            backend.setBalance(from, fromAfter);
            try {
                backend.setBalance(to, toBefore.add(amount));
            } catch (RuntimeException creditFailure) {
                // The debit already committed; roll it back so a failed credit
                // cannot destroy money.
                try {
                    backend.setBalance(from, fromBefore);
                } catch (RuntimeException rollbackFailure) {
                    creditFailure.addSuppressed(rollbackFailure);
                    return new EconomyResult.ProviderError(
                            "Transfer credit to " + to + " failed and rolling back the debit from " + from
                                    + " also failed; " + from + " may be short by " + amount, creditFailure);
                }
                return new EconomyResult.ProviderError(
                        "Transfer credit to " + to + " failed; the debit from " + from + " was rolled back",
                        creditFailure);
            }
            return success(from, fromAfter, txn(TransactionType.TRANSFER_OUT, from, amount, fromBefore, fromAfter, reason));
        }));
    }

    private <T> CompletableFuture<T> async(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, executor);
    }

    private EconomyResult.Success success(UUID account, BigDecimal newBalance, @Nullable Transaction txn) {
        return new EconomyResult.Success(account, currency, newBalance, txn);
    }

    private Transaction txn(TransactionType type, UUID target, BigDecimal amount, BigDecimal before, BigDecimal after, @Nullable String reason) {
        return new Transaction(UUID.randomUUID(), type, null, target, currency, amount, before, after, reason, Map.of(), Instant.now());
    }
}

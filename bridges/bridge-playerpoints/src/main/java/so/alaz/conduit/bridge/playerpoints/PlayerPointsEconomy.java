package so.alaz.conduit.bridge.playerpoints;

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
 * Conduit {@link Economy} provider backed by PlayerPoints.
 *
 * <p>PlayerPoints is a single, whole-number ("points") currency with no decimal
 * places, bank, history, or idempotency API, so this bridge implements only the
 * base economy contract and does not declare
 * {@link Capability#ECONOMY_FRACTIONAL_BALANCES}. Conduit's dispatch layer rejects
 * any amount whose scale exceeds the currency's zero decimal places before it
 * reaches the bridge, so every amount here is a whole number.
 *
 * <p>PlayerPoints operations are blocking, so every method is dispatched onto the
 * supplied {@link Executor} (an async worker in production). Balance reads and
 * writes are a check-then-act pair serialized per account via {@link AccountLocks}
 * so concurrent operations cannot overdraw; transfers use PlayerPoints' native
 * atomic {@code pay} and lock both endpoints. Deposits, withdrawals, and transfers
 * route through the native {@code give}/{@code take}/{@code pay} calls so other
 * plugins listening for PlayerPoints change events still observe them.
 *
 * <p>Account existence follows the backend: with the live PlayerPoints API every
 * account exists implicitly (see {@link PlayerPointsBackendImpl}), so operations
 * against a never-seen UUID succeed rather than resolving to
 * {@link EconomyResult.AccountNotFound}.
 */
public final class PlayerPointsEconomy implements Economy {

    private final PlayerPointsBackend backend;
    private final Executor executor;
    private final Currency currency = SimpleCurrency.ofDefault("playerpoints", "pts ", 0);
    private final AccountLocks locks = new AccountLocks();

    /**
     * @param backend  the PlayerPoints seam
     * @param executor the executor blocking calls are dispatched onto
     */
    public PlayerPointsEconomy(@NotNull PlayerPointsBackend backend, @NotNull Executor executor) {
        this.backend = backend;
        this.executor = executor;
    }

    @Override
    public @NotNull String getName() {
        return "PlayerPoints";
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
        return EnumSet.of(Capability.ECONOMY_OFFLINE_PLAYERS, Capability.ECONOMY_PREFLIGHT);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasAccount(@NotNull UUID uuid) {
        return async(() -> backend.hasAccount(uuid));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> createAccount(@NotNull UUID uuid) {
        return async(() -> {
            backend.ensureAccount(uuid);
            return success(uuid, balanceOf(uuid), null);
        });
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteAccount(@NotNull UUID uuid) {
        return async(() -> {
            if (!backend.hasAccount(uuid)) {
                return new EconomyResult.AccountNotFound(uuid);
            }
            BigDecimal last = balanceOf(uuid);
            backend.removeAccount(uuid);
            return success(uuid, last, null);
        });
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> renameAccount(@NotNull UUID uuid, @NotNull String newName) {
        // PlayerPoints stores no display name for an account; treat as a no-op success.
        return async(() -> success(uuid, balanceOf(uuid), null));
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid) {
        return async(() -> new Balance(uuid, currency, balanceOf(uuid)));
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
        return async(() -> balanceOf(uuid).compareTo(amount) >= 0);
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
            int points = toPoints(amount);
            if (points < 0) {
                return overflow(amount);
            }
            BigDecimal before = balanceOf(uuid);
            if (!backend.give(uuid, points)) {
                return new EconomyResult.ProviderError("PlayerPoints rejected the deposit for " + uuid, null);
            }
            BigDecimal after = balanceOf(uuid);
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
            int points = toPoints(amount);
            if (points < 0) {
                return overflow(amount);
            }
            BigDecimal before = balanceOf(uuid);
            if (before.compareTo(amount) < 0) {
                return new EconomyResult.InsufficientFunds(before, amount, currency);
            }
            if (!backend.take(uuid, points)) {
                return new EconomyResult.ProviderError("PlayerPoints rejected the withdrawal for " + uuid, null);
            }
            BigDecimal after = balanceOf(uuid);
            return success(uuid, after, txn(TransactionType.WITHDRAWAL, uuid, amount, before, after, reason));
        }));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return async(() -> locks.withLock(uuid, () -> {
            if (!backend.hasAccount(uuid)) {
                return new EconomyResult.AccountNotFound(uuid);
            }
            int points = toPoints(amount);
            if (points < 0) {
                return overflow(amount);
            }
            if (!backend.set(uuid, points)) {
                return new EconomyResult.ProviderError("PlayerPoints rejected the balance set for " + uuid, null);
            }
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
            int points = toPoints(amount);
            if (points < 0) {
                return overflow(amount);
            }
            BigDecimal fromBefore = balanceOf(from);
            if (fromBefore.compareTo(amount) < 0) {
                return new EconomyResult.InsufficientFunds(fromBefore, amount, currency);
            }
            // pay() is atomic in PlayerPoints (it rolls back the debit if the
            // credit fails), so a thrown or false result leaves balances untouched.
            try {
                if (!backend.pay(from, to, points)) {
                    return new EconomyResult.ProviderError(
                            "PlayerPoints rejected the transfer from " + from + " to " + to, null);
                }
            } catch (RuntimeException failure) {
                return new EconomyResult.ProviderError(
                        "PlayerPoints transfer from " + from + " to " + to + " failed", failure);
            }
            BigDecimal fromAfter = balanceOf(from);
            return success(from, fromAfter, txn(TransactionType.TRANSFER_OUT, from, amount, fromBefore, fromAfter, reason));
        }));
    }

    private BigDecimal balanceOf(UUID uuid) {
        return BigDecimal.valueOf(backend.look(uuid));
    }

    /**
     * @return the amount as whole points, or {@code -1} if it does not fit in an
     *         {@code int} (amounts are pre-validated to whole numbers by dispatch)
     */
    private static int toPoints(BigDecimal amount) {
        try {
            return amount.intValueExact();
        } catch (ArithmeticException overflow) {
            return -1;
        }
    }

    private EconomyResult overflow(BigDecimal amount) {
        return new EconomyResult.ProviderError(
                "PlayerPoints supports whole-number amounts up to " + Integer.MAX_VALUE + "; got " + amount.toPlainString(),
                null);
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

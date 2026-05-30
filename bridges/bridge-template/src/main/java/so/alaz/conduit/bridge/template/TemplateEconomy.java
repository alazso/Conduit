package so.alaz.conduit.bridge.template;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starter template for a Conduit economy bridge.
 *
 * <p>This reference implements the base {@link Economy} contract over a simple
 * in-memory store. Replace the storage calls with your backend, decide which
 * extension interfaces ({@code BankingEconomy}, {@code TransactionalEconomy},
 * {@code MultiCurrencyEconomy}) you can faithfully support, and validate against
 * the shared conformance suites in {@code conduit-test-fixtures}.
 */
public final class TemplateEconomy implements Economy {

    private final Currency currency = SimpleCurrency.ofDefault("template", "$", 2);
    private final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();

    @Override
    public @NotNull String getName() {
        return "Template";
    }

    @Override
    public @NotNull Currency defaultCurrency() {
        return currency;
    }

    @Override
    public @NotNull Set<Capability> capabilities() {
        return EnumSet.of(Capability.ECONOMY_OFFLINE_PLAYERS, Capability.ECONOMY_FRACTIONAL_BALANCES);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasAccount(@NotNull UUID uuid) {
        return done(balances.containsKey(uuid));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> createAccount(@NotNull UUID uuid) {
        balances.putIfAbsent(uuid, BigDecimal.ZERO);
        return done(success(uuid, balances.get(uuid), null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteAccount(@NotNull UUID uuid) {
        BigDecimal last = balances.remove(uuid);
        if (last == null) {
            return done(new EconomyResult.AccountNotFound(uuid));
        }
        return done(success(uuid, last, null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> renameAccount(@NotNull UUID uuid, @NotNull String newName) {
        return done(success(uuid, balances.getOrDefault(uuid, BigDecimal.ZERO), null));
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid) {
        return done(new Balance(uuid, currency, balances.getOrDefault(uuid, BigDecimal.ZERO)));
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithOwnerOf(@NotNull UUID uuid) {
        return done(balances.containsKey(uuid) ? Set.of(uuid) : Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithMembershipTo(@NotNull UUID uuid) {
        return done(Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithAccessTo(@NotNull UUID uuid) {
        return done(balances.containsKey(uuid) ? Set.of(uuid) : Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canDeposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return done(true);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canWithdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return done(balances.getOrDefault(uuid, BigDecimal.ZERO).compareTo(amount) >= 0);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        BigDecimal before = balances.getOrDefault(uuid, BigDecimal.ZERO);
        BigDecimal after = before.add(amount);
        balances.put(uuid, after);
        return done(success(uuid, after, txn(TransactionType.DEPOSIT, uuid, amount, before, after)));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return deposit(uuid, amount);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        BigDecimal before = balances.getOrDefault(uuid, BigDecimal.ZERO);
        if (before.compareTo(amount) < 0) {
            return done(new EconomyResult.InsufficientFunds(before, amount, currency));
        }
        BigDecimal after = before.subtract(amount);
        balances.put(uuid, after);
        return done(success(uuid, after, txn(TransactionType.WITHDRAWAL, uuid, amount, before, after)));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return withdraw(uuid, amount);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        balances.put(uuid, amount);
        return done(success(uuid, amount, null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        BigDecimal fromBefore = balances.getOrDefault(from, BigDecimal.ZERO);
        if (fromBefore.compareTo(amount) < 0) {
            return done(new EconomyResult.InsufficientFunds(fromBefore, amount, currency));
        }
        BigDecimal fromAfter = fromBefore.subtract(amount);
        balances.put(from, fromAfter);
        balances.put(to, balances.getOrDefault(to, BigDecimal.ZERO).add(amount));
        return done(success(from, fromAfter, txn(TransactionType.TRANSFER_OUT, from, amount, fromBefore, fromAfter)));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason) {
        return transfer(from, to, amount);
    }

    private EconomyResult.Success success(UUID account, BigDecimal newBalance, @Nullable Transaction txn) {
        return new EconomyResult.Success(account, currency, newBalance, txn);
    }

    private Transaction txn(TransactionType type, UUID target, BigDecimal amount, BigDecimal before, BigDecimal after) {
        return new Transaction(UUID.randomUUID(), type, null, target, currency, amount, before, after, null, Map.of(), Instant.now());
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }
}

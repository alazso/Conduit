package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link TransactionBuilder} that records a single operation and, on
 * {@link #execute()}, dispatches it through the owning {@link Economy}'s own
 * async methods.
 *
 * <p>Package-private and lives in {@code conduit-api} (not {@code conduit-core})
 * because {@link Economy#transaction()} is a default method and the API module
 * must not depend on the runtime module.
 */
final class DefaultTransactionBuilder implements TransactionBuilder {

    private enum Kind { NONE, WITHDRAW, DEPOSIT, TRANSFER }

    private final Economy economy;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    private Kind kind = Kind.NONE;
    private UUID primary;
    private UUID destination;
    private BigDecimal amount;
    private String reason;

    DefaultTransactionBuilder(@NotNull Economy economy) {
        this.economy = economy;
    }

    @Override
    public @NotNull TransactionBuilder withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        ensureUnset();
        this.kind = Kind.WITHDRAW;
        this.primary = uuid;
        this.amount = amount;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder deposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        ensureUnset();
        this.kind = Kind.DEPOSIT;
        this.primary = uuid;
        this.amount = amount;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        ensureUnset();
        this.kind = Kind.TRANSFER;
        this.primary = from;
        this.destination = to;
        this.amount = amount;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder reason(@NotNull String reason) {
        this.reason = reason;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder metadata(@NotNull String key, @NotNull String value) {
        this.metadata.put(key, value);
        return this;
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> execute() {
        return switch (kind) {
            case WITHDRAW -> reason != null
                    ? economy.withdraw(primary, amount, reason)
                    : economy.withdraw(primary, amount);
            case DEPOSIT -> reason != null
                    ? economy.deposit(primary, amount, reason)
                    : economy.deposit(primary, amount);
            case TRANSFER -> reason != null
                    ? economy.transfer(primary, destination, amount, reason)
                    : economy.transfer(primary, destination, amount);
            case NONE -> throw new IllegalStateException(
                    "No operation configured on TransactionBuilder; call withdraw/deposit/transfer first");
        };
    }

    private void ensureUnset() {
        if (kind != Kind.NONE) {
            throw new IllegalStateException("TransactionBuilder already has a configured operation: " + kind);
        }
    }
}

package so.alaz.conduit.api;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.exception.ProviderNotFoundException;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.registry.ProviderRegistry;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Static entry point to the Conduit runtime.
 *
 * <p>The {@link ProviderRegistry} is installed by {@code conduit-core} during
 * plugin bootstrap. The registry is the sole owner of dispatch decoration: every
 * resolved {@link Economy} it returns is already wrapped with the dispatch layer
 * (synchronous amount validation, pre-auth interceptors, post-commit events), so
 * this facade simply forwards to it.
 */
public final class Conduit {

    /**
     * The {@code major.minor} version of the Conduit API this runtime ships.
     * Providers declare a minimum via {@link Economy#requiredApiVersion()}; the
     * registry rejects providers requiring a newer API than this.
     */
    public static final String API_VERSION = "1.0";

    private static volatile ProviderRegistry registry;

    private Conduit() {
    }

    /**
     * @return {@code true} if the Conduit runtime has been initialised (the
     *         plugin is enabled); {@code false} before enable or after disable
     */
    public static boolean isInitialized() {
        return registry != null;
    }

    /**
     * @return the active provider registry
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull ProviderRegistry getRegistry() {
        ProviderRegistry r = registry;
        if (r == null) {
            throw new IllegalStateException(
                    "Conduit runtime is not initialised. Is the Conduit plugin installed and enabled?");
        }
        return r;
    }

    /**
     * @return the active economy provider, wrapped with the dispatch layer
     * @throws so.alaz.conduit.api.exception.ProviderNotFoundException if none is registered
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull Economy getEconomy() {
        return getRegistry().requireProvider(Economy.class);
    }

    /**
     * @return the active economy provider wrapped with the dispatch layer, or empty
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull Optional<Economy> findEconomy() {
        return getRegistry().getProvider(Economy.class);
    }

    /**
     * Order-insensitive provider consumption; preferred over {@link #getEconomy()}
     * in {@code onEnable()}.
     *
     * @param service  the service type of interest
     * @param consumer the consumer of the provider
     * @param <T>      the service type
     */
    public static <T> void whenProviderAvailable(@NotNull Class<T> service, @NotNull Consumer<T> consumer) {
        getRegistry().whenProviderAvailable(service, consumer);
    }

    // --- Convenience surface (experimental) ---------------------------------
    //
    // Opt-in one-liners for the common "mutate and react" path. Every method
    // here delegates to the active Economy and adds no behaviour of its own:
    // same async CompletableFuture, same BigDecimal, same synchronous amount
    // validation. Advanced consumers should keep using getEconomy() / the
    // registry directly. This whole surface is @ApiStatus.Experimental for the
    // 0.x line and may move before 1.0; the core Economy contract is stable.

    /**
     * Soft no-provider outcome for mutations: a <em>completed</em> future
     * carrying {@link EconomyResult.ProviderError}, so a beginner's
     * {@code thenAccept} always runs even when no economy plugin is installed.
     *
     * @return a completed future describing the missing provider
     */
    private static @NotNull CompletableFuture<EconomyResult> noProvider() {
        return CompletableFuture.completedFuture(new EconomyResult.ProviderError(
                "No economy provider is registered. Is an economy plugin installed?", null));
    }

    /**
     * Experimental sugar over {@link Economy#deposit(UUID, BigDecimal, String)};
     * delegates to the active provider and adds no behaviour.
     *
     * <p><strong>Attribution:</strong> this does not bind a {@link so.alaz.conduit.api.caller.CallerToken};
     * attribution flows from whatever the caller bound via
     * {@link so.alaz.conduit.api.caller.CallerToken#runWith}, else
     * {@link so.alaz.conduit.api.caller.CallerToken#ANONYMOUS}.
     *
     * <p><strong>No provider:</strong> returns a completed future carrying
     * {@link EconomyResult.ProviderError} (soft), so {@code thenAccept} still runs.
     *
     * <p><strong>Validation:</strong> a {@code null}, negative, zero, or
     * scale-overflowing {@code amount} throws {@link IllegalArgumentException}
     * <em>synchronously</em> at this call — before the future exists, exactly as
     * {@link Economy#deposit(UUID, BigDecimal, String)} does. It is not delivered
     * via {@code exceptionally}.
     *
     * @param account the account to credit
     * @param amount  the positive magnitude to deposit
     * @param reason  a human-readable audit reason
     * @return a future resolving the result; completed {@link EconomyResult.ProviderError} if none is registered
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> deposit(
            @NotNull UUID account, @NotNull BigDecimal amount, @NotNull String reason) {
        return findEconomy().map(e -> e.deposit(account, amount, reason)).orElseGet(Conduit::noProvider);
    }

    /**
     * Experimental sugar over {@link Economy#withdraw(UUID, BigDecimal, String)};
     * delegates to the active provider and adds no behaviour. See
     * {@link #deposit(UUID, BigDecimal, String)} for attribution, no-provider,
     * and validation semantics.
     *
     * @param account the account to debit
     * @param amount  the positive magnitude to withdraw
     * @param reason  a human-readable audit reason
     * @return a future resolving the result; completed {@link EconomyResult.ProviderError} if none is registered
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> withdraw(
            @NotNull UUID account, @NotNull BigDecimal amount, @NotNull String reason) {
        return findEconomy().map(e -> e.withdraw(account, amount, reason)).orElseGet(Conduit::noProvider);
    }

    /**
     * Experimental sugar over {@link Economy#transfer(UUID, UUID, BigDecimal, String)};
     * delegates to the active provider and adds no behaviour. See
     * {@link #deposit(UUID, BigDecimal, String)} for attribution, no-provider,
     * and validation semantics.
     *
     * @param from   the source account
     * @param to     the destination account
     * @param amount the positive magnitude to transfer
     * @param reason a human-readable audit reason
     * @return a future resolving the result; completed {@link EconomyResult.ProviderError} if none is registered
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> transfer(
            @NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason) {
        return findEconomy().map(e -> e.transfer(from, to, amount, reason)).orElseGet(Conduit::noProvider);
    }

    /**
     * Experimental sugar over {@link Economy#getBalance(UUID)}; delegates to the
     * active provider and adds no behaviour.
     *
     * <p><strong>No provider:</strong> unlike the mutations, this returns an
     * <em>exceptionally</em> completed future carrying
     * {@link ProviderNotFoundException}, because {@link Balance} has no neutral
     * inhabitant. Handle it with {@code exceptionally}.
     *
     * @param account the account to query
     * @return a future resolving the balance; exceptionally completed with {@link ProviderNotFoundException} if none is registered
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<Balance> balance(@NotNull UUID account) {
        return findEconomy().map(e -> e.getBalance(account))
                .orElseGet(() -> CompletableFuture.failedFuture(new ProviderNotFoundException(Economy.class)));
    }

    // long-amount overloads: BigDecimal.valueOf(long) has scale 0 and never
    // trips scale validation. Each delegates to the canonical BigDecimal method.

    /**
     * {@code long}-amount overload of {@link #deposit(UUID, BigDecimal, String)}.
     *
     * @param account the account to credit
     * @param amount  the positive magnitude to deposit
     * @param reason  a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> deposit(
            @NotNull UUID account, long amount, @NotNull String reason) {
        return deposit(account, BigDecimal.valueOf(amount), reason);
    }

    /**
     * {@code long}-amount overload of {@link #withdraw(UUID, BigDecimal, String)}.
     *
     * @param account the account to debit
     * @param amount  the positive magnitude to withdraw
     * @param reason  a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> withdraw(
            @NotNull UUID account, long amount, @NotNull String reason) {
        return withdraw(account, BigDecimal.valueOf(amount), reason);
    }

    /**
     * {@code long}-amount overload of {@link #transfer(UUID, UUID, BigDecimal, String)}.
     *
     * @param from   the source account
     * @param to     the destination account
     * @param amount the positive magnitude to transfer
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> transfer(
            @NotNull UUID from, @NotNull UUID to, long amount, @NotNull String reason) {
        return transfer(from, to, BigDecimal.valueOf(amount), reason);
    }

    // OfflinePlayer overloads: resolve to player.getUniqueId(). Kept homogeneous
    // (no mixed player/UUID signatures) to avoid ambiguous overload resolution.

    /**
     * {@link OfflinePlayer} overload of {@link #deposit(UUID, BigDecimal, String)}.
     *
     * @param player the account owner to credit
     * @param amount the positive magnitude to deposit
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> deposit(
            @NotNull OfflinePlayer player, @NotNull BigDecimal amount, @NotNull String reason) {
        return deposit(player.getUniqueId(), amount, reason);
    }

    /**
     * {@link OfflinePlayer}, {@code long}-amount overload of {@link #deposit(UUID, BigDecimal, String)}.
     *
     * @param player the account owner to credit
     * @param amount the positive magnitude to deposit
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> deposit(
            @NotNull OfflinePlayer player, long amount, @NotNull String reason) {
        return deposit(player.getUniqueId(), BigDecimal.valueOf(amount), reason);
    }

    /**
     * {@link OfflinePlayer} overload of {@link #withdraw(UUID, BigDecimal, String)}.
     *
     * @param player the account owner to debit
     * @param amount the positive magnitude to withdraw
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> withdraw(
            @NotNull OfflinePlayer player, @NotNull BigDecimal amount, @NotNull String reason) {
        return withdraw(player.getUniqueId(), amount, reason);
    }

    /**
     * {@link OfflinePlayer}, {@code long}-amount overload of {@link #withdraw(UUID, BigDecimal, String)}.
     *
     * @param player the account owner to debit
     * @param amount the positive magnitude to withdraw
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> withdraw(
            @NotNull OfflinePlayer player, long amount, @NotNull String reason) {
        return withdraw(player.getUniqueId(), BigDecimal.valueOf(amount), reason);
    }

    /**
     * {@link OfflinePlayer} overload of {@link #transfer(UUID, UUID, BigDecimal, String)}.
     *
     * @param from   the source account owner
     * @param to     the destination account owner
     * @param amount the positive magnitude to transfer
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> transfer(
            @NotNull OfflinePlayer from, @NotNull OfflinePlayer to, @NotNull BigDecimal amount, @NotNull String reason) {
        return transfer(from.getUniqueId(), to.getUniqueId(), amount, reason);
    }

    /**
     * {@link OfflinePlayer}, {@code long}-amount overload of {@link #transfer(UUID, UUID, BigDecimal, String)}.
     *
     * @param from   the source account owner
     * @param to     the destination account owner
     * @param amount the positive magnitude to transfer
     * @param reason a human-readable audit reason
     * @return a future resolving the result
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<EconomyResult> transfer(
            @NotNull OfflinePlayer from, @NotNull OfflinePlayer to, long amount, @NotNull String reason) {
        return transfer(from.getUniqueId(), to.getUniqueId(), BigDecimal.valueOf(amount), reason);
    }

    /**
     * {@link OfflinePlayer} overload of {@link #balance(UUID)}.
     *
     * @param player the account owner to query
     * @return a future resolving the balance; exceptionally completed with {@link ProviderNotFoundException} if none is registered
     */
    @ApiStatus.Experimental
    public static @NotNull CompletableFuture<Balance> balance(@NotNull OfflinePlayer player) {
        return balance(player.getUniqueId());
    }

    /**
     * Install the runtime registry. Called once by {@code conduit-core}.
     *
     * @param providerRegistry the registry implementation
     */
    @ApiStatus.Internal
    public static void init(@NotNull ProviderRegistry providerRegistry) {
        registry = providerRegistry;
    }

    /**
     * Tear down the runtime references. Called by {@code conduit-core} on disable
     * and usable by tests to reset global state.
     */
    @ApiStatus.Internal
    public static void shutdown() {
        registry = null;
    }
}

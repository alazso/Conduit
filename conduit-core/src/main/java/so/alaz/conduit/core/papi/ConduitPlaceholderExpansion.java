package so.alaz.conduit.core.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion exposing economy balances.
 *
 * <p>PlaceholderAPI resolves placeholders synchronously, but Conduit balances
 * are async. Each lookup serves the last cached value while it is fresh and,
 * when it is stale or missing, kicks off a single throttled async refresh so
 * subsequent lookups converge on the live balance without blocking the main
 * thread. Until a value is known the expansion returns {@link #LOADING} rather
 * than a misleading {@code 0}.
 *
 * <p>The cache is a bounded LRU keyed by player UUID, so a server with many
 * distinct players cannot grow it without limit. The expansion deliberately
 * does <em>not</em> implement {@code Listener}: PlaceholderAPI auto-registers
 * listener expansions via an internal {@code registerEvents} call that Paper's
 * modern loader rejects during plugin enable.
 *
 * <p>Supported placeholders: {@code %conduit_balance%},
 * {@code %conduit_balance_formatted%}, {@code %conduit_currency_symbol%},
 * {@code %conduit_provider%}.
 */
@ApiStatus.Internal
public final class ConduitPlaceholderExpansion extends PlaceholderExpansion {

    /** Sentinel returned while a balance has not yet been resolved. */
    public static final String LOADING = "...";

    private static final long TTL_MILLIS = 5_000L;
    private static final int MAX_ENTRIES = 1_000;

    private final String version;
    private final Map<UUID, CacheEntry> balanceCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, CacheEntry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });
    private final java.util.Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ConduitPlaceholderExpansion(@NotNull String version) {
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "conduit";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Alazso";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        Optional<Economy> economy = Conduit.findEconomy();
        return switch (params.toLowerCase()) {
            case "provider" -> economy.map(Economy::getName).orElse("none");
            case "currency_symbol" -> economy.map(e -> e.defaultCurrency().symbol()).orElse("");
            case "balance" -> economy
                    .map(e -> renderBalance(e, player, BigDecimal::toPlainString))
                    .orElse("0");
            case "balance_formatted" -> economy
                    .map(e -> renderBalance(e, player, amount -> e.defaultCurrency().format(amount)))
                    .orElse("");
            default -> null;
        };
    }

    private String renderBalance(Economy economy, @Nullable OfflinePlayer player,
                                 java.util.function.Function<BigDecimal, String> formatter) {
        BigDecimal amount = balance(economy, player);
        return amount == null ? LOADING : formatter.apply(amount);
    }

    /**
     * @return the last known balance, or {@code null} if not yet resolved.
     *         A stale or missing entry triggers a single throttled async refresh.
     */
    private @Nullable BigDecimal balance(Economy economy, @Nullable OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        CacheEntry entry = balanceCache.get(uuid);
        long now = System.currentTimeMillis();
        boolean stale = entry == null || now - entry.timestampMillis > TTL_MILLIS;
        if (stale && inFlight.add(uuid)) {
            economy.getBalance(uuid).whenComplete((balance, throwable) -> {
                if (balance != null) {
                    balanceCache.put(uuid, new CacheEntry(balance.amount(), System.currentTimeMillis()));
                }
                inFlight.remove(uuid);
            });
        }
        return entry == null ? null : entry.amount;
    }

    private record CacheEntry(BigDecimal amount, long timestampMillis) {
    }
}

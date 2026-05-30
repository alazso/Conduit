package so.alaz.conduit.bridge.essentialsx;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EssentialsEconomyBackend} for exercising the bridge
 * translation logic without a live EssentialsX/server.
 */
final class FakeEssentialsBackend implements EssentialsEconomyBackend {

    private final ConcurrentHashMap<UUID, BigDecimal> balances = new ConcurrentHashMap<>();

    @Override
    public boolean hasAccount(@NotNull UUID uuid) {
        return balances.containsKey(uuid);
    }

    @Override
    public void ensureAccount(@NotNull UUID uuid) {
        balances.putIfAbsent(uuid, BigDecimal.ZERO);
    }

    @Override
    public void removeAccount(@NotNull UUID uuid) {
        balances.remove(uuid);
    }

    @Override
    public @NotNull BigDecimal balance(@NotNull UUID uuid) {
        return balances.getOrDefault(uuid, BigDecimal.ZERO);
    }

    @Override
    public void setBalance(@NotNull UUID uuid, @NotNull BigDecimal balance) {
        balances.put(uuid, balance);
    }

    @Override
    public @NotNull String currencySymbol() {
        return "$";
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }
}

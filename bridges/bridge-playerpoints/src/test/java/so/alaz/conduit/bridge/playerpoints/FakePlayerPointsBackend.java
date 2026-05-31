package so.alaz.conduit.bridge.playerpoints;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link PlayerPointsBackend} for exercising the bridge translation
 * logic without a live PlayerPoints/server.
 *
 * <p>Unlike production, this fake models account existence explicitly (via a
 * tracked set) so the shared conformance suite — which asserts existence
 * transitions across create/delete — exercises the bridge's
 * {@link so.alaz.conduit.api.result.EconomyResult.AccountNotFound} paths.
 */
class FakePlayerPointsBackend implements PlayerPointsBackend {

    private final Set<UUID> accounts = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Integer> points = new ConcurrentHashMap<>();

    @Override
    public boolean hasAccount(@NotNull UUID uuid) {
        return accounts.contains(uuid);
    }

    @Override
    public void ensureAccount(@NotNull UUID uuid) {
        accounts.add(uuid);
        points.putIfAbsent(uuid, 0);
    }

    @Override
    public boolean removeAccount(@NotNull UUID uuid) {
        points.remove(uuid);
        return accounts.remove(uuid);
    }

    @Override
    public int look(@NotNull UUID uuid) {
        return points.getOrDefault(uuid, 0);
    }

    @Override
    public boolean give(@NotNull UUID uuid, int amount) {
        points.merge(uuid, amount, Integer::sum);
        return true;
    }

    @Override
    public boolean take(@NotNull UUID uuid, int amount) {
        int current = look(uuid);
        if (current < amount) {
            return false;
        }
        points.put(uuid, current - amount);
        return true;
    }

    @Override
    public boolean set(@NotNull UUID uuid, int amount) {
        points.put(uuid, amount);
        return true;
    }

    @Override
    public boolean pay(@NotNull UUID from, @NotNull UUID to, int amount) {
        int fromBalance = look(from);
        if (fromBalance < amount) {
            return false;
        }
        points.put(from, fromBalance - amount);
        points.merge(to, amount, Integer::sum);
        return true;
    }
}

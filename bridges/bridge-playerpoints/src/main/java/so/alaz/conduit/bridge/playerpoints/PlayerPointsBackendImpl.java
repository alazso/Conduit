package so.alaz.conduit.bridge.playerpoints;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Production {@link PlayerPointsBackend} backed by the live {@link PlayerPointsAPI}.
 *
 * <p><b>Account-existence caveat.</b> PlayerPoints exposes no existence check; every
 * UUID has an implicit zero balance and {@code look} cannot distinguish a missing
 * account from a real zero. This bridge therefore treats every account as existing
 * ({@link #hasAccount(UUID)} returns {@code true}), which matches PlayerPoints'
 * own behaviour: mutations succeed for any UUID and never resolve to
 * {@link so.alaz.conduit.api.result.EconomyResult.AccountNotFound}. {@code create}
 * is a no-op (rows materialise on first mutation) and {@code delete} resets the
 * balance to zero.
 */
public final class PlayerPointsBackendImpl implements PlayerPointsBackend {

    private final PlayerPointsAPI api;

    /**
     * @param api the PlayerPoints API instance
     */
    public PlayerPointsBackendImpl(@NotNull PlayerPointsAPI api) {
        this.api = api;
    }

    @Override
    public boolean hasAccount(@NotNull UUID uuid) {
        return true;
    }

    @Override
    public void ensureAccount(@NotNull UUID uuid) {
        // No-op: PlayerPoints accounts exist implicitly and materialise on first mutation.
    }

    @Override
    public boolean removeAccount(@NotNull UUID uuid) {
        return api.reset(uuid);
    }

    @Override
    public int look(@NotNull UUID uuid) {
        return api.look(uuid);
    }

    @Override
    public boolean give(@NotNull UUID uuid, int points) {
        return api.give(uuid, points);
    }

    @Override
    public boolean take(@NotNull UUID uuid, int points) {
        return api.take(uuid, points);
    }

    @Override
    public boolean set(@NotNull UUID uuid, int points) {
        return api.set(uuid, points);
    }

    @Override
    public boolean pay(@NotNull UUID from, @NotNull UUID to, int points) {
        return api.pay(from, to, points);
    }
}

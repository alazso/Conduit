package so.alaz.conduit.bridge.essentialsx;

import com.earth2me.essentials.api.Economy;
import net.ess3.api.IEssentials;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Production {@link EssentialsEconomyBackend} backed by the EssentialsX economy
 * API ({@link com.earth2me.essentials.api.Economy} UUID methods).
 *
 * <p>EssentialsX creates accounts on first join and has no first-class account
 * deletion, so {@link #ensureAccount(UUID)} and {@link #removeAccount(UUID)} are
 * best-effort. Checked EssentialsX exceptions are surfaced loudly as
 * {@link EssentialsBackendException} rather than swallowed.
 */
public final class EssentialsXBackend implements EssentialsEconomyBackend {

    private static final int DEFAULT_FRACTIONAL_DIGITS = 2;

    private final IEssentials essentials;

    public EssentialsXBackend(@NotNull IEssentials essentials) {
        this.essentials = essentials;
    }

    @Override
    public boolean hasAccount(@NotNull UUID uuid) {
        return Economy.playerExists(uuid);
    }

    @Override
    public void ensureAccount(@NotNull UUID uuid) {
        // EssentialsX provisions accounts on first join; nothing to do for an
        // already-known UUID, and unseen offline UUIDs cannot be created here.
    }

    @Override
    public void removeAccount(@NotNull UUID uuid) {
        // EssentialsX exposes no account deletion; intentionally a no-op.
    }

    @Override
    public @NotNull BigDecimal balance(@NotNull UUID uuid) {
        if (!Economy.playerExists(uuid)) {
            return BigDecimal.ZERO;
        }
        try {
            return Economy.getMoneyExact(uuid);
        } catch (Exception e) {
            throw new EssentialsBackendException("Failed to read EssentialsX balance for " + uuid, e);
        }
    }

    @Override
    public void setBalance(@NotNull UUID uuid, @NotNull BigDecimal balance) {
        try {
            Economy.setMoney(uuid, balance);
        } catch (Exception e) {
            throw new EssentialsBackendException("Failed to set EssentialsX balance for " + uuid, e);
        }
    }

    @Override
    public @NotNull String currencySymbol() {
        try {
            return essentials.getSettings().getCurrencySymbol();
        } catch (Exception e) {
            return "$";
        }
    }

    @Override
    public int fractionalDigits() {
        return DEFAULT_FRACTIONAL_DIGITS;
    }
}

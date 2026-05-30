package so.alaz.conduit.bridge.essentialsx;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Narrow seam over the EssentialsX economy. Isolating the EssentialsX static
 * API behind this interface lets the bridge translation logic be exercised by
 * a fast in-memory fake in tests while the production implementation talks to
 * EssentialsX.
 */
public interface EssentialsEconomyBackend {

    /**
     * @param uuid account owner
     * @return {@code true} if EssentialsX has a user account for the UUID
     */
    boolean hasAccount(@NotNull java.util.UUID uuid);

    /**
     * Ensure an account exists (best-effort; EssentialsX creates accounts on
     * first join, so this may be a no-op for unseen UUIDs).
     *
     * @param uuid account owner
     */
    void ensureAccount(@NotNull java.util.UUID uuid);

    /**
     * Remove an account (best-effort; EssentialsX has no first-class delete).
     *
     * @param uuid account owner
     */
    void removeAccount(@NotNull java.util.UUID uuid);

    /**
     * @param uuid account owner
     * @return the current balance, or {@link BigDecimal#ZERO} if unknown
     */
    @NotNull BigDecimal balance(@NotNull java.util.UUID uuid);

    /**
     * Set the absolute balance.
     *
     * @param uuid    account owner
     * @param balance the new balance
     */
    void setBalance(@NotNull java.util.UUID uuid, @NotNull BigDecimal balance);

    /**
     * @return the configured currency symbol
     */
    @NotNull String currencySymbol();

    /**
     * @return the number of fractional digits the currency supports
     */
    int fractionalDigits();
}

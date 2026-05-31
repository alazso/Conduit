package so.alaz.conduit.bridge.playerpoints;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Narrow seam over the PlayerPoints API. Isolating the PlayerPoints plugin
 * behind this interface lets the bridge translation logic be exercised by a fast
 * in-memory fake in tests while the production implementation talks to the live
 * {@code PlayerPointsAPI}.
 *
 * <p>PlayerPoints stores whole-number points keyed by UUID and has no first-class
 * account-existence concept: {@code look} returns {@code 0} for an unknown UUID,
 * indistinguishable from a real zero balance. {@link #hasAccount(UUID)} therefore
 * carries different semantics per implementation — see
 * {@link PlayerPointsBackendImpl} for the production caveat.
 */
public interface PlayerPointsBackend {

    /**
     * @param uuid account owner
     * @return {@code true} if the account is considered to exist
     */
    boolean hasAccount(@NotNull UUID uuid);

    /**
     * Ensure an account exists (best-effort; PlayerPoints materialises rows on
     * first mutation, so this may be a no-op).
     *
     * @param uuid account owner
     */
    void ensureAccount(@NotNull UUID uuid);

    /**
     * Remove an account.
     *
     * @param uuid account owner
     * @return {@code true} if an account was removed
     */
    boolean removeAccount(@NotNull UUID uuid);

    /**
     * @param uuid account owner
     * @return the current points balance, or {@code 0} if unknown
     */
    int look(@NotNull UUID uuid);

    /**
     * Add points to an account.
     *
     * @param uuid   account owner
     * @param points points to add (positive)
     * @return {@code true} if the change was applied (PlayerPoints may veto via event)
     */
    boolean give(@NotNull UUID uuid, int points);

    /**
     * Remove points from an account.
     *
     * @param uuid   account owner
     * @param points points to remove (positive)
     * @return {@code true} if the change was applied
     */
    boolean take(@NotNull UUID uuid, int points);

    /**
     * Set the absolute points balance.
     *
     * @param uuid   account owner
     * @param points the new balance
     * @return {@code true} if the change was applied
     */
    boolean set(@NotNull UUID uuid, int points);

    /**
     * Atomically transfer points between accounts (PlayerPoints rolls back the
     * debit if the credit fails).
     *
     * @param from   source account
     * @param to     destination account
     * @param points points to transfer (positive)
     * @return {@code true} if the transfer completed
     */
    boolean pay(@NotNull UUID from, @NotNull UUID to, int points);
}

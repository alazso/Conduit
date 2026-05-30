package so.alaz.conduit.api.capability;

import org.jetbrains.annotations.ApiStatus;

/**
 * Fine-grained feature flags declared by a provider within an interface it
 * already implements.
 *
 * <p><strong>These are not structural capabilities.</strong> Banking,
 * transaction history, and multi-currency are expressed through the
 * {@code BankingEconomy} / {@code TransactionalEconomy} /
 * {@code MultiCurrencyEconomy} extension interfaces — their presence in the
 * registry is the capability signal. There is deliberately no
 * {@code Capability.BANKING}, {@code Capability.MULTI_CURRENCY}, or
 * {@code Capability.TRANSACTION_HISTORY}; recreating the structural
 * decomposition as a runtime check is exactly the dual-surface pattern this
 * API exists to eliminate.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum Capability {
    /** The provider can act on accounts belonging to offline players. */
    ECONOMY_OFFLINE_PLAYERS,
    /** Balances support sub-integer (fractional) precision. */
    ECONOMY_FRACTIONAL_BALANCES,
    /** The provider implements {@code canDeposit} / {@code canWithdraw} pre-flight checks. */
    ECONOMY_PREFLIGHT
}

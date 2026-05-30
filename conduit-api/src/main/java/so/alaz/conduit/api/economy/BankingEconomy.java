package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.model.AccountPermission;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.api.result.OperationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy providers that support shared bank accounts.
 *
 * <p>Extends {@link Economy} so a single provider handles both player accounts
 * and banks — backend identity is structural.
 * {@code registry.getProvider(BankingEconomy.class)} returning empty is the
 * signal that the active economy does not support banks.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface BankingEconomy extends Economy {

    // --- Lifecycle ---

    /**
     * @param name  the bank name
     * @param owner the owning player's UUID
     * @return a future resolving the creation result
     */
    @NotNull CompletableFuture<EconomyResult> createBank(@NotNull String name, @NotNull UUID owner);

    /**
     * @param name the bank name
     * @return a future resolving the deletion result
     */
    @NotNull CompletableFuture<EconomyResult> deleteBank(@NotNull String name);

    /**
     * @return a future resolving all bank names
     */
    @NotNull CompletableFuture<List<String>> getBanks();

    // --- Balance ---

    /**
     * @param name the bank name
     * @return a future resolving the bank balance
     */
    @NotNull CompletableFuture<Balance> getBankBalance(@NotNull String name);

    /**
     * @param name      the bank name
     * @param depositor the player depositing
     * @param amount    the positive magnitude to deposit
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> bankDeposit(@NotNull String name, @NotNull UUID depositor, @NotNull BigDecimal amount);

    /**
     * @param name       the bank name
     * @param withdrawer the player withdrawing
     * @param amount     the positive magnitude to withdraw
     * @return a future resolving the result
     */
    @NotNull CompletableFuture<EconomyResult> bankWithdraw(@NotNull String name, @NotNull UUID withdrawer, @NotNull BigDecimal amount);

    // --- Membership (simple) ---

    /**
     * @param name the bank name
     * @param uuid the player UUID
     * @return a future resolving whether the player owns the bank
     */
    @NotNull CompletableFuture<Boolean> isBankOwner(@NotNull String name, @NotNull UUID uuid);

    /**
     * @param name the bank name
     * @param uuid the player UUID
     * @return a future resolving whether the player is a member of the bank
     */
    @NotNull CompletableFuture<Boolean> isBankMember(@NotNull String name, @NotNull UUID uuid);

    /**
     * @param name the bank name
     * @return a future resolving the bank's member UUIDs
     */
    @NotNull CompletableFuture<Set<UUID>> getBankMembers(@NotNull String name);

    // --- Granular AccountPermission membership ---

    /**
     * @param bank       the bank name
     * @param uuid       the player UUID
     * @param permission the permission to test
     * @return a future resolving whether the player has the permission
     */
    @NotNull CompletableFuture<Boolean> playerHasBankPermission(@NotNull String bank, @NotNull UUID uuid, @NotNull AccountPermission permission);

    /**
     * Grant or revoke a specific permission for a bank member. Granting
     * {@link AccountPermission#OWNER} implies all others; revoking
     * {@link AccountPermission#ALL} removes all non-owner entries.
     *
     * @param bank       the bank name
     * @param member     the member UUID
     * @param permission the permission to change
     * @param granted    {@code true} to grant, {@code false} to revoke
     * @return a future resolving the operation result
     */
    @NotNull CompletableFuture<OperationResult> setBankMemberPermission(
            @NotNull String bank, @NotNull UUID member, @NotNull AccountPermission permission, boolean granted);

    /**
     * @param bank the bank name
     * @param uuid the player UUID
     * @return a future resolving the effective permission set for the player
     */
    @NotNull CompletableFuture<Set<AccountPermission>> getBankMemberPermissions(@NotNull String bank, @NotNull UUID uuid);
}

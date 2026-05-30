package so.alaz.conduit.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared behavioural conformance suite every {@link Economy} provider must pass.
 *
 * <p>Scope note: this suite verifies provider <em>domain behaviour</em> (credits,
 * debits, atomic transfers, result shapes). Synchronous amount validation
 * (null/negative/zero magnitude, scale overflow) is enforced at the Conduit
 * dispatch boundary, not by individual providers, and is covered by the core
 * dispatcher tests — providers may assume amounts are already validated.
 */
public abstract class AbstractEconomyConformanceTest {

    private Economy economy;

    /**
     * @return a fresh provider instance with no pre-existing accounts
     */
    protected abstract Economy createEconomy();

    @BeforeEach
    void initProvider() {
        economy = createEconomy();
    }

    protected final Economy economy() {
        return economy;
    }

    protected final BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    protected final void fund(UUID uuid, String amount) {
        economy.createAccount(uuid).join();
        economy.set(uuid, money(amount)).join();
    }

    protected final BigDecimal balanceOf(UUID uuid) {
        return economy.getBalance(uuid).join().amount();
    }

    @Test
    void mutations_return_nonnull_futures() {
        UUID uuid = UUID.randomUUID();
        assertThat(economy.deposit(uuid, money("1.00"))).isNotNull();
        assertThat(economy.getBalance(uuid)).isNotNull();
    }

    @Test
    void fresh_account_has_zero_balance() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();
        assertThat(balanceOf(uuid)).isEqualByComparingTo("0");
    }

    @Test
    void hasAccount_reflects_creation() {
        UUID uuid = UUID.randomUUID();
        assertThat(economy.hasAccount(uuid).join()).isFalse();
        economy.createAccount(uuid).join();
        assertThat(economy.hasAccount(uuid).join()).isTrue();
    }

    @Test
    void deleteAccount_removes_the_account() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();
        economy.deleteAccount(uuid).join();
        assertThat(economy.hasAccount(uuid).join()).isFalse();
    }

    @Test
    void deposit_credits_account() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");
        EconomyResult result = economy.deposit(uuid, money("50.00")).join();
        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(balanceOf(uuid)).isEqualByComparingTo("50.00");
    }

    @Test
    void withdraw_debits_account() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "100.00");
        EconomyResult result = economy.withdraw(uuid, money("30.00")).join();
        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(balanceOf(uuid)).isEqualByComparingTo("70.00");
    }

    @Test
    void withdraw_when_account_is_empty_returns_InsufficientFunds() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");
        EconomyResult result = economy.withdraw(uuid, money("10.00")).join();
        assertThat(result).isInstanceOf(EconomyResult.InsufficientFunds.class);
    }

    @Test
    void insufficientFunds_includes_balance_and_requested() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "5.00");
        EconomyResult result = economy.withdraw(uuid, money("10.00")).join();
        assertThat(result).isInstanceOfSatisfying(EconomyResult.InsufficientFunds.class, funds -> {
            assertThat(funds.balance()).isEqualByComparingTo("5.00");
            assertThat(funds.requested()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void set_overwrites_balance() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "100.00");
        economy.set(uuid, money("25.00")).join();
        assertThat(balanceOf(uuid)).isEqualByComparingTo("25.00");
    }

    @Test
    void transfer_credits_destination_and_debits_source() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        fund(from, "100.00");
        fund(to, "0.00");

        EconomyResult result = economy.transfer(from, to, money("40.00")).join();

        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(balanceOf(from)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("40.00");
    }

    @Test
    void transfer_with_insufficient_source_does_not_debit() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        fund(from, "5.00");
        fund(to, "0.00");

        EconomyResult result = economy.transfer(from, to, money("40.00")).join();

        assertThat(result).isInstanceOf(EconomyResult.InsufficientFunds.class);
        assertThat(balanceOf(from)).isEqualByComparingTo("5.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void renameAccount_succeeds() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();
        EconomyResult result = economy.renameAccount(uuid, "Renamed").join();
        assertThat(result).isInstanceOf(EconomyResult.Success.class);
    }

    @Test
    void success_includes_newBalance_currency_and_transaction() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");
        EconomyResult result = economy.deposit(uuid, money("10.00")).join();
        assertThat(result).isInstanceOfSatisfying(EconomyResult.Success.class, success -> {
            assertThat(success.newBalance()).isEqualByComparingTo("10.00");
            assertThat(success.currency()).isEqualTo(economy.defaultCurrency());
            assertThat(success.transaction()).isNotNull();
        });
    }

    @Test
    void getBalances_returns_each_requested_account() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        fund(a, "10.00");
        fund(b, "20.00");

        Map<UUID, Balance> balances = economy.getBalances(List.of(a, b)).join();

        assertThat(balances).containsOnlyKeys(a, b);
        assertThat(balances.get(a).amount()).isEqualByComparingTo("10.00");
        assertThat(balances.get(b).amount()).isEqualByComparingTo("20.00");
    }
}

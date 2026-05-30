package so.alaz.conduit.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.BankingEconomy;
import so.alaz.conduit.api.model.AccountPermission;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance suite for {@link BankingEconomy} providers.
 */
public abstract class AbstractBankingEconomyConformanceTest {

    private BankingEconomy economy;

    /**
     * @return a fresh banking provider with no pre-existing banks
     */
    protected abstract BankingEconomy createBankingEconomy();

    @BeforeEach
    void initProvider() {
        economy = createBankingEconomy();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void createBank_then_listed_and_owned() {
        UUID owner = UUID.randomUUID();
        EconomyResult result = economy.createBank("spawn", owner).join();

        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(economy.getBanks().join()).contains("spawn");
        assertThat(economy.isBankOwner("spawn", owner).join()).isTrue();
        assertThat(economy.isBankMember("spawn", owner).join()).isTrue();
    }

    @Test
    void deleteBank_removes_it() {
        UUID owner = UUID.randomUUID();
        economy.createBank("temp", owner).join();
        economy.deleteBank("temp").join();
        assertThat(economy.getBanks().join()).doesNotContain("temp");
    }

    @Test
    void bankDeposit_and_withdraw_adjust_balance() {
        UUID owner = UUID.randomUUID();
        economy.createBank("vault", owner).join();

        economy.bankDeposit("vault", owner, money("100.00")).join();
        assertThat(economy.getBankBalance("vault").join().amount()).isEqualByComparingTo("100.00");

        EconomyResult withdrawal = economy.bankWithdraw("vault", owner, money("40.00")).join();
        assertThat(withdrawal).isInstanceOf(EconomyResult.Success.class);
        assertThat(economy.getBankBalance("vault").join().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void bankWithdraw_beyond_balance_returns_InsufficientFunds() {
        UUID owner = UUID.randomUUID();
        economy.createBank("vault", owner).join();
        economy.bankDeposit("vault", owner, money("10.00")).join();

        EconomyResult result = economy.bankWithdraw("vault", owner, money("50.00")).join();
        assertThat(result).isInstanceOf(EconomyResult.InsufficientFunds.class);
    }

    @Test
    void owner_has_all_permissions_via_OWNER() {
        UUID owner = UUID.randomUUID();
        economy.createBank("vault", owner).join();

        assertThat(economy.playerHasBankPermission("vault", owner, AccountPermission.WITHDRAW).join()).isTrue();
        assertThat(economy.playerHasBankPermission("vault", owner, AccountPermission.DEPOSIT).join()).isTrue();
    }

    @Test
    void member_permissions_can_be_granted_and_revoked() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        economy.createBank("vault", owner).join();

        economy.setBankMemberPermission("vault", member, AccountPermission.DEPOSIT, true).join();
        assertThat(economy.playerHasBankPermission("vault", member, AccountPermission.DEPOSIT).join()).isTrue();
        assertThat(economy.getBankMemberPermissions("vault", member).join()).contains(AccountPermission.DEPOSIT);
        assertThat(economy.getBankMembers("vault").join()).contains(member);

        economy.setBankMemberPermission("vault", member, AccountPermission.DEPOSIT, false).join();
        assertThat(economy.playerHasBankPermission("vault", member, AccountPermission.DEPOSIT).join()).isFalse();
    }

    @Test
    void granting_OWNER_implies_every_permission() {
        UUID owner = UUID.randomUUID();
        UUID coOwner = UUID.randomUUID();
        economy.createBank("vault", owner).join();

        economy.setBankMemberPermission("vault", coOwner, AccountPermission.OWNER, true).join();

        assertThat(economy.playerHasBankPermission("vault", coOwner, AccountPermission.DEPOSIT).join()).isTrue();
        assertThat(economy.playerHasBankPermission("vault", coOwner, AccountPermission.WITHDRAW).join()).isTrue();
    }

    @Test
    void revoking_ALL_clears_non_owner_permissions() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        economy.createBank("vault", owner).join();
        economy.setBankMemberPermission("vault", member, AccountPermission.DEPOSIT, true).join();
        economy.setBankMemberPermission("vault", member, AccountPermission.WITHDRAW, true).join();

        economy.setBankMemberPermission("vault", member, AccountPermission.ALL, false).join();

        assertThat(economy.playerHasBankPermission("vault", member, AccountPermission.DEPOSIT).join()).isFalse();
        assertThat(economy.playerHasBankPermission("vault", member, AccountPermission.WITHDRAW).join()).isFalse();
    }

    @Test
    void deleting_a_nonexistent_bank_does_not_throw() {
        EconomyResult result = economy.deleteBank("ghost").join();
        assertThat(result).isInstanceOf(EconomyResult.ProviderError.class);
    }
}

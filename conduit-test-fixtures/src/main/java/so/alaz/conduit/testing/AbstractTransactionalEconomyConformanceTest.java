package so.alaz.conduit.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.TransactionalEconomy;
import so.alaz.conduit.api.exception.IdempotencyMismatchException;
import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conformance suite for {@link TransactionalEconomy} providers, focused on the
 * normative idempotency contract and transaction history.
 */
public abstract class AbstractTransactionalEconomyConformanceTest {

    private TransactionalEconomy economy;

    /**
     * @return a fresh transactional provider with no pre-existing accounts
     */
    protected abstract TransactionalEconomy createTransactionalEconomy();

    @BeforeEach
    void initProvider() {
        economy = createTransactionalEconomy();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private void fund(UUID uuid, String amount) {
        economy.createAccount(uuid).join();
        economy.set(uuid, money(amount)).join();
    }

    @Test
    void deposit_is_recorded_in_history() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");
        economy.deposit(uuid, money("10.00")).join();

        List<Transaction> history = economy.getTransactionHistory(uuid, 10).join();
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void same_id_same_params_returns_original_without_reexecuting() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");
        UUID op = UUID.randomUUID();

        EconomyResult first = economy.depositIdempotent(uuid, money("10.00"), op).join();
        EconomyResult second = economy.depositIdempotent(uuid, money("10.00"), op).join();

        assertThat(first).isInstanceOf(EconomyResult.Success.class);
        assertThat(second).isInstanceOf(EconomyResult.Success.class);
        // Not re-executed: balance reflects a single deposit.
        assertThat(economy.getBalance(uuid).join().amount()).isEqualByComparingTo("10.00");

        EconomyResult.Success s1 = (EconomyResult.Success) first;
        EconomyResult.Success s2 = (EconomyResult.Success) second;
        assertThat(s2.newBalance()).isEqualByComparingTo(s1.newBalance());
        assertThat(s2.transaction()).isEqualTo(s1.transaction());
    }

    @Test
    void same_id_different_params_fails_with_mismatch() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");
        UUID op = UUID.randomUUID();

        economy.depositIdempotent(uuid, money("10.00"), op).join();

        assertThatThrownBy(() -> economy.depositIdempotent(uuid, money("99.00"), op).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IdempotencyMismatchException.class);
    }

    @Test
    void distinct_ids_execute_independently() {
        UUID uuid = UUID.randomUUID();
        fund(uuid, "0.00");

        economy.depositIdempotent(uuid, money("10.00"), UUID.randomUUID()).join();
        economy.depositIdempotent(uuid, money("10.00"), UUID.randomUUID()).join();

        assertThat(economy.getBalance(uuid).join().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void same_operationId_on_different_accounts_executes_independently() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        fund(a, "0.00");
        fund(b, "0.00");
        UUID op = UUID.randomUUID();

        // The contract scopes operationId uniqueness to the primary account, so
        // the same id on a different account must NOT be treated as a replay.
        EconomyResult first = economy.depositIdempotent(a, money("10.00"), op).join();
        EconomyResult second = economy.depositIdempotent(b, money("10.00"), op).join();

        assertThat(first).isInstanceOf(EconomyResult.Success.class);
        assertThat(second).isInstanceOf(EconomyResult.Success.class);
        assertThat(economy.getBalance(a).join().amount()).isEqualByComparingTo("10.00");
        assertThat(economy.getBalance(b).join().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void transferIdempotent_does_not_double_apply_on_replay() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        fund(from, "100.00");
        fund(to, "0.00");
        UUID op = UUID.randomUUID();

        economy.transferIdempotent(from, to, money("30.00"), op).join();
        economy.transferIdempotent(from, to, money("30.00"), op).join();

        assertThat(economy.getBalance(from).join().amount()).isEqualByComparingTo("70.00");
        assertThat(economy.getBalance(to).join().amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void transferIdempotent_distinct_ids_apply_each_time() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        fund(from, "100.00");
        fund(to, "0.00");

        economy.transferIdempotent(from, to, money("30.00"), UUID.randomUUID()).join();
        economy.transferIdempotent(from, to, money("30.00"), UUID.randomUUID()).join();

        assertThat(economy.getBalance(from).join().amount()).isEqualByComparingTo("40.00");
        assertThat(economy.getBalance(to).join().amount()).isEqualByComparingTo("60.00");
    }
}

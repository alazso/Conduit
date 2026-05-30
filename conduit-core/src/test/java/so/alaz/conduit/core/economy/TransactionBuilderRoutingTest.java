package so.alaz.conduit.core.economy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.event.EconomyTransactionEvent;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.testing.MockEconomy;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@code DefaultTransactionBuilder} (obtained via
 * {@link Economy#transaction()}) through the dispatcher, validating that the
 * builder's refinements are honoured rather than silently dropped.
 */
class TransactionBuilderRoutingTest {

    private static final Currency GEMS = new SimpleCurrency("gems", "gem", "gems", "G", 0, false);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private RecordingEventPublisher events;
    private Economy economy;

    @BeforeEach
    void setUp() {
        MockEconomy backing = MockEconomy.builder()
                .withCurrency("coins", "$", 2)
                .withCurrency(GEMS)
                .withAccount(alice, new BigDecimal("100.00"))
                .withAccount(bob, new BigDecimal("0.00"))
                .build();
        events = new RecordingEventPublisher();
        economy = new EconomyDispatcher(backing, new InterceptorBus(), events);
    }

    @Test
    void metadata_is_carried_into_the_published_event() {
        economy.transaction()
                .deposit(alice, new BigDecimal("5.00"))
                .metadata("source", "quest")
                .execute()
                .join();

        EconomyTransactionEvent event = events.ofType(EconomyTransactionEvent.class).get(0);
        assertThat(event.getTransaction().metadata()).containsEntry("source", "quest");
    }

    @Test
    void currency_routes_to_the_multi_currency_overload() {
        economy.transaction()
                .deposit(alice, new BigDecimal("7"))
                .currency(GEMS)
                .execute()
                .join();

        assertThat(((EconomyDispatcher) economy).getBalance(alice, GEMS).join().amount()).isEqualByComparingTo("7");
    }

    @Test
    void idempotencyKey_routes_to_the_idempotent_overload() {
        UUID op = UUID.randomUUID();
        economy.transaction().deposit(alice, new BigDecimal("10.00")).idempotencyKey(op).execute().join();
        economy.transaction().deposit(alice, new BigDecimal("10.00")).idempotencyKey(op).execute().join();

        // Replayed under the same key: only applied once.
        assertThat(economy.getBalance(alice).join().amount()).isEqualByComparingTo("110.00");
    }

    @Test
    void combining_currency_and_idempotencyKey_fails_fast() {
        assertThatThrownBy(() -> economy.transaction()
                .deposit(alice, new BigDecimal("1"))
                .currency(GEMS)
                .idempotencyKey(UUID.randomUUID())
                .execute())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void execute_without_an_operation_fails_fast() {
        assertThatThrownBy(() -> economy.transaction().execute())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transfer_through_builder_succeeds() {
        EconomyResult result = economy.transaction()
                .transfer(alice, bob, new BigDecimal("20.00"))
                .reason("gift")
                .execute()
                .join();
        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(economy.getBalance(bob).join().amount()).isEqualByComparingTo("20.00");
    }
}

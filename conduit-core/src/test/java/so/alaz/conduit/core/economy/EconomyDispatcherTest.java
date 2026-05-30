package so.alaz.conduit.core.economy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.event.EconomyAccountEvent;
import so.alaz.conduit.api.event.EconomyTransactionEvent;
import so.alaz.conduit.api.model.AccountEventType;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.core.support.TestPlugins;
import so.alaz.conduit.testing.MockEconomy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.bukkit.plugin.ServicePriority;

class EconomyDispatcherTest {

    private final UUID alice = UUID.randomUUID();

    private MockEconomy backing;
    private RecordingEventPublisher events;
    private InterceptorBus interceptors;
    private EconomyDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        backing = MockEconomy.builder()
                .withCurrency("coins", "$", 2)
                .withAccount(alice, new BigDecimal("100.00"))
                .build();
        events = new RecordingEventPublisher();
        interceptors = new InterceptorBus();
        dispatcher = new EconomyDispatcher(backing, interceptors, events);
    }

    @Test
    void negative_magnitude_throws_synchronously() {
        assertThatThrownBy(() -> dispatcher.deposit(alice, new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero_magnitude_throws_synchronously() {
        assertThatThrownBy(() -> dispatcher.withdraw(alice, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void over_scaled_amount_throws_synchronously() {
        assertThatThrownBy(() -> dispatcher.deposit(alice, new BigDecimal("1.234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
    }

    @Test
    void set_accepts_zero_but_rejects_negative() {
        EconomyResult zero = dispatcher.set(alice, BigDecimal.ZERO).join();
        assertThat(zero).isInstanceOf(EconomyResult.Success.class);

        assertThatThrownBy(() -> dispatcher.set(alice, new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successful_deposit_publishes_transaction_event() {
        EconomyResult result = dispatcher.deposit(alice, new BigDecimal("25.00")).join();

        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(backing.getBalance(alice).join().amount()).isEqualByComparingTo("125.00");

        List<EconomyTransactionEvent> published = events.ofType(EconomyTransactionEvent.class);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getTransaction().amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void interceptor_veto_aborts_with_Rejected_and_publishes_no_event() {
        interceptors.register(ctx -> false, TestPlugins.named("Guard"), ServicePriority.Normal);

        EconomyResult result = dispatcher.withdraw(alice, new BigDecimal("10.00")).join();

        // A policy veto is a Rejected, not a ProviderError (the backend was never asked).
        assertThat(result).isInstanceOf(EconomyResult.Rejected.class);
        assertThat(((EconomyResult.Rejected) result).reason()).contains("interceptor");
        // Balance untouched; no event fired.
        assertThat(backing.getBalance(alice).join().amount()).isEqualByComparingTo("100.00");
        assertThat(events.ofType(EconomyTransactionEvent.class)).isEmpty();
    }

    @Test
    void caller_token_is_attributed_to_published_event() {
        CallerToken token = CallerToken.create(TestPlugins.named("Shop"), "Shop");

        CallerToken.runWith(token, () -> dispatcher.deposit(alice, new BigDecimal("5.00")).join());

        List<EconomyTransactionEvent> published = events.ofType(EconomyTransactionEvent.class);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getCaller()).isSameAs(token);
    }

    @Test
    void create_and_delete_account_publish_account_events() {
        UUID bob = UUID.randomUUID();
        dispatcher.createAccount(bob).join();
        dispatcher.deleteAccount(bob).join();

        List<EconomyAccountEvent> published = events.ofType(EconomyAccountEvent.class);
        assertThat(published).hasSize(2);
        assertThat(published.get(0).getType()).isEqualTo(AccountEventType.CREATED);
        assertThat(published.get(1).getType()).isEqualTo(AccountEventType.DELETED);
    }

    @Test
    void dispatcher_exposes_unwrapped_delegate() {
        Economy delegate = dispatcher.delegate();
        assertThat(delegate).isSameAs(backing);
    }
}

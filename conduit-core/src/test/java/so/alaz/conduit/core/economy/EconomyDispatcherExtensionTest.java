package so.alaz.conduit.core.economy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.economy.TransactionContext;
import so.alaz.conduit.api.event.EconomyAccountEvent;
import so.alaz.conduit.api.event.EconomyTransactionEvent;
import so.alaz.conduit.api.exception.CapabilityNotSupportedException;
import so.alaz.conduit.api.model.AccountEventType;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.testing.MockEconomy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the dispatcher's totality over the extension interfaces, the two-leg
 * transfer events, capability enforcement, and metadata propagation.
 */
class EconomyDispatcherExtensionTest {

    private static final Currency GEMS = new SimpleCurrency("gems", "gem", "gems", "G", 0, false);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private MockEconomy backing;
    private RecordingEventPublisher events;
    private EconomyDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        backing = MockEconomy.builder()
                .withCurrency("coins", "$", 2)
                .withCurrency(GEMS)
                .withAccount(alice, new BigDecimal("100.00"))
                .withAccount(bob, new BigDecimal("0.00"))
                .build();
        events = new RecordingEventPublisher();
        dispatcher = new EconomyDispatcher(backing, new InterceptorBus(), events);
    }

    @Test
    void transfer_publishes_both_out_and_in_events() {
        dispatcher.transfer(alice, bob, new BigDecimal("40.00")).join();

        List<EconomyTransactionEvent> published = events.ofType(EconomyTransactionEvent.class);
        assertThat(published).hasSize(2);
        assertThat(published).anyMatch(e -> e.getTransaction().type() == TransactionType.TRANSFER_OUT
                && e.getTransaction().target().equals(alice));
        assertThat(published).anyMatch(e -> e.getTransaction().type() == TransactionType.TRANSFER_IN
                && e.getTransaction().target().equals(bob));
    }

    @Test
    void transfer_in_event_carries_real_recipient_balance() {
        dispatcher.transfer(alice, bob, new BigDecimal("40.00")).join();

        EconomyTransactionEvent in = events.ofType(EconomyTransactionEvent.class).stream()
                .filter(e -> e.getTransaction().type() == TransactionType.TRANSFER_IN)
                .findFirst().orElseThrow();
        assertThat(in.getTransaction().balanceAfter()).isEqualByComparingTo("40.00");
        assertThat(in.getTransaction().balanceBefore()).isEqualByComparingTo("0.00");
    }

    @Test
    void multi_currency_deposit_routes_through_dispatch_and_validates_scale() {
        dispatcher.deposit(alice, new BigDecimal("5"), GEMS).join();
        assertThat(backing.getBalance(alice, GEMS).join().amount()).isEqualByComparingTo("5");

        // GEMS has 0 decimal places: a fractional amount must be rejected synchronously.
        assertThatThrownBy(() -> dispatcher.deposit(alice, new BigDecimal("1.5"), GEMS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void banking_deposit_routes_through_dispatch_and_emits_event() {
        dispatcher.createBank("vault", alice).join();
        dispatcher.bankDeposit("vault", alice, new BigDecimal("50.00")).join();

        assertThat(dispatcher.getBankBalance("vault").join().amount()).isEqualByComparingTo("50.00");
        assertThat(events.ofType(EconomyTransactionEvent.class))
                .anyMatch(e -> e.getTransaction().type() == TransactionType.BANK_DEPOSIT);
    }

    @Test
    void idempotent_deposit_does_not_publish_transaction_event() {
        dispatcher.depositIdempotent(alice, new BigDecimal("10.00"), UUID.randomUUID()).join();
        assertThat(events.ofType(EconomyTransactionEvent.class)).isEmpty();
    }

    @Test
    void renameAccount_publishes_renamed_event() {
        dispatcher.renameAccount(alice, "Alice2").join();
        assertThat(events.ofType(EconomyAccountEvent.class))
                .anyMatch(e -> e.getType() == AccountEventType.RENAMED);
    }

    @Test
    void preflight_throws_when_capability_absent() {
        MockEconomy noPreflight = MockEconomy.builder()
                .withCurrency("coins", "$", 2)
                .withCapabilities(EnumSet.of(Capability.ECONOMY_OFFLINE_PLAYERS))
                .build();
        EconomyDispatcher d = new EconomyDispatcher(noPreflight, new InterceptorBus(), events);

        assertThatThrownBy(() -> d.canDeposit(alice, new BigDecimal("1.00")))
                .isInstanceOf(CapabilityNotSupportedException.class);
    }

    @Test
    void preflight_allowed_when_capability_present() {
        // MockEconomy declares all capabilities by default.
        assertThat(dispatcher.canWithdraw(alice, new BigDecimal("1.00")).join()).isTrue();
    }

    @Test
    void unsupported_extension_interface_fails_loudly() {
        Economy plain = new PlainEconomy();
        EconomyDispatcher d = new EconomyDispatcher(plain, new InterceptorBus(), events);
        assertThatThrownBy(d::supportedCurrencies)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("MultiCurrencyEconomy");
    }

    @Test
    void builder_metadata_is_carried_into_published_event() {
        TransactionContext.supplyWith(Map.of("source", "quest-reward"),
                () -> dispatcher.deposit(alice, new BigDecimal("5.00"))).join();

        EconomyTransactionEvent event = events.ofType(EconomyTransactionEvent.class).get(0);
        assertThat(event.getTransaction().metadata()).containsEntry("source", "quest-reward");
    }

    /** A minimal base-only economy used to exercise the "unsupported interface" guard. */
    private static final class PlainEconomy implements Economy {
        private final Currency currency = SimpleCurrency.ofDefault("coins", "$", 2);

        @Override public String getName() { return "Plain"; }
        @Override public Currency defaultCurrency() { return currency; }
        @Override public java.util.Set<Capability> capabilities() { return java.util.Set.of(); }
        @Override public java.util.concurrent.CompletableFuture<Boolean> hasAccount(UUID uuid) { return done(false); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> createAccount(UUID uuid) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> deleteAccount(UUID uuid) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> renameAccount(UUID uuid, String n) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Set<UUID>> accountsWithOwnerOf(UUID uuid) { return done(java.util.Set.of()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Set<UUID>> accountsWithMembershipTo(UUID uuid) { return done(java.util.Set.of()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Set<UUID>> accountsWithAccessTo(UUID uuid) { return done(java.util.Set.of()); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.model.Balance> getBalance(UUID uuid) {
            return done(new so.alaz.conduit.api.model.Balance(uuid, currency, BigDecimal.ZERO));
        }
        @Override public java.util.concurrent.CompletableFuture<Boolean> canDeposit(UUID uuid, BigDecimal a) { return done(true); }
        @Override public java.util.concurrent.CompletableFuture<Boolean> canWithdraw(UUID uuid, BigDecimal a) { return done(true); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> deposit(UUID uuid, BigDecimal a) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> deposit(UUID uuid, BigDecimal a, String r) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> withdraw(UUID uuid, BigDecimal a) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> withdraw(UUID uuid, BigDecimal a, String r) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> set(UUID uuid, BigDecimal a) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> transfer(UUID from, UUID to, BigDecimal a) { return done(ok()); }
        @Override public java.util.concurrent.CompletableFuture<EconomyResult> transfer(UUID from, UUID to, BigDecimal a, String r) { return done(ok()); }

        private EconomyResult ok() { return new EconomyResult.Success(new UUID(0, 0), currency, BigDecimal.ZERO, null); }
        private static <T> java.util.concurrent.CompletableFuture<T> done(T v) { return java.util.concurrent.CompletableFuture.completedFuture(v); }
    }
}

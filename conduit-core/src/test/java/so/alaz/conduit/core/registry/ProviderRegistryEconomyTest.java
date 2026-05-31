package so.alaz.conduit.core.registry;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.BankingEconomy;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.economy.LeaderboardEconomy;
import so.alaz.conduit.api.economy.MultiCurrencyEconomy;
import so.alaz.conduit.api.economy.TransactionalEconomy;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.core.economy.DispatchInvocationHandler;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.core.support.TestPlugins;
import so.alaz.conduit.testing.MockEconomy;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRegistryEconomyTest {

    private ProviderRegistryImpl registry;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        registry = new ProviderRegistryImpl(new RecordingEventPublisher(), new InterceptorBus());
        plugin = TestPlugins.named("Test");
    }

    private MockEconomy mock(String name) {
        return MockEconomy.builder().name(name).withCurrency("coins", "$", 2).build();
    }

    @Test
    void economy_resolution_returns_a_dispatcher_with_stable_identity() {
        registry.register(Economy.class, mock("A"), plugin, ServicePriority.Normal);

        Economy first = registry.requireProvider(Economy.class);
        Economy second = registry.requireProvider(Economy.class);

        assertThat(DispatchInvocationHandler.isDecorated(first)).isTrue();
        // Memoised: the same delegate always yields the same decorated instance.
        assertThat(first).isSameAs(second);
    }

    @Test
    void registered_view_resolves_to_a_total_dispatcher() {
        MockEconomy provider = mock("Bank");
        registry.register(BankingEconomy.class, provider, plugin, ServicePriority.Normal);

        BankingEconomy banking = registry.requireProvider(BankingEconomy.class);
        Economy base = registry.requireProvider(Economy.class);

        assertThat(DispatchInvocationHandler.isDecorated(banking)).isTrue();
        assertThat(DispatchInvocationHandler.unwrap(base)).isSameAs(provider);
        // Base and banking views are the same memoised handle (stable identity).
        assertThat(base).isSameAs(banking);
        // The handle is total: although the provider was registered only as a
        // BankingEconomy, the returned handle can be used as every economy
        // interface the delegate actually implements.
        assertThat(banking).isInstanceOf(MultiCurrencyEconomy.class);
        assertThat(((MultiCurrencyEconomy) banking).supportedCurrencies()).isNotEmpty();
    }

    @Test
    void extension_view_absent_when_provider_registered_only_as_base() {
        registry.register(Economy.class, mock("Plainish"), plugin, ServicePriority.Normal);

        // Registered as Economy: resolution is by registered type, so the bank
        // view must not resolve even though MockEconomy implements BankingEconomy.
        assertThat(registry.getProvider(BankingEconomy.class)).isEmpty();
    }

    @Test
    void decorated_handle_does_not_lie_about_unsupported_interfaces() {
        // A genuinely base-only provider: the decorated handle must report the
        // extension interfaces as absent (instanceof is truthful), not always-true.
        registry.register(Economy.class, new VersionedEconomy("BaseOnly", "1.0"), plugin, ServicePriority.Normal);

        Economy handle = registry.requireProvider(Economy.class);

        assertThat(handle).isNotInstanceOf(MultiCurrencyEconomy.class);
        assertThat(handle).isNotInstanceOf(TransactionalEconomy.class);
        assertThat(handle).isNotInstanceOf(BankingEconomy.class);
        assertThat(handle).isNotInstanceOf(LeaderboardEconomy.class);
    }

    @Test
    void builder_currency_refinement_fails_fast_on_single_currency_provider() {
        registry.register(Economy.class, new VersionedEconomy("BaseOnly", "1.0"), plugin, ServicePriority.Normal);
        Economy handle = registry.requireProvider(Economy.class);

        // The builder is bound to the decorated handle; because the handle is
        // honestly not a MultiCurrencyEconomy, the guard fires the friendly
        // IllegalStateException rather than a confusing UnsupportedOperationException.
        assertThatThrownBy(() -> handle.transaction()
                .deposit(UUID.randomUUID(), new java.math.BigDecimal("1.00"))
                .currency(SimpleCurrency.ofDefault("eur", "€", 2))
                .execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MultiCurrencyEconomy");
    }

    @Test
    void provider_requiring_newer_api_is_rejected_at_registration() {
        Economy future = new VersionedEconomy("Future", "2.0");

        assertThatThrownBy(() -> registry.register(Economy.class, future, plugin, ServicePriority.Normal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires Conduit API");
    }

    @Test
    void provider_requiring_older_api_is_accepted() {
        Economy legacy = new VersionedEconomy("Legacy", "0.9");
        registry.register(Economy.class, legacy, plugin, ServicePriority.Normal);
        assertThat(registry.getProvider(Economy.class)).isPresent();
    }

    @Test
    void provider_override_wins_regardless_of_priority() {
        MockEconomy high = mock("HighPriority");
        MockEconomy preferred = mock("Preferred");
        registry.register(Economy.class, high, plugin, ServicePriority.Highest);
        registry.register(Economy.class, preferred, plugin, ServicePriority.Low);

        registry.setEconomyProviderOverride("Preferred");

        Economy active = registry.requireProvider(Economy.class);
        assertThat(DispatchInvocationHandler.unwrap(active)).isSameAs(preferred);

        // Clearing the override restores priority-based selection.
        registry.setEconomyProviderOverride(null);
        assertThat(DispatchInvocationHandler.unwrap(registry.requireProvider(Economy.class))).isSameAs(high);
    }

    /** Minimal economy whose required API version is configurable. */
    private static final class VersionedEconomy implements Economy {
        private final String name;
        private final String apiVersion;
        private final so.alaz.conduit.api.model.Currency currency =
                so.alaz.conduit.api.model.SimpleCurrency.ofDefault("coins", "$", 2);

        VersionedEconomy(String name, String apiVersion) {
            this.name = name;
            this.apiVersion = apiVersion;
        }

        @Override public String getName() { return name; }
        @Override public String requiredApiVersion() { return apiVersion; }
        @Override public so.alaz.conduit.api.model.Currency defaultCurrency() { return currency; }
        @Override public java.util.Set<so.alaz.conduit.api.capability.Capability> capabilities() { return java.util.Set.of(); }
        @Override public java.util.concurrent.CompletableFuture<Boolean> hasAccount(java.util.UUID u) { return done(false); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> createAccount(java.util.UUID u) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> deleteAccount(java.util.UUID u) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> renameAccount(java.util.UUID u, String n) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Set<java.util.UUID>> accountsWithOwnerOf(java.util.UUID u) { return done(java.util.Set.of()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Set<java.util.UUID>> accountsWithMembershipTo(java.util.UUID u) { return done(java.util.Set.of()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Set<java.util.UUID>> accountsWithAccessTo(java.util.UUID u) { return done(java.util.Set.of()); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.model.Balance> getBalance(java.util.UUID u) {
            return done(new so.alaz.conduit.api.model.Balance(u, currency, java.math.BigDecimal.ZERO));
        }
        @Override public java.util.concurrent.CompletableFuture<Boolean> canDeposit(java.util.UUID u, java.math.BigDecimal a) { return done(true); }
        @Override public java.util.concurrent.CompletableFuture<Boolean> canWithdraw(java.util.UUID u, java.math.BigDecimal a) { return done(true); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> deposit(java.util.UUID u, java.math.BigDecimal a) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> deposit(java.util.UUID u, java.math.BigDecimal a, String r) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> withdraw(java.util.UUID u, java.math.BigDecimal a) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> withdraw(java.util.UUID u, java.math.BigDecimal a, String r) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> set(java.util.UUID u, java.math.BigDecimal a) { return done(ok(u)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> transfer(java.util.UUID f, java.util.UUID t, java.math.BigDecimal a) { return done(ok(f)); }
        @Override public java.util.concurrent.CompletableFuture<so.alaz.conduit.api.result.EconomyResult> transfer(java.util.UUID f, java.util.UUID t, java.math.BigDecimal a, String r) { return done(ok(f)); }

        private so.alaz.conduit.api.result.EconomyResult ok(java.util.UUID u) {
            return new so.alaz.conduit.api.result.EconomyResult.Success(u, currency, java.math.BigDecimal.ZERO, null);
        }
        private static <T> java.util.concurrent.CompletableFuture<T> done(T v) { return java.util.concurrent.CompletableFuture.completedFuture(v); }
    }
}

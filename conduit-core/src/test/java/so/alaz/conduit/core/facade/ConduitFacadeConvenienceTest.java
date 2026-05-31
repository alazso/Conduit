package so.alaz.conduit.core.facade;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.exception.ProviderNotFoundException;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.registry.ProviderRegistryImpl;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.core.support.TestPlugins;
import so.alaz.conduit.testing.MockEconomy;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage for the experimental {@link Conduit} convenience surface,
 * exercised through a real {@link ProviderRegistryImpl} (so the dispatch-decorated
 * handle, with its synchronous amount validation, is on the path). The facade
 * lives in {@code conduit-api}, but it can only be meaningfully tested where a
 * registry and {@link MockEconomy} coexist — i.e. {@code conduit-core} test scope.
 * Coverage of the api class does not affect core's JaCoCo gate, which measures
 * core's own compiled classes only.
 */
class ConduitFacadeConvenienceTest {

    private ProviderRegistryImpl registry;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        registry = new ProviderRegistryImpl(new RecordingEventPublisher(), new InterceptorBus());
        plugin = TestPlugins.named("Test");
        Conduit.init(registry);
    }

    @AfterEach
    void tearDown() {
        // Critical: clear the static volatile registry so tests do not leak state.
        Conduit.shutdown();
    }

    private void registerEconomy() {
        registry.register(Economy.class,
                MockEconomy.builder().name("Mock").withCurrency("coins", "$", 2).build(),
                plugin, ServicePriority.Normal);
    }

    @Test
    void deposit_uuid_bigdecimal_commits_and_reports_new_balance() {
        registerEconomy();
        UUID account = UUID.randomUUID();

        EconomyResult result = Conduit.deposit(account, new BigDecimal("100.00"), "reward").join();

        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(result.committedBalance()).hasValueSatisfying(b ->
                assertThat(b).isEqualByComparingTo("100.00"));
    }

    @Test
    void long_overload_matches_bigdecimal_overload() {
        registerEconomy();
        UUID viaLong = UUID.randomUUID();
        UUID viaDecimal = UUID.randomUUID();

        BigDecimal longBalance = Conduit.deposit(viaLong, 100L, "reward").join().committedBalance().orElseThrow();
        BigDecimal decimalBalance =
                Conduit.deposit(viaDecimal, BigDecimal.valueOf(100), "reward").join().committedBalance().orElseThrow();

        assertThat(longBalance).isEqualByComparingTo(decimalBalance);
    }

    @Test
    void offlinePlayer_overload_routes_to_player_uuid() {
        registerEconomy();
        UUID id = UUID.randomUUID();
        OfflinePlayer player = offlinePlayer(id);

        Conduit.deposit(player, 50L, "reward").join();

        // The credit must land on the player's UUID, observable via the UUID query.
        assertThat(Conduit.balance(id).join().amount()).isEqualByComparingTo("50");
    }

    @Test
    void transfer_debits_source_and_credits_destination() {
        registerEconomy();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Conduit.deposit(from, 100L, "seed").join();

        EconomyResult result = Conduit.transfer(from, to, new BigDecimal("30.00"), "gift").join();

        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(Conduit.balance(from).join().amount()).isEqualByComparingTo("70.00");
        assertThat(Conduit.balance(to).join().amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void transfer_without_funds_resolves_to_insufficient_funds_not_exception() {
        registerEconomy();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        EconomyResult result = Conduit.transfer(from, to, new BigDecimal("25.00"), "gift").join();

        assertThat(result).isInstanceOf(EconomyResult.InsufficientFunds.class);
    }

    @Test
    void withdraw_commits_against_funded_account() {
        registerEconomy();
        UUID account = UUID.randomUUID();
        Conduit.deposit(account, 100L, "seed").join();

        EconomyResult result = Conduit.withdraw(account, new BigDecimal("40.00"), "fee").join();

        assertThat(result).isInstanceOf(EconomyResult.Success.class);
        assertThat(Conduit.balance(account).join().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void balance_returns_expected_amount() {
        registerEconomy();
        UUID account = UUID.randomUUID();
        Conduit.deposit(account, new BigDecimal("12.50"), "seed").join();

        assertThat(Conduit.balance(account).join().amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void no_provider_mutation_completes_softly_with_provider_error() {
        // No economy registered in this fresh registry.
        UUID account = UUID.randomUUID();

        EconomyResult result = Conduit.deposit(account, new BigDecimal("5.00"), "reward").join();

        assertThat(result).isInstanceOf(EconomyResult.ProviderError.class);
        assertThat(((EconomyResult.ProviderError) result).cause()).isNull();

        // A beginner's thenAccept still runs because the future is completed, not failed.
        AtomicBoolean ran = new AtomicBoolean(false);
        Conduit.deposit(account, new BigDecimal("5.00"), "reward").thenAccept(r -> ran.set(true)).join();
        assertThat(ran).isTrue();
    }

    @Test
    void no_provider_balance_completes_exceptionally_with_provider_not_found() {
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() -> Conduit.balance(account).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ProviderNotFoundException.class);

        AtomicBoolean handled = new AtomicBoolean(false);
        Conduit.balance(account).exceptionally(ex -> {
            handled.set(true);
            return null;
        }).join();
        assertThat(handled).isTrue();
    }

    @Test
    void invalid_amount_throws_synchronously_through_facade() {
        registerEconomy();
        UUID account = UUID.randomUUID();

        // Negative magnitude and scale-overflow both fail at the call boundary,
        // before any future exists — not delivered via exceptionally().
        assertThatThrownBy(() -> Conduit.deposit(account, new BigDecimal("-5.00"), "reward"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conduit.deposit(account, new BigDecimal("1.234"), "reward"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Minimal {@link OfflinePlayer} stub whose only meaningful method is
     * {@link OfflinePlayer#getUniqueId()}, mirroring {@link TestPlugins}.
     */
    private static OfflinePlayer offlinePlayer(UUID id) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                ConduitFacadeConvenienceTest.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "StubPlayer[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        return 0;
    }
}

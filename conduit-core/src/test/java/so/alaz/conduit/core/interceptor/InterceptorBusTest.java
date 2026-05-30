package so.alaz.conduit.core.interceptor;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor.InterceptContext;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.core.support.TestPlugins;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterceptorBusTest {

    private static final Currency CURRENCY = SimpleCurrency.ofDefault("coins", "$", 2);

    private InterceptorBus bus;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        bus = new InterceptorBus();
        plugin = TestPlugins.named("Test");
    }

    private static InterceptContext context() {
        return new InterceptContext(UUID.randomUUID(), new BigDecimal("10.00"),
                TransactionType.WITHDRAWAL, CURRENCY, CallerToken.ANONYMOUS);
    }

    @Test
    void empty_bus_authorises() {
        assertThat(bus.preAuthorize(context())).isTrue();
    }

    @Test
    void single_veto_aborts() {
        bus.register(ctx -> false, plugin, ServicePriority.Normal);
        assertThat(bus.preAuthorize(context())).isFalse();
    }

    @Test
    void runs_highest_priority_first_and_short_circuits_on_veto() {
        List<String> order = new ArrayList<>();
        bus.register(ctx -> { order.add("low"); return true; }, plugin, ServicePriority.Low);
        bus.register(ctx -> { order.add("high"); return false; }, plugin, ServicePriority.Highest);

        boolean allowed = bus.preAuthorize(context());

        assertThat(allowed).isFalse();
        // Highest runs first and vetoes; low never runs.
        assertThat(order).containsExactly("high");
    }

    @Test
    void unregister_removes_interceptor() {
        EconomyTransactionInterceptor veto = ctx -> false;
        bus.register(veto, plugin, ServicePriority.Normal);
        bus.unregister(veto);

        assertThat(bus.preAuthorize(context())).isTrue();
        assertThat(bus.size()).isZero();
    }
}

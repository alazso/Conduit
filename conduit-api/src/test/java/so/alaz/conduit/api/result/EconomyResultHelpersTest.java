package so.alaz.conduit.api.result;

import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-helper coverage for the experimental result-shaping methods on
 * {@link EconomyResult}. No registry or provider is involved; cases are built
 * directly so this stays inside {@code conduit-api} test scope (which must not
 * depend on {@code conduit-test-fixtures} to avoid an api &rarr; fixtures &rarr; api cycle).
 */
class EconomyResultHelpersTest {

    private static final Currency COINS = SimpleCurrency.ofDefault("coins", "$", 2);
    private static final UUID ACCOUNT = UUID.randomUUID();

    private static EconomyResult.Success success() {
        return new EconomyResult.Success(ACCOUNT, COINS, new BigDecimal("42.00"), null);
    }

    private static List<EconomyResult> nonSuccessCases() {
        return List.of(
                new EconomyResult.InsufficientFunds(new BigDecimal("1.00"), new BigDecimal("5.00"), COINS),
                new EconomyResult.AccountNotFound(ACCOUNT),
                new EconomyResult.CurrencyNotSupported(COINS),
                new EconomyResult.Rejected("policy veto"),
                new EconomyResult.ProviderError("backend down", null));
    }

    @Test
    void committedBalance_present_on_success_empty_otherwise() {
        assertThat(success().committedBalance()).contains(new BigDecimal("42.00"));
        for (EconomyResult result : nonSuccessCases()) {
            assertThat(result.committedBalance()).as(result.getClass().getSimpleName()).isEmpty();
        }
    }

    @Test
    void ifFailure_fires_for_every_non_success_and_returns_this() {
        for (EconomyResult result : nonSuccessCases()) {
            AtomicInteger seen = new AtomicInteger();
            EconomyResult returned = result.ifFailure(r -> {
                assertThat(r).isSameAs(result);
                seen.incrementAndGet();
            });
            assertThat(seen).as(result.getClass().getSimpleName()).hasValue(1);
            assertThat(returned).isSameAs(result);
        }
    }

    @Test
    void ifFailure_does_not_fire_for_success() {
        AtomicInteger seen = new AtomicInteger();
        EconomyResult.Success success = success();
        assertThat(success.ifFailure(r -> seen.incrementAndGet())).isSameAs(success);
        assertThat(seen).hasValue(0);
    }

    @Test
    void ifSuccess_regression_guard_unchanged() {
        AtomicInteger seen = new AtomicInteger();
        EconomyResult.Success success = success();
        assertThat(success.ifSuccess(s -> seen.incrementAndGet())).isSameAs(success);
        assertThat(seen).hasValue(1);

        for (EconomyResult result : nonSuccessCases()) {
            result.ifSuccess(s -> seen.incrementAndGet());
        }
        assertThat(seen).hasValue(1);
    }

    @Test
    void describe_is_non_blank_for_all_six_cases() {
        assertThat(success().describe()).isNotBlank();
        for (EconomyResult result : nonSuccessCases()) {
            assertThat(result.describe()).as(result.getClass().getSimpleName()).isNotBlank();
        }
    }
}

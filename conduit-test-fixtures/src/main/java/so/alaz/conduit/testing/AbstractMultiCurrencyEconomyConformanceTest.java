package so.alaz.conduit.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.MultiCurrencyEconomy;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance suite for {@link MultiCurrencyEconomy} providers, covering
 * default-currency equivalence and unsupported-currency semantics.
 */
public abstract class AbstractMultiCurrencyEconomyConformanceTest {

    private MultiCurrencyEconomy economy;

    /**
     * @return a fresh multi-currency provider
     */
    protected abstract MultiCurrencyEconomy createMultiCurrencyEconomy();

    /**
     * @return a supported, non-default currency the provider recognises
     */
    protected abstract Currency supportedNonDefaultCurrency();

    /**
     * @return a currency the provider does <em>not</em> support
     */
    protected abstract Currency unsupportedCurrency();

    @BeforeEach
    void initProvider() {
        economy = createMultiCurrencyEconomy();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void supportedCurrencies_includes_default_and_secondary() {
        assertThat(economy.supportedCurrencies()).contains(economy.defaultCurrency(), supportedNonDefaultCurrency());
    }

    @Test
    void default_operations_are_equivalent_to_default_currency_overload() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();

        economy.deposit(uuid, money("15.00")).join();

        assertThat(economy.getBalance(uuid).join().amount()).isEqualByComparingTo("15.00");
        assertThat(economy.getBalance(uuid, economy.defaultCurrency()).join().amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void balances_are_tracked_per_currency() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();
        Currency secondary = supportedNonDefaultCurrency();

        economy.deposit(uuid, money("10.00"), economy.defaultCurrency()).join();
        economy.deposit(uuid, money("3.00"), secondary).join();

        assertThat(economy.getBalance(uuid, economy.defaultCurrency()).join().amount()).isEqualByComparingTo("10.00");
        assertThat(economy.getBalance(uuid, secondary).join().amount()).isEqualByComparingTo("3.00");
    }

    @Test
    void unsupported_currency_resolves_to_CurrencyNotSupported() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();

        EconomyResult result = economy.deposit(uuid, money("5.00"), unsupportedCurrency()).join();
        assertThat(result).isInstanceOf(EconomyResult.CurrencyNotSupported.class);
    }

    @Test
    void accountSupportsCurrency_reflects_support() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();

        assertThat(economy.accountSupportsCurrency(uuid, supportedNonDefaultCurrency()).join()).isTrue();
        assertThat(economy.accountSupportsCurrency(uuid, unsupportedCurrency()).join()).isFalse();
    }

    @Test
    void transfer_in_unsupported_currency_resolves_to_CurrencyNotSupported_without_debit() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        economy.createAccount(from).join();
        economy.createAccount(to).join();
        economy.deposit(from, money("50.00")).join();

        EconomyResult result = economy.transfer(from, to, money("5.00"), unsupportedCurrency()).join();

        assertThat(result).isInstanceOf(EconomyResult.CurrencyNotSupported.class);
        // Source must not be debited when the currency is unsupported.
        assertThat(economy.getBalance(from).join().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void withdraw_in_unsupported_currency_resolves_to_CurrencyNotSupported() {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid).join();

        EconomyResult result = economy.withdraw(uuid, money("5.00"), unsupportedCurrency()).join();
        assertThat(result).isInstanceOf(EconomyResult.CurrencyNotSupported.class);
    }
}

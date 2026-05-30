package so.alaz.conduit.testing;

import so.alaz.conduit.api.economy.MultiCurrencyEconomy;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;

class MockMultiCurrencyEconomyConformanceTest extends AbstractMultiCurrencyEconomyConformanceTest {

    private static final Currency GOLD = new SimpleCurrency("gold", "gold", "gold", "G", 0, false);
    private static final Currency RUBIES = new SimpleCurrency("rubies", "ruby", "rubies", "R", 0, false);

    @Override
    protected MultiCurrencyEconomy createMultiCurrencyEconomy() {
        return MockEconomy.builder()
                .withCurrency("coins", "$", 2)
                .withCurrency(GOLD)
                .build();
    }

    @Override
    protected Currency supportedNonDefaultCurrency() {
        return GOLD;
    }

    @Override
    protected Currency unsupportedCurrency() {
        return RUBIES;
    }
}

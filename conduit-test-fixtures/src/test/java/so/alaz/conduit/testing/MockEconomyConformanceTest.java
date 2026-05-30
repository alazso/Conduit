package so.alaz.conduit.testing;

import so.alaz.conduit.api.economy.Economy;

class MockEconomyConformanceTest extends AbstractEconomyConformanceTest {

    @Override
    protected Economy createEconomy() {
        return MockEconomy.builder().withCurrency("coins", "$", 2).build();
    }
}

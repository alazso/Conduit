package so.alaz.conduit.testing;

import so.alaz.conduit.api.economy.BankingEconomy;

class MockBankingEconomyConformanceTest extends AbstractBankingEconomyConformanceTest {

    @Override
    protected BankingEconomy createBankingEconomy() {
        return MockEconomy.builder().withCurrency("coins", "$", 2).build();
    }
}

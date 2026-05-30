package so.alaz.conduit.testing;

import so.alaz.conduit.api.economy.TransactionalEconomy;

class MockTransactionalEconomyConformanceTest extends AbstractTransactionalEconomyConformanceTest {

    @Override
    protected TransactionalEconomy createTransactionalEconomy() {
        return MockEconomy.builder().withCurrency("coins", "$", 2).build();
    }
}

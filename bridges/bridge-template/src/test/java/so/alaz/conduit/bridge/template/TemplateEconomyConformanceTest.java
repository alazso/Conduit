package so.alaz.conduit.bridge.template;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.testing.AbstractEconomyConformanceTest;

/**
 * Proves the template bridge satisfies the shared economy conformance suite —
 * the bar every community bridge must clear.
 */
class TemplateEconomyConformanceTest extends AbstractEconomyConformanceTest {

    @Override
    protected Economy createEconomy() {
        return new TemplateEconomy();
    }
}

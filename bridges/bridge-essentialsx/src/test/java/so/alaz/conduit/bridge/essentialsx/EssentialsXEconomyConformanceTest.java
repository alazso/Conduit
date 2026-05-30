package so.alaz.conduit.bridge.essentialsx;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.testing.AbstractEconomyConformanceTest;

/**
 * Runs the shared economy conformance suite against the EssentialsX bridge,
 * backed by an in-memory fake so the bridge's translation logic is validated
 * without a live server.
 */
class EssentialsXEconomyConformanceTest extends AbstractEconomyConformanceTest {

    @Override
    protected Economy createEconomy() {
        // Direct executor: operations complete synchronously for deterministic tests.
        return new EssentialsXEconomy(new FakeEssentialsBackend(), Runnable::run);
    }
}

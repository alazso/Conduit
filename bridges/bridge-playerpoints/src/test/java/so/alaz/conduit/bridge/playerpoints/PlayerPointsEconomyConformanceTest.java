package so.alaz.conduit.bridge.playerpoints;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.testing.AbstractEconomyConformanceTest;

/**
 * Runs the shared Conduit economy conformance suite against the PlayerPoints
 * bridge, driven by an in-memory fake backend and a synchronous executor.
 */
class PlayerPointsEconomyConformanceTest extends AbstractEconomyConformanceTest {

    @Override
    protected Economy createEconomy() {
        return new PlayerPointsEconomy(new FakePlayerPointsBackend(), Runnable::run);
    }
}

package so.alaz.conduit.bridge.playerpoints;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money-integrity tests for the PlayerPoints bridge: transfer atomicity when the
 * native {@code pay} fails, account guards, and concurrent read-modify-write
 * safety.
 */
class PlayerPointsEconomyIntegrityTest {

    private static final BigDecimal TEN = new BigDecimal("10");

    @Test
    void transfer_does_not_destroy_points_when_pay_fails() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        // Backend whose native pay fails atomically (no balances moved).
        FakePlayerPointsBackend backend = new FakePlayerPointsBackend() {
            @Override
            public boolean pay(@NotNull UUID source, @NotNull UUID target, int amount) {
                throw new PlayerPointsBackendException("simulated transfer failure", null);
            }
        };
        backend.ensureAccount(from);
        backend.set(from, 100);
        backend.ensureAccount(to);

        Economy economy = new PlayerPointsEconomy(backend, Runnable::run);
        EconomyResult result = economy.transfer(from, to, new BigDecimal("40")).join();

        assertThat(result).isInstanceOf(EconomyResult.ProviderError.class);
        // pay is atomic, so a failure leaves the source untouched — no points destroyed.
        assertThat(backend.look(from)).isEqualTo(100);
        assertThat(backend.look(to)).isZero();
    }

    @Test
    void mutation_on_unknown_account_resolves_to_AccountNotFound() {
        FakePlayerPointsBackend backend = new FakePlayerPointsBackend();
        Economy economy = new PlayerPointsEconomy(backend, Runnable::run);

        EconomyResult deposit = economy.deposit(UUID.randomUUID(), TEN).join();
        EconomyResult set = economy.set(UUID.randomUUID(), TEN).join();

        assertThat(deposit).isInstanceOf(EconomyResult.AccountNotFound.class);
        assertThat(set).isInstanceOf(EconomyResult.AccountNotFound.class);
    }

    @Test
    void concurrent_withdrawals_never_overdraw() throws Exception {
        UUID account = UUID.randomUUID();
        FakePlayerPointsBackend backend = new FakePlayerPointsBackend();
        backend.ensureAccount(account);
        backend.set(account, 100); // exactly 10 withdrawals of 10

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Economy economy = new PlayerPointsEconomy(backend, pool);
            int attempts = 50; // far more than the balance allows

            CompletableFuture<?>[] futures = new CompletableFuture<?>[attempts];
            for (int i = 0; i < attempts; i++) {
                futures[i] = economy.withdraw(account, TEN);
            }
            CompletableFuture.allOf(futures).join();

            long successes = 0;
            for (CompletableFuture<?> future : futures) {
                if (((EconomyResult) future.join()) instanceof EconomyResult.Success) {
                    successes++;
                }
            }
            // Without per-account locking this races and overdraws; with it,
            // exactly 10 succeed and the balance never goes negative.
            assertThat(successes).isEqualTo(10);
            assertThat(backend.look(account)).isZero();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}

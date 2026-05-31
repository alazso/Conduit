package so.alaz.conduit.bridge.playerpoints;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Per-account serialization for the PlayerPoints bridge.
 *
 * <p>The bridge pre-checks a balance and then mutates it, and dispatches that work
 * onto a multi-threaded async executor, so concurrent operations on the same
 * account would otherwise race (lost updates, overdraft). Each operation runs
 * under the account's lock; two-account operations (transfer) acquire both locks
 * in {@link UUID#compareTo(UUID) UUID order} to avoid deadlock.
 *
 * <p>Locks are created lazily and retained per UUID; the map is bounded in
 * practice by the number of distinct accounts the server touches.
 */
final class AccountLocks {

    private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Run {@code action} holding {@code account}'s lock.
     *
     * @param account the account to lock
     * @param action  the work to run while locked
     * @param <T>     the result type
     * @return the action's result
     */
    <T> T withLock(@NotNull UUID account, @NotNull Supplier<T> action) {
        ReentrantLock lock = lockFor(account);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Run {@code action} holding both accounts' locks, acquired in a consistent
     * order so concurrent transfers in opposite directions cannot deadlock.
     *
     * @param a      one account
     * @param b      the other account
     * @param action the work to run while both are locked
     * @param <T>    the result type
     * @return the action's result
     */
    <T> T withLocks(@NotNull UUID a, @NotNull UUID b, @NotNull Supplier<T> action) {
        if (a.equals(b)) {
            return withLock(a, action);
        }
        UUID first = a.compareTo(b) <= 0 ? a : b;
        UUID second = first.equals(a) ? b : a;
        ReentrantLock l1 = lockFor(first);
        ReentrantLock l2 = lockFor(second);
        l1.lock();
        try {
            l2.lock();
            try {
                return action.get();
            } finally {
                l2.unlock();
            }
        } finally {
            l1.unlock();
        }
    }

    private ReentrantLock lockFor(UUID id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }
}

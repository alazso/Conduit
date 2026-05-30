package so.alaz.conduit.core.registry;

import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.event.ActiveProviderChangeEvent;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor;
import so.alaz.conduit.api.event.ProviderRegisterEvent;
import so.alaz.conduit.api.event.ProviderUnregisterEvent;
import so.alaz.conduit.api.exception.ProviderNotFoundException;
import so.alaz.conduit.api.registry.ProviderInfo;
import so.alaz.conduit.api.registry.ProviderRegistry;
import so.alaz.conduit.core.economy.EconomyDispatcher;
import so.alaz.conduit.core.events.EventPublisher;
import so.alaz.conduit.core.interceptor.InterceptorBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Thread-safe {@link ProviderRegistry} implementation.
 *
 * <p>Implements the normative registration/resolution contract: single
 * most-derived registration, hierarchy-walking resolution by registered type,
 * {@link ServicePriority} tie-breaking (registration order within equal
 * priority), duplicate-instance rejection, deferred consumption, and per-base
 * active-provider change events.
 *
 * <p>All mutation and resolution happens under a single monitor; events and
 * deferred consumers are collected under the lock and dispatched after it is
 * released, so listeners may safely call back into the registry.
 */
@ApiStatus.Internal
public final class ProviderRegistryImpl implements ProviderRegistry {

    private record Registration(Class<?> service, Object provider, Plugin plugin, ServicePriority priority, long seq) {}

    private final Object lock = new Object();
    private final List<Registration> registrations = new ArrayList<>();
    private final Set<Object> instances = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Class<?>, List<Consumer<?>>> pending = new HashMap<>();
    private final Map<Plugin, CallerToken> callers = new IdentityHashMap<>();
    private final Set<Class<?>> trackedBases = new LinkedHashSet<>();
    // Memoised dispatch decorators, keyed by the raw provider instance, so every
    // resolution of the same provider returns the same decorated object (stable
    // identity) and dispatch applies uniformly across all economy interfaces.
    private final Map<Economy, Economy> economyDispatchers = new IdentityHashMap<>();

    private final EventPublisher events;
    private final InterceptorBus interceptors;

    private long seq;
    private volatile @Nullable String economyProviderOverride;

    /**
     * @param events       publisher for registry lifecycle events
     * @param interceptors the shared interceptor bus
     */
    public ProviderRegistryImpl(@NotNull EventPublisher events, @NotNull InterceptorBus interceptors) {
        this.events = events;
        this.interceptors = interceptors;
    }

    // --- Lookup ---

    @Override
    public <T> @NotNull Optional<T> getProvider(@NotNull Class<T> service) {
        synchronized (lock) {
            return Optional.ofNullable(resolveActive(service));
        }
    }

    @Override
    public <T> @NotNull T requireProvider(@NotNull Class<T> service) {
        T provider;
        synchronized (lock) {
            provider = resolveActive(service);
        }
        if (provider == null) {
            throw new ProviderNotFoundException(service);
        }
        return provider;
    }

    @Override
    public <T> @NotNull List<T> getProviders(@NotNull Class<T> service) {
        synchronized (lock) {
            return resolveAll(service);
        }
    }

    @Override
    public <T> @NotNull ProviderInfo<T> getProviderInfo(@NotNull Class<T> service) {
        synchronized (lock) {
            List<T> all = resolveAll(service);
            Registration active = activeRegistration(service);
            return new ProviderInfo<>(
                    Optional.ofNullable(active == null ? null : service.cast(decorate(active.provider()))),
                    all,
                    active == null ? null : active.priority(),
                    active == null ? null : active.plugin());
        }
    }

    // --- Registration ---

    @Override
    public <T> void register(@NotNull Class<T> service, @NotNull T provider, @NotNull Plugin plugin, @NotNull ServicePriority priority) {
        if (!service.isInstance(provider)) {
            throw new IllegalArgumentException(
                    "Provider " + provider.getClass().getName() + " is not an instance of service " + service.getName());
        }
        if (provider instanceof Economy economy) {
            requireCompatibleApiVersion(economy);
        }

        List<Event> toPublish = new ArrayList<>();
        List<Runnable> deferred = new ArrayList<>();

        synchronized (lock) {
            if (!instances.add(provider)) {
                throw new IllegalStateException(
                        "Provider instance already registered: " + provider.getClass().getName()
                                + ". Register a multi-interface provider once under its most-derived type.");
            }

            trackedBases.addAll(conduitBases(service));
            Map<Class<?>, Object> before = snapshotActives();

            registrations.add(new Registration(service, provider, plugin, priority, seq++));

            Map<Class<?>, Object> after = snapshotActives();

            toPublish.add(new ProviderRegisterEvent<>(service, provider, priority, plugin));
            collectActiveChanges(before, after, toPublish);
            collectPending(service, deferred);
        }

        dispatch(toPublish, deferred);
    }

    @Override
    public <T> void unregister(@NotNull Class<T> service, @NotNull T provider) {
        List<Event> toPublish = new ArrayList<>();

        synchronized (lock) {
            Map<Class<?>, Object> before = snapshotActives();
            boolean removed = registrations.removeIf(r -> r.service().equals(service) && r.provider() == provider);
            if (!removed) {
                return;
            }
            instances.remove(provider);
            if (provider instanceof Economy) {
                economyDispatchers.remove(provider);
            }
            Map<Class<?>, Object> after = snapshotActives();

            toPublish.add(new ProviderUnregisterEvent<>(service, provider));
            collectActiveChanges(before, after, toPublish);
        }

        dispatch(toPublish, List.of());
    }

    // --- Deferred consumption ---

    @Override
    public <T> void whenProviderAvailable(@NotNull Class<T> service, @NotNull Consumer<T> consumer) {
        T active;
        synchronized (lock) {
            active = resolveActive(service);
            if (active == null) {
                pending.computeIfAbsent(service, k -> new ArrayList<>()).add(consumer);
                return;
            }
        }
        consumer.accept(active);
    }

    // --- Caller identity & interceptors ---

    @Override
    public @NotNull CallerToken registerCaller(@NotNull Plugin plugin) {
        synchronized (lock) {
            return callers.computeIfAbsent(plugin, p -> CallerToken.create(p, p.getName()));
        }
    }

    @Override
    public void registerInterceptor(@NotNull EconomyTransactionInterceptor interceptor, @NotNull Plugin plugin, @NotNull ServicePriority priority) {
        interceptors.register(interceptor, plugin, priority);
    }

    @Override
    public void unregisterInterceptor(@NotNull EconomyTransactionInterceptor interceptor) {
        interceptors.unregister(interceptor);
    }

    // --- Internals ---

    @SuppressWarnings("unchecked")
    private <T> @Nullable T resolveActive(Class<T> service) {
        Registration active = activeRegistration(service);
        return active == null ? null : (T) decorate(active.provider());
    }

    /**
     * Wrap economy providers in the dispatch decorator (memoised per raw
     * instance for stable identity); pass non-economy providers through
     * unchanged. Always invoked under {@code lock}.
     */
    private Object decorate(Object provider) {
        if (provider instanceof EconomyDispatcher) {
            return provider;
        }
        if (provider instanceof Economy economy) {
            return economyDispatchers.computeIfAbsent(economy, e -> new EconomyDispatcher(e, interceptors, events));
        }
        return provider;
    }

    private static void requireCompatibleApiVersion(Economy economy) {
        String required = economy.requiredApiVersion();
        if (!apiVersionSatisfied(required)) {
            throw new IllegalArgumentException(
                    "Economy provider '" + economy.getName() + "' requires Conduit API " + required
                            + " but this runtime provides " + Conduit.API_VERSION
                            + "; refusing to register an incompatible provider.");
        }
    }

    private static boolean apiVersionSatisfied(String required) {
        int[] req = parseMajorMinor(required);
        int[] cur = parseMajorMinor(Conduit.API_VERSION);
        if (req[0] != cur[0]) {
            return req[0] < cur[0];
        }
        return req[1] <= cur[1];
    }

    private static int[] parseMajorMinor(String version) {
        String[] parts = version.trim().split("\\.");
        try {
            int major = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return new int[]{major, minor};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unparseable API version: " + version, e);
        }
    }

    private @Nullable Registration activeRegistration(Class<?> service) {
        String override = economyProviderOverride;
        Registration best = null;
        Registration overridden = null;
        for (Registration r : registrations) {
            if (!service.isAssignableFrom(r.service())) {
                continue;
            }
            if (best == null || higherPriority(r, best)) {
                best = r;
            }
            if (override != null
                    && r.provider() instanceof Economy economy
                    && economy.getName().equalsIgnoreCase(override)
                    && (overridden == null || higherPriority(r, overridden))) {
                overridden = r;
            }
        }
        return overridden != null ? overridden : best;
    }

    /**
     * Force economy resolution to prefer the named provider regardless of
     * priority. {@code null} restores automatic (priority-based) selection.
     *
     * @param name the provider name to prefer, or {@code null}
     */
    public void setEconomyProviderOverride(@Nullable String name) {
        synchronized (lock) {
            this.economyProviderOverride = name;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> resolveAll(Class<T> service) {
        List<Registration> matches = new ArrayList<>();
        for (Registration r : registrations) {
            if (service.isAssignableFrom(r.service())) {
                matches.add(r);
            }
        }
        matches.sort(Comparator
                .comparingInt((Registration r) -> r.priority().ordinal()).reversed()
                .thenComparingLong(Registration::seq));
        List<T> out = new ArrayList<>(matches.size());
        for (Registration r : matches) {
            out.add((T) decorate(r.provider()));
        }
        return List.copyOf(out);
    }

    /** Higher priority wins; within equal priority, earlier registration (lower seq) wins. */
    private static boolean higherPriority(Registration candidate, Registration current) {
        int byPriority = Integer.compare(candidate.priority().ordinal(), current.priority().ordinal());
        if (byPriority != 0) {
            return byPriority > 0;
        }
        return candidate.seq() < current.seq();
    }

    private Map<Class<?>, Object> snapshotActives() {
        Map<Class<?>, Object> snapshot = new LinkedHashMap<>();
        for (Class<?> base : trackedBases) {
            Registration active = activeRegistration(base);
            snapshot.put(base, active == null ? null : active.provider());
        }
        return snapshot;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void collectActiveChanges(Map<Class<?>, Object> before, Map<Class<?>, Object> after, List<Event> out) {
        for (Class<?> base : trackedBases) {
            Object old = before.get(base);
            Object now = after.get(base);
            if (now != null && now != old) {
                out.add(new ActiveProviderChangeEvent(base, old, now));
            }
        }
    }

    private void collectPending(Class<?> registeredService, List<Runnable> out) {
        for (Map.Entry<Class<?>, List<Consumer<?>>> entry : pending.entrySet()) {
            Class<?> wanted = entry.getKey();
            if (wanted.isAssignableFrom(registeredService)) {
                Object active = resolveActive(wanted);
                if (active != null) {
                    for (Consumer<?> consumer : entry.getValue()) {
                        @SuppressWarnings("unchecked")
                        Consumer<Object> typed = (Consumer<Object>) consumer;
                        out.add(() -> typed.accept(active));
                    }
                    entry.setValue(new ArrayList<>());
                }
            }
        }
    }

    private void dispatch(List<Event> toPublish, List<Runnable> deferred) {
        for (Event event : toPublish) {
            events.publish(event);
        }
        for (Runnable r : deferred) {
            r.run();
        }
    }

    /**
     * Collect the non-JDK service interfaces/classes in {@code service}'s type
     * hierarchy (including {@code service} itself). These are the base keys
     * tracked for active-provider change events; JDK marker interfaces
     * ({@code java.*} / {@code javax.*}) are excluded to avoid spurious events.
     */
    private static Set<Class<?>> conduitBases(Class<?> service) {
        Set<Class<?>> bases = new LinkedHashSet<>();
        collectTrackableTypes(service, bases);
        return bases;
    }

    private static void collectTrackableTypes(@Nullable Class<?> type, Set<Class<?>> out) {
        if (type == null || type == Object.class) {
            return;
        }
        String name = type.getName();
        if (!name.startsWith("java.") && !name.startsWith("javax.")) {
            out.add(type);
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectTrackableTypes(iface, out);
        }
        collectTrackableTypes(type.getSuperclass(), out);
    }
}

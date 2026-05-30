package so.alaz.conduit.api.registry;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The central service registry for Conduit providers.
 *
 * <h3>Registration &amp; resolution (normative)</h3>
 * <ol>
 *   <li><b>Most-derived registration.</b> A provider implementing several
 *       Conduit service interfaces is registered <em>once</em>, under its
 *       most-derived type. If no single most-derived type exists, the registrant
 *       must declare a composite interface or pick a canonical type; the
 *       registry does not guess.</li>
 *   <li><b>Hierarchy-walking resolution.</b> {@link #getProvider(Class)} finds
 *       all registrations whose registered type is assignable to the query type
 *       and returns the highest-priority entry; resolution is by registered
 *       type, not {@code instanceof}.</li>
 *   <li><b>Priority tie-breaking.</b> {@link ServicePriority} is the only
 *       tiebreaker (highest wins); subtype specificity is not. Within equal
 *       priority, earlier registration wins.</li>
 *   <li><b>No duplicate-instance registration.</b> A given provider instance
 *       cannot be registered twice; the registry rejects this with
 *       {@link IllegalStateException}.</li>
 * </ol>
 */
@ApiStatus.AvailableSince("1.0.0")
public interface ProviderRegistry {

    // --- Lookup ---

    /**
     * @param service the queried service type
     * @param <T>     the service type
     * @return the highest-priority provider assignable to {@code service}, or empty
     */
    <T> @NotNull Optional<T> getProvider(@NotNull Class<T> service);

    /**
     * @param service the queried service type
     * @param <T>     the service type
     * @return the highest-priority provider assignable to {@code service}
     * @throws so.alaz.conduit.api.exception.ProviderNotFoundException if none is registered
     */
    <T> @NotNull T requireProvider(@NotNull Class<T> service);

    /**
     * @param service the queried service type
     * @param <T>     the service type
     * @return all matching providers in priority order (highest first)
     */
    <T> @NotNull List<T> getProviders(@NotNull Class<T> service);

    /**
     * @param service the queried service type
     * @param <T>     the service type
     * @return a snapshot of the registry state for {@code service}
     */
    <T> @NotNull ProviderInfo<T> getProviderInfo(@NotNull Class<T> service);

    // --- Registration ---

    /**
     * Register a provider under its most-derived service type.
     *
     * @param service  the service type to register under
     * @param provider the provider instance
     * @param plugin   the registering plugin
     * @param priority the service priority
     * @param <T>      the service type
     * @throws IllegalStateException if the instance is already registered
     */
    <T> void register(@NotNull Class<T> service, @NotNull T provider, @NotNull Plugin plugin, @NotNull ServicePriority priority);

    /**
     * Unregister a previously registered provider.
     *
     * @param service  the service type it was registered under
     * @param provider the provider instance
     * @param <T>      the service type
     */
    <T> void unregister(@NotNull Class<T> service, @NotNull T provider);

    // --- Deferred consumption ---

    /**
     * Run {@code consumer} immediately if a provider is already registered for
     * {@code service}, otherwise once the next one registers (single-shot,
     * order-insensitive — eliminates the onEnable race).
     *
     * @param service  the service type of interest
     * @param consumer the consumer of the provider
     * @param <T>      the service type
     */
    <T> void whenProviderAvailable(@NotNull Class<T> service, @NotNull Consumer<T> consumer);

    // --- Caller identity &amp; interceptors ---

    /**
     * Register a plugin as a caller. Idempotent; call once in {@code onEnable()}.
     *
     * @param plugin the calling plugin
     * @return a caller token tied to the plugin's identity
     */
    @NotNull CallerToken registerCaller(@NotNull Plugin plugin);

    /**
     * Register a synchronous pre-authorisation interceptor for economy operations.
     *
     * @param interceptor the interceptor
     * @param plugin      the registering plugin
     * @param priority    the interceptor priority (highest runs first)
     */
    void registerInterceptor(@NotNull EconomyTransactionInterceptor interceptor, @NotNull Plugin plugin, @NotNull ServicePriority priority);

    /**
     * Remove a registered interceptor.
     *
     * @param interceptor the interceptor to remove
     */
    void unregisterInterceptor(@NotNull EconomyTransactionInterceptor interceptor);
}

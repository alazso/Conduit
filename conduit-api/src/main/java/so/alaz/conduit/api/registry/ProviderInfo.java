package so.alaz.conduit.api.registry;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * An immutable snapshot of the registry state for a single service type.
 *
 * @param activeProvider  the highest-priority provider, if any
 * @param allProviders    all matching providers in priority order (highest first)
 * @param activePriority  the active provider's priority, or {@code null} if none
 * @param activeRegistrant the plugin that registered the active provider, or {@code null}
 * @param <T>             the service type
 */
@ApiStatus.AvailableSince("1.0.0")
public record ProviderInfo<T>(
        @NotNull Optional<T> activeProvider,
        @NotNull List<T> allProviders,
        @Nullable ServicePriority activePriority,
        @Nullable Plugin activeRegistrant
) {
    /**
     * Canonical constructor; defensively copies the provider list.
     */
    public ProviderInfo {
        allProviders = List.copyOf(allProviders);
    }
}

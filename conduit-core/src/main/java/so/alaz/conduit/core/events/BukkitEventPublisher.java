package so.alaz.conduit.core.events;

import org.bukkit.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.core.scheduler.SchedulerAdapter;

/**
 * {@link EventPublisher} that dispatches Bukkit events on a thread appropriate
 * for the event's synchronicity: asynchronous events on an async worker,
 * synchronous events on the global/main thread.
 */
@ApiStatus.Internal
public final class BukkitEventPublisher implements EventPublisher {

    private final org.bukkit.Server server;
    private final SchedulerAdapter scheduler;

    public BukkitEventPublisher(@NotNull org.bukkit.Server server, @NotNull SchedulerAdapter scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    @Override
    public void publish(@NotNull Event event) {
        if (event.isAsynchronous()) {
            scheduler.runAsync(() -> server.getPluginManager().callEvent(event));
        } else {
            scheduler.runGlobal(() -> server.getPluginManager().callEvent(event));
        }
    }
}

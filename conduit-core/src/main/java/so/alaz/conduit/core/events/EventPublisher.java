package so.alaz.conduit.core.events;

import org.bukkit.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Publishes Bukkit events on a thread appropriate for the event's
 * synchronicity. Abstracted so the runtime can be unit-tested without a live
 * server.
 */
@ApiStatus.Internal
public interface EventPublisher {

    /**
     * Publish an event. Asynchronous events
     * ({@link Event#isAsynchronous()} {@code == true}) are dispatched off the
     * main thread; synchronous events are dispatched on the main/global thread.
     *
     * @param event the event to publish
     */
    void publish(@NotNull Event event);
}

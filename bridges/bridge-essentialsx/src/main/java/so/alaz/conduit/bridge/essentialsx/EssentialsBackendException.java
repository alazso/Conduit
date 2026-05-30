package so.alaz.conduit.bridge.essentialsx;

/**
 * Wraps a checked failure from the EssentialsX economy API so it surfaces
 * loudly through the bridge's {@code CompletableFuture} rather than being
 * silently swallowed.
 */
public final class EssentialsBackendException extends RuntimeException {

    public EssentialsBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}

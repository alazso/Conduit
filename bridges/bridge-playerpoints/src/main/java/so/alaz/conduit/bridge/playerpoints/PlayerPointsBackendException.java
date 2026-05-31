package so.alaz.conduit.bridge.playerpoints;

/**
 * Unchecked wrapper for failures surfaced by the PlayerPoints backend. Propagates
 * through the bridge's {@link java.util.concurrent.CompletableFuture} pipeline and
 * is mapped to an {@link so.alaz.conduit.api.result.EconomyResult.ProviderError}
 * where the bridge can preserve money integrity.
 */
public final class PlayerPointsBackendException extends RuntimeException {

    public PlayerPointsBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}

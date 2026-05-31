package so.alaz.conduit.core.metrics;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import dev.faststats.core.data.Metric;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

/**
 * FastStats metrics and error-tracking wiring. The FastStats SDK is shaded into
 * the plugin jar (relocated under {@code so.alaz.conduit.libs.faststats}), so the
 * hard references here are always satisfied at runtime.
 *
 * <p>The project Public ID is compiled in rather than read from configuration:
 * FastStats treats it as a non-secret project identifier, but exposing it in
 * {@code config.yml} would let an operator redirect Conduit's metrics and error
 * reports to an unrelated FastStats project.
 */
@ApiStatus.Internal
public final class MetricsService {

    /** Conduit's FastStats project Public ID (non-secret, intentionally hardcoded). */
    private static final String PROJECT_ID = "db63118b3e9efdccb94d53b4674dd855";

    private final BukkitMetrics metrics;

    /**
     * @param plugin             the owning plugin
     * @param activeProviderName supplier of the active economy provider name
     * @param debug              enable verbose FastStats logging (development only)
     */
    public MetricsService(@NotNull JavaPlugin plugin,
                          @NotNull Supplier<String> activeProviderName,
                          boolean debug) {
        this.metrics = BukkitMetrics.factory()
                .token(PROJECT_ID)
                .addMetric(Metric.string("active_economy_provider", activeProviderName::get))
                .errorTracker(createErrorTracker())
                .debug(debug)
                .create(plugin);
    }

    /**
     * Builds the context-aware error tracker with filtering and anonymization.
     *
     * <p>Conduit dispatches economy calls through reflective proxies, so the raw
     * stack traces FastStats would upload can carry sensitive substrings (player
     * UUIDs, JDBC URLs, SQL fragments, filesystem paths). The anonymizers below
     * scrub those before any data leaves the server. When in doubt we prefer
     * over-redacting: a slightly noisy trace is recoverable, a leaked secret is
     * not.
     */
    private static ErrorTracker createErrorTracker() {
        return ErrorTracker.contextAware()
                // Filtering: drop reflection/proxy wrapper noise from dispatch.
                .ignoreError(InvocationTargetException.class)

                // Anonymization: generic secrets (emails, tokens, keys, UUIDs, query params).
                .anonymize("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$", "[email hidden]")
                .anonymize("Bearer [A-Za-z0-9._~+/=-]+", "Bearer [token hidden]")
                .anonymize("AKIA[0-9A-Z]{16}", "[aws-key hidden]")
                .anonymize("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "[uuid hidden]")
                .anonymize("([?&](?:api_?key|token|secret)=)[^&\\s]+", "$1[redacted]")

                // Anonymization: economy-provider additions (JDBC/SQL leakage, filesystem paths).
                .anonymize("jdbc:[^\\s\"']+", "jdbc:[redacted]")
                .anonymize("(?i)\\b(SELECT|INSERT|UPDATE|DELETE)\\b[^\\n]{0,200}", "[sql hidden]")
                .anonymize("[A-Za-z]:\\\\[^\\s\"']+", "[path hidden]")
                .anonymize("(?<![\\w.])(/[/\\w.-]+){2,}", "[path hidden]");
    }

    /**
     * Register FastStats error handlers. Call once after construction, during
     * plugin enable.
     */
    public void ready() {
        metrics.ready();
    }

    /**
     * Shut down the metrics submitter.
     */
    public void shutdown() {
        metrics.shutdown();
    }
}

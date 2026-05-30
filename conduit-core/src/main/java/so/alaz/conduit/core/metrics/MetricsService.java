package so.alaz.conduit.core.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * bStats metrics wiring. Constructed only when the bStats classes are present on
 * the runtime classpath (guarded by the caller), so the hard reference here is
 * safe.
 */
@ApiStatus.Internal
public final class MetricsService {

    private static final int BSTATS_PLUGIN_ID = 99999;

    private final Metrics metrics;

    public MetricsService(@NotNull JavaPlugin plugin, @NotNull Supplier<String> activeProviderName) {
        this.metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("active_economy_provider", activeProviderName::get));
    }

    /**
     * Shut down the metrics submitter.
     */
    public void shutdown() {
        metrics.shutdown();
    }
}

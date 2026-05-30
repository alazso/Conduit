package so.alaz.conduit.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.core.command.ConduitCommand;
import so.alaz.conduit.core.economy.EconomyDispatcher;
import so.alaz.conduit.core.events.BukkitEventPublisher;
import so.alaz.conduit.core.events.EventPublisher;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.metrics.MetricsService;
import so.alaz.conduit.core.papi.ConduitPlaceholderExpansion;
import so.alaz.conduit.core.registry.ProviderRegistryImpl;
import so.alaz.conduit.core.scheduler.SchedulerAdapter;
import so.alaz.conduit.core.scheduler.SchedulerAdapterImpl;
import so.alaz.conduit.core.update.UpdateChecker;

import java.net.URI;

/**
 * Conduit runtime entry point. Constructs the registry, interceptor bus, event
 * publisher, and scheduler; installs the {@link Conduit} static facade and the
 * economy dispatch decorator; then registers the operator surface.
 */
@ApiStatus.Internal
public final class ConduitPlugin extends JavaPlugin {

    private static final URI LATEST_RELEASE_ENDPOINT =
            URI.create("https://api.github.com/repos/alazso/conduit/releases/latest");

    private InterceptorBus interceptors;
    private ProviderRegistryImpl registry;
    private SchedulerAdapter scheduler;
    private EventPublisher eventPublisher;
    private MetricsService metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.scheduler = new SchedulerAdapterImpl(this);
        this.eventPublisher = new BukkitEventPublisher(getServer(), scheduler);
        this.interceptors = new InterceptorBus();
        this.registry = new ProviderRegistryImpl(eventPublisher, interceptors);

        Conduit.init(registry);
        Conduit.setEconomyDecorator(this::decorate);

        applyProviderOverride();
        registerCommand();
        registerPlaceholders();
        startMetrics();
        startUpdateChecker();

        getSLF4JLogger().info("Conduit {} enabled — economy abstraction ready.", getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        Conduit.shutdown();
    }

    private void registerCommand() {
        getServer().getCommandMap().register("conduit", new ConduitCommand(this));
    }

    private void registerPlaceholders() {
        if (!isPluginPresent("PlaceholderAPI")) {
            return;
        }
        ConduitPlaceholderExpansion expansion = new ConduitPlaceholderExpansion(getPluginMeta().getVersion());
        expansion.register();
        getSLF4JLogger().info("Registered PlaceholderAPI expansion.");
    }

    private void applyProviderOverride() {
        String override = getConfig().getString("economy.provider-override", "").trim();
        registry.setEconomyProviderOverride(override.isEmpty() ? null : override);
        if (!override.isEmpty()) {
            getSLF4JLogger().info("Economy provider override active: '{}'.", override);
        }
    }

    private void startMetrics() {
        if (!getConfig().getBoolean("metrics.enabled", false)) {
            return;
        }
        int bstatsId = getConfig().getInt("metrics.bstats-id", 0);
        if (bstatsId <= 0) {
            getSLF4JLogger().warn("Metrics enabled but metrics.bstats-id is unset; skipping bStats submission.");
            return;
        }
        if (!isClassPresent("org.bstats.bukkit.Metrics")) {
            return;
        }
        this.metrics = new MetricsService(this, bstatsId,
                () -> registry.getProvider(Economy.class).map(Economy::getName).orElse("none"));
    }

    private void startUpdateChecker() {
        if (!getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }
        new UpdateChecker(getPluginMeta().getVersion(), LATEST_RELEASE_ENDPOINT,
                scheduler::runAsync, getSLF4JLogger()).checkAsync();
    }

    private boolean isPluginPresent(String name) {
        return getServer().getPluginManager().getPlugin(name) != null;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Economy decorate(@NotNull Economy economy) {
        if (economy instanceof EconomyDispatcher) {
            return economy;
        }
        return new EconomyDispatcher(economy, interceptors, eventPublisher);
    }

    /**
     * @return the runtime provider registry
     */
    public @NotNull ProviderRegistryImpl registry() {
        return registry;
    }

    /**
     * @return the runtime scheduler adapter
     */
    public @NotNull SchedulerAdapter scheduler() {
        return scheduler;
    }
}

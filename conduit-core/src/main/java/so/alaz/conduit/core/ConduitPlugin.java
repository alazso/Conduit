package so.alaz.conduit.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        getComponentLogger().info(Component.text("Conduit ", NamedTextColor.GREEN)
                .append(Component.text(getPluginMeta().getVersion(), NamedTextColor.AQUA))
                .append(Component.text(" enabled — economy abstraction ready.", NamedTextColor.GREEN)));
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
        getComponentLogger().info(Component.text("Registered PlaceholderAPI expansion.", NamedTextColor.GREEN));
    }

    private void applyProviderOverride() {
        String override = getConfig().getString("economy.provider-override", "").trim();
        registry.setEconomyProviderOverride(override.isEmpty() ? null : override);
        if (!override.isEmpty()) {
            getComponentLogger().info(Component.text("Economy provider override active: '", NamedTextColor.AQUA)
                    .append(Component.text(override, NamedTextColor.WHITE))
                    .append(Component.text("'.", NamedTextColor.AQUA)));
        }
    }

    private void startMetrics() {
        if (!getConfig().getBoolean("metrics.enabled", false)) {
            return;
        }
        int bstatsId = getConfig().getInt("metrics.bstats-id", 0);
        if (bstatsId <= 0) {
            getComponentLogger().warn(Component.text(
                    "Metrics enabled but metrics.bstats-id is unset; skipping bStats submission.",
                    NamedTextColor.YELLOW));
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
                scheduler::runAsync, getComponentLogger()).checkAsync();
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

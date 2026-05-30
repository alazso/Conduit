package so.alaz.conduit.example.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.plugin.java.JavaPlugin;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.registry.ProviderInfo;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Case B example: a shop where the <strong>server owner</strong> chooses which
 * Conduit economy the shop charges against. The choice is an operator config
 * decision, not a per-player one.
 *
 * <p>The server may have several registered providers (e.g. the
 * {@code conduit-economy} dollars provider and the {@code conduit-points} points
 * provider). The owner pins one by its registered name in {@code config.yml}
 * ({@code economy: ConduitExamplePoints}); leaving it blank/{@code auto} follows
 * whichever provider Conduit resolves as active.
 *
 * <ul>
 *   <li>{@code /economies} — list registered economies (so the owner knows the
 *       valid names) and show which one this shop is currently using.</li>
 *   <li>{@code /buy} — buy one item, paying in the configured economy.</li>
 * </ul>
 */
public final class ConduitShopPlugin extends JavaPlugin {

    /** Flat price for the demo item. Scale 0 so it is valid for any currency precision. */
    static final BigDecimal ITEM_PRICE = new BigDecimal("5");

    private static final String CONFIG_KEY = "economy";

    private CallerToken callerToken;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.callerToken = Conduit.getRegistry().registerCaller(this);
        getServer().getCommandMap().register("conduitshop", new EconomiesCommand(this));
        getServer().getCommandMap().register("conduitshop", new BuyCommand(this));
        getSLF4JLogger().info("Conduit shop ready; configured economy = '{}'.", configuredEconomyName());
    }

    /**
     * @return the raw economy name the owner configured, or an empty string for auto
     */
    String configuredEconomyName() {
        return getConfig().getString(CONFIG_KEY, "").trim();
    }

    /**
     * Resolve the economy this shop should charge against, honouring the owner's
     * configuration. Resolution is done lazily (per command) rather than cached at
     * enable, so it is immune to plugin load order: by the time a player buys,
     * every provider is registered.
     *
     * @return the configured provider, the active provider as a fallback, or empty
     *         if nothing is registered at all
     */
    Optional<Economy> resolveConfiguredEconomy() {
        ProviderInfo<Economy> info = Conduit.getRegistry().getProviderInfo(Economy.class);
        if (info.allProviders().isEmpty()) {
            return Optional.empty();
        }
        String configured = configuredEconomyName();
        if (configured.isEmpty() || configured.equalsIgnoreCase("auto")) {
            return info.activeProvider();
        }
        Optional<Economy> match = info.allProviders().stream()
                .filter(economy -> economy.getName().equalsIgnoreCase(configured))
                .findFirst();
        if (match.isEmpty()) {
            getSLF4JLogger().warn(
                    "Configured economy '{}' is not registered; falling back to active provider '{}'. "
                            + "Valid names: {}",
                    configured,
                    info.activeProvider().map(Economy::getName).orElse("none"),
                    info.allProviders().stream().map(Economy::getName).toList());
            return info.activeProvider();
        }
        return match;
    }

    /**
     * Wrap the chosen provider in Conduit's dispatch layer when it is the active
     * provider. Non-active providers are returned raw — operating on them bypasses
     * validation/interceptors/events, which is an inherent property of targeting a
     * specific non-default provider.
     *
     * @param chosen the provider the owner selected
     * @return the handle to actually operate through
     */
    Economy dispatchHandleFor(Economy chosen) {
        Economy rawActive = Conduit.getRegistry().getProvider(Economy.class).orElse(null);
        return chosen.equals(rawActive) ? Conduit.getEconomy() : chosen;
    }

    /**
     * @return the caller token identifying this plugin to Conduit
     */
    CallerToken callerToken() {
        return callerToken;
    }

    static Component info(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    static Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}

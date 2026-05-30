package so.alaz.conduit.example.shop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.registry.ProviderInfo;

/**
 * {@code /economies} — discovers every registered economy from the registry and
 * reports its name and currency, so the server owner knows which names are valid
 * to put in {@code config.yml}. Also shows which economy this shop currently uses.
 */
final class EconomiesCommand extends Command {

    private final ConduitShopPlugin plugin;

    EconomiesCommand(ConduitShopPlugin plugin) {
        super("economies");
        this.plugin = plugin;
        setDescription("List all economies registered with Conduit.");
        setUsage("/economies");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        ProviderInfo<Economy> info = Conduit.getRegistry().getProviderInfo(Economy.class);
        if (info.allProviders().isEmpty()) {
            sender.sendMessage(ConduitShopPlugin.error("No economies are registered."));
            return true;
        }

        Economy active = info.activeProvider().orElse(null);
        sender.sendMessage(ConduitShopPlugin.info("Registered economies (priority order):"));
        for (Economy economy : info.allProviders()) {
            String marker = economy.equals(active) ? " (active)" : "";
            sender.sendMessage(ConduitShopPlugin.info("  - " + economy.getName()
                    + " — currency " + economy.defaultCurrency().id()
                    + ", e.g. " + economy.format(ConduitShopPlugin.ITEM_PRICE) + marker));
        }

        String configured = plugin.configuredEconomyName();
        String configuredLabel = configured.isEmpty() ? "auto (active provider)" : configured;
        String usingNow = plugin.resolveConfiguredEconomy().map(Economy::getName).orElse("none");
        sender.sendMessage(ConduitShopPlugin.info(
                "Shop config economy = " + configuredLabel + "; charging via: " + usingNow));
        sender.sendMessage(ConduitShopPlugin.info(
                "Server owner: set 'economy' in this plugin's config.yml to one of the names above."));
        return true;
    }
}

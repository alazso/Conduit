package so.alaz.conduit.bridge.essentialsx;

import net.ess3.api.IEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;

import java.util.concurrent.Executor;

/**
 * Registers the {@link EssentialsXEconomy} provider with the Conduit registry
 * when both Conduit and EssentialsX are present.
 */
public final class EssentialsXBridgePlugin extends JavaPlugin {

    private EssentialsXEconomy economy;

    @Override
    public void onEnable() {
        Plugin essPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (!(essPlugin instanceof IEssentials essentials)) {
            getComponentLogger().error(Component.text(
                    "EssentialsX not found; the Conduit EssentialsX bridge will not register.",
                    NamedTextColor.RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Executor asyncExecutor = task -> getServer().getAsyncScheduler().runNow(this, scheduled -> task.run());
        this.economy = new EssentialsXEconomy(new EssentialsXBackend(essentials), asyncExecutor);

        Conduit.getRegistry().register(Economy.class, economy, this, ServicePriority.Normal);
        getComponentLogger().info(Component.text(
                "Registered EssentialsX economy provider with Conduit.", NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            Conduit.getRegistry().unregister(Economy.class, economy);
        }
    }
}

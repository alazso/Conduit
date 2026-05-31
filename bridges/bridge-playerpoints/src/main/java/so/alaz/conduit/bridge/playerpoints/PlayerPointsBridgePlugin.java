package so.alaz.conduit.bridge.playerpoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;

import java.util.concurrent.Executor;

/**
 * Registers the {@link PlayerPointsEconomy} provider with the Conduit registry
 * when both Conduit and PlayerPoints are present.
 */
public final class PlayerPointsBridgePlugin extends JavaPlugin {

    private PlayerPointsEconomy economy;

    @Override
    public void onEnable() {
        Plugin ppPlugin = getServer().getPluginManager().getPlugin("PlayerPoints");
        if (!(ppPlugin instanceof PlayerPoints playerPoints)) {
            getComponentLogger().error(Component.text(
                    "PlayerPoints not found; the Conduit PlayerPoints bridge will not register.",
                    NamedTextColor.RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerPointsAPI api = playerPoints.getAPI();
        if (api == null) {
            getComponentLogger().error(Component.text(
                    "PlayerPoints API unavailable; the Conduit PlayerPoints bridge will not register.",
                    NamedTextColor.RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Executor asyncExecutor = task -> getServer().getAsyncScheduler().runNow(this, scheduled -> task.run());
        this.economy = new PlayerPointsEconomy(new PlayerPointsBackendImpl(api), asyncExecutor);

        Conduit.getRegistry().register(Economy.class, economy, this, ServicePriority.Normal);
        getComponentLogger().info(Component.text(
                "Registered PlayerPoints economy provider with Conduit.", NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            Conduit.getRegistry().unregister(Economy.class, economy);
        }
    }
}

package so.alaz.conduit.bridge.template;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;

/**
 * Reference bridge plugin: registers the {@link TemplateEconomy} provider with
 * Conduit on enable and unregisters it on disable. Copy this module and swap in
 * your backend to publish a community bridge.
 */
public final class TemplateBridgePlugin extends JavaPlugin {

    private TemplateEconomy economy;

    @Override
    public void onEnable() {
        this.economy = new TemplateEconomy();
        Conduit.getRegistry().register(Economy.class, economy, this, ServicePriority.Normal);
        getComponentLogger().info(Component.text(
                "Registered Template economy provider with Conduit.", NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            Conduit.getRegistry().unregister(Economy.class, economy);
        }
    }
}

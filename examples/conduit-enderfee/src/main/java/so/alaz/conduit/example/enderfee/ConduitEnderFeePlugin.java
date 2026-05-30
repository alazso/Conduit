package so.alaz.conduit.example.enderfee;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A consumer-only Conduit example that charges money for an <em>in-game action</em>:
 * opening an ender chest costs a flat fee.
 *
 * <p>It demonstrates the correct async gating pattern. Economy operations are
 * asynchronous, so we cannot block the synchronous interact event to decide
 * whether to allow it. Instead we cancel the default open immediately, withdraw
 * the fee through the decorated {@link Conduit#getEconomy()} facade, and only on
 * a successful debit re-open the chest on the player's own region/entity thread
 * (Folia-safe).
 *
 * <p>This plugin only <em>consumes</em> the economy — it relies on a registered
 * provider being present (e.g. the {@code conduit-economy} example or any bridge).
 */
public final class ConduitEnderFeePlugin extends JavaPlugin implements Listener {

    /** Flat fee charged per ender-chest access. Scale 0 is valid for any currency precision. */
    static final BigDecimal ACCESS_FEE = new BigDecimal("10");

    private static final String WITHDRAW_REASON = "Ender chest access fee";

    private CallerToken callerToken;

    @Override
    public void onEnable() {
        this.callerToken = Conduit.getRegistry().registerCaller(this);
        getServer().getPluginManager().registerEvents(this, this);
        getSLF4JLogger().info("Ender chest access fee active: {} per open.", ACCESS_FEE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) {
            return;
        }

        // Gate the action on payment: suppress the vanilla open and decide async.
        event.setCancelled(true);
        chargeThenOpen(event.getPlayer());
    }

    private void chargeThenOpen(Player player) {
        Economy economy = Conduit.getEconomy();
        UUID id = player.getUniqueId();
        CallerToken.runWith(callerToken, () -> economy
                .withdraw(id, ACCESS_FEE, WITHDRAW_REASON)
                .thenAccept(result -> handle(player, economy, result))
                .exceptionally(throwable -> {
                    player.sendMessage(error("Could not charge the access fee: " + throwable.getMessage()));
                    return null;
                }));
    }

    private void handle(Player player, Economy economy, EconomyResult result) {
        switch (result) {
            case EconomyResult.Success success -> openEnderChest(player, economy, success.newBalance());
            case EconomyResult.InsufficientFunds insufficient -> player.sendMessage(error(
                    "You need " + economy.format(ACCESS_FEE) + " to open your ender chest (you have "
                            + economy.format(insufficient.balance()) + ")."));
            case EconomyResult.AccountNotFound ignored -> player.sendMessage(error(
                    "You don't have an economy account yet."));
            case EconomyResult.CurrencyNotSupported unsupported -> player.sendMessage(error(
                    "Currency not supported: " + unsupported.currency().id() + "."));
            case EconomyResult.ProviderError providerError -> player.sendMessage(error(
                    "Economy error: " + providerError.message()));
        }
    }

    private void openEnderChest(Player player, Economy economy, BigDecimal newBalance) {
        // The debit succeeded off-thread; open the inventory back on the player's
        // own thread so this works correctly under both Paper and Folia.
        player.getScheduler().run(this, task -> {
            player.openInventory(player.getEnderChest());
            player.sendMessage(success("Charged " + economy.format(ACCESS_FEE)
                    + " — balance now " + economy.format(newBalance) + "."));
        }, null);
    }

    private static Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    private static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}

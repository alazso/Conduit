package so.alaz.conduit.example.shop;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.result.EconomyResult;

import java.util.Optional;

/**
 * {@code /buy} — purchases one diamond, charging the economy the server owner
 * configured for this shop. The player makes no currency choice; the owner's
 * {@code config.yml} selection decides the tender.
 */
final class BuyCommand extends Command {

    private final ConduitShopPlugin plugin;

    BuyCommand(ConduitShopPlugin plugin) {
        super("buy");
        this.plugin = plugin;
        setDescription("Buy a diamond using the shop's configured economy.");
        setUsage("/buy");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player buyer)) {
            sender.sendMessage(ConduitShopPlugin.error("Only players can buy."));
            return true;
        }

        Optional<Economy> resolved = plugin.resolveConfiguredEconomy();
        if (resolved.isEmpty()) {
            buyer.sendMessage(ConduitShopPlugin.error("No economy is available to charge."));
            return true;
        }

        Economy chosen = resolved.get();
        Economy handle = plugin.dispatchHandleFor(chosen);
        try {
            CallerToken.runWith(plugin.callerToken(), () ->
                    handle.withdraw(buyer.getUniqueId(), ConduitShopPlugin.ITEM_PRICE, "Shop purchase: diamond")
                            .thenAccept(result -> report(buyer, chosen, result))
                            .exceptionally(throwable -> {
                                buyer.sendMessage(ConduitShopPlugin.error("Purchase failed: " + throwable.getMessage()));
                                return null;
                            }));
        } catch (IllegalArgumentException e) {
            buyer.sendMessage(ConduitShopPlugin.error("Configured price is invalid for "
                    + chosen.getName() + ": " + e.getMessage()));
        }
        return true;
    }

    private void report(Player buyer, Economy economy, EconomyResult result) {
        switch (result) {
            case EconomyResult.Success success -> grantItem(buyer, economy, success);
            case EconomyResult.InsufficientFunds insufficient -> buyer.sendMessage(ConduitShopPlugin.error(
                    "You need " + economy.format(ConduitShopPlugin.ITEM_PRICE) + " (you have "
                            + economy.format(insufficient.balance()) + ")."));
            case EconomyResult.AccountNotFound ignored -> buyer.sendMessage(ConduitShopPlugin.error(
                    "You don't have an account in " + economy.getName() + "."));
            case EconomyResult.CurrencyNotSupported unsupported -> buyer.sendMessage(ConduitShopPlugin.error(
                    "Currency not supported: " + unsupported.currency().id() + "."));
            case EconomyResult.Rejected rejected -> buyer.sendMessage(ConduitShopPlugin.error(
                    "Purchase blocked: " + rejected.reason()));
            case EconomyResult.ProviderError providerError -> buyer.sendMessage(ConduitShopPlugin.error(
                    "Economy error: " + providerError.message()));
        }
    }

    private void grantItem(Player buyer, Economy economy, EconomyResult.Success success) {
        // The debit succeeded off-thread; mutate inventory on the player's own
        // thread so this is correct under both Paper and Folia.
        buyer.getScheduler().run(plugin, task -> {
            buyer.getInventory().addItem(new ItemStack(Material.DIAMOND));
            buyer.sendMessage(ConduitShopPlugin.success(
                    "Bought a diamond for " + economy.format(ConduitShopPlugin.ITEM_PRICE)
                            + " via " + economy.getName()
                            + " (balance now " + economy.format(success.newBalance()) + ")."));
        }, null);
    }
}

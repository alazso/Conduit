package so.alaz.conduit.example.economy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;

/**
 * {@code /pay <player> <amount>} — transfers funds between two online players in
 * this plugin's own economy, keeping its balances independent of any other
 * registered economy.
 */
final class PayCommand extends Command {

    private final ConduitEconomyPlugin plugin;

    PayCommand(ConduitEconomyPlugin plugin) {
        super("pay");
        this.plugin = plugin;
        setDescription("Pay another online player from your Conduit example-economy balance.");
        setUsage("/pay <player> <amount>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage(ConduitEconomyPlugin.error("Only players can pay."));
            return true;
        }
        if (args.length != 2) {
            payer.sendMessage(ConduitEconomyPlugin.error("Usage: /pay <player> <amount>"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            payer.sendMessage(ConduitEconomyPlugin.error("Player '" + args[0] + "' is not online."));
            return true;
        }
        if (target.equals(payer)) {
            payer.sendMessage(ConduitEconomyPlugin.error("You cannot pay yourself."));
            return true;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]);
        } catch (NumberFormatException e) {
            payer.sendMessage(ConduitEconomyPlugin.error("'" + args[1] + "' is not a valid amount."));
            return true;
        }

        Economy eco = plugin.economy();
        try {
            CallerToken.runWith(plugin.callerToken(), () ->
                    eco.transfer(payer.getUniqueId(), target.getUniqueId(), amount)
                            .thenAccept(result -> report(payer, target, eco, amount, result))
                            .exceptionally(throwable -> {
                                payer.sendMessage(ConduitEconomyPlugin.error("Transfer failed: " + throwable.getMessage()));
                                return null;
                            }));
        } catch (IllegalArgumentException e) {
            // Synchronous boundary validation (null/zero/negative/scale overflow).
            payer.sendMessage(ConduitEconomyPlugin.error("Invalid amount: " + e.getMessage()));
        }
        return true;
    }

    private void report(Player payer, Player target, Economy eco, BigDecimal amount, EconomyResult result) {
        switch (result) {
            case EconomyResult.Success success -> {
                payer.sendMessage(ConduitEconomyPlugin.success(
                        "Paid " + eco.format(amount) + " to " + target.getName()
                                + " (new balance " + eco.format(success.newBalance()) + ")."));
                target.sendMessage(ConduitEconomyPlugin.success(
                        "Received " + eco.format(amount) + " from " + payer.getName() + "."));
            }
            case EconomyResult.InsufficientFunds insufficient -> payer.sendMessage(ConduitEconomyPlugin.error(
                    "Insufficient funds: you have " + eco.format(insufficient.balance()) + "."));
            case EconomyResult.AccountNotFound notFound -> payer.sendMessage(
                    ConduitEconomyPlugin.error("No account found for " + notFound.uuid() + "."));
            case EconomyResult.CurrencyNotSupported unsupported -> payer.sendMessage(
                    ConduitEconomyPlugin.error("Currency not supported: " + unsupported.currency().id() + "."));
            case EconomyResult.Rejected rejected -> payer.sendMessage(
                    ConduitEconomyPlugin.error("Payment blocked: " + rejected.reason()));
            case EconomyResult.ProviderError providerError -> payer.sendMessage(
                    ConduitEconomyPlugin.error("Provider error: " + providerError.message()));
        }
    }
}

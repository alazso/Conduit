package so.alaz.conduit.example.points;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;

/**
 * {@code /sendpoints <player> <amount>} — transfers whole points between two
 * online players via this plugin's own points provider. Non-integer amounts are
 * rejected because the points currency permits zero decimal places.
 */
final class PointsPayCommand extends Command {

    private final ConduitPointsPlugin plugin;

    PointsPayCommand(ConduitPointsPlugin plugin) {
        super("sendpoints");
        this.plugin = plugin;
        setDescription("Send points to another online player.");
        setUsage("/sendpoints <player> <amount>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage(ConduitPointsPlugin.error("Only players can send points."));
            return true;
        }
        if (args.length != 2) {
            payer.sendMessage(ConduitPointsPlugin.error("Usage: /sendpoints <player> <amount>"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            payer.sendMessage(ConduitPointsPlugin.error("Player '" + args[0] + "' is not online."));
            return true;
        }
        if (target.equals(payer)) {
            payer.sendMessage(ConduitPointsPlugin.error("You cannot send points to yourself."));
            return true;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]);
        } catch (NumberFormatException e) {
            payer.sendMessage(ConduitPointsPlugin.error("'" + args[1] + "' is not a valid amount."));
            return true;
        }

        Economy eco = plugin.economy();
        try {
            CallerToken.runWith(plugin.callerToken(), () ->
                    eco.transfer(payer.getUniqueId(), target.getUniqueId(), amount)
                            .thenAccept(result -> report(payer, target, eco, amount, result))
                            .exceptionally(throwable -> {
                                payer.sendMessage(ConduitPointsPlugin.error("Transfer failed: " + throwable.getMessage()));
                                return null;
                            }));
        } catch (IllegalArgumentException e) {
            payer.sendMessage(ConduitPointsPlugin.error("Invalid amount (points are whole numbers): " + e.getMessage()));
        }
        return true;
    }

    private void report(Player payer, Player target, Economy eco, BigDecimal amount, EconomyResult result) {
        switch (result) {
            case EconomyResult.Success success -> {
                payer.sendMessage(ConduitPointsPlugin.success(
                        "Sent " + eco.format(amount) + " to " + target.getName()
                                + " (you now have " + eco.format(success.newBalance()) + ")."));
                target.sendMessage(ConduitPointsPlugin.success(
                        "Received " + eco.format(amount) + " from " + payer.getName() + "."));
            }
            case EconomyResult.InsufficientFunds insufficient -> payer.sendMessage(ConduitPointsPlugin.error(
                    "Not enough points: you have " + eco.format(insufficient.balance()) + "."));
            case EconomyResult.AccountNotFound notFound -> payer.sendMessage(
                    ConduitPointsPlugin.error("No account found for " + notFound.uuid() + "."));
            case EconomyResult.CurrencyNotSupported unsupported -> payer.sendMessage(
                    ConduitPointsPlugin.error("Currency not supported: " + unsupported.currency().id() + "."));
            case EconomyResult.Rejected rejected -> payer.sendMessage(
                    ConduitPointsPlugin.error("Payment blocked: " + rejected.reason()));
            case EconomyResult.ProviderError providerError -> payer.sendMessage(
                    ConduitPointsPlugin.error("Provider error: " + providerError.message()));
        }
    }
}

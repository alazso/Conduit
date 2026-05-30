package so.alaz.conduit.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.registry.ProviderInfo;
import so.alaz.conduit.core.ConduitPlugin;
import so.alaz.conduit.core.scheduler.SchedulerAdapter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Operator command surface: {@code /conduit info} and
 * {@code /conduit economy <info|providers|balance>}.
 */
@ApiStatus.Internal
public final class ConduitCommand extends Command {

    private static final String PERMISSION = "conduit.admin";

    private final ConduitPlugin plugin;

    public ConduitCommand(@NotNull ConduitPlugin plugin) {
        super("conduit");
        this.plugin = plugin;
        setDescription("Conduit administration and economy inspection.");
        setUsage("/conduit <info|economy>");
        setPermission(PERMISSION);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "You lack permission (" + PERMISSION + ").", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            info(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("economy")) {
            economy(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        send(sender, "Unknown subcommand. Usage: " + getUsage(), NamedTextColor.RED);
        return true;
    }

    private void info(CommandSender sender) {
        send(sender, "Conduit " + plugin.getPluginMeta().getVersion(), NamedTextColor.AQUA);
        ProviderInfo<Economy> economyInfo = plugin.registry().getProviderInfo(Economy.class);
        send(sender, "Economy providers registered: " + economyInfo.allProviders().size(), NamedTextColor.GRAY);
        economyInfo.activeProvider().ifPresentOrElse(
                e -> send(sender, "Active economy: " + e.getName(), NamedTextColor.GREEN),
                () -> send(sender, "Active economy: none", NamedTextColor.YELLOW));
    }

    private void economy(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "info" -> economyInfo(sender);
            case "providers" -> economyProviders(sender);
            case "balance" -> economyBalance(sender, args);
            default -> send(sender, "Usage: /conduit economy <info|providers|balance>", NamedTextColor.RED);
        }
    }

    private void economyInfo(CommandSender sender) {
        Optional<Economy> economy = Conduit.findEconomy();
        if (economy.isEmpty()) {
            send(sender, "No active economy provider.", NamedTextColor.YELLOW);
            return;
        }
        Economy e = economy.get();
        send(sender, "Active economy: " + e.getName(), NamedTextColor.GREEN);
        send(sender, "Default currency: " + e.defaultCurrency().symbol()
                + " (" + e.defaultCurrency().decimalPlaces() + " dp)", NamedTextColor.GRAY);
        String caps = e.capabilities().stream().map(Enum::name).collect(Collectors.joining(", "));
        send(sender, "Capabilities: " + (caps.isEmpty() ? "none" : caps), NamedTextColor.GRAY);
    }

    private void economyProviders(CommandSender sender) {
        ProviderInfo<Economy> info = plugin.registry().getProviderInfo(Economy.class);
        if (info.allProviders().isEmpty()) {
            send(sender, "No economy providers registered.", NamedTextColor.YELLOW);
            return;
        }
        String active = info.activeProvider().map(Economy::getName).orElse(null);
        for (Economy provider : info.allProviders()) {
            boolean isActive = provider.getName().equals(active);
            send(sender, (isActive ? "* " : "  ") + provider.getName(),
                    isActive ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        }
    }

    private void economyBalance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Usage: /conduit economy balance <player|uuid>", NamedTextColor.RED);
            return;
        }
        Optional<Economy> economy = Conduit.findEconomy();
        if (economy.isEmpty()) {
            send(sender, "No active economy provider.", NamedTextColor.YELLOW);
            return;
        }
        Optional<UUID> target = resolve(args[1]);
        if (target.isEmpty()) {
            send(sender, "Unknown player: " + args[1], NamedTextColor.RED);
            return;
        }
        Economy e = economy.get();
        SchedulerAdapter scheduler = plugin.scheduler();
        e.getBalance(target.get()).thenAccept(balance ->
                scheduler.runGlobal(() -> send(sender,
                        args[1] + ": " + e.defaultCurrency().format(balance.amount()), NamedTextColor.GREEN)));
    }

    private Optional<UUID> resolve(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID; fall through to name resolution.
        }
        var online = plugin.getServer().getPlayerExact(token);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(token);
        return Optional.ofNullable(cached).map(OfflinePlayer::getUniqueId);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("info", "economy"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("economy")) {
            return filter(List.of("info", "providers", "balance"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }

    private void send(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }
}

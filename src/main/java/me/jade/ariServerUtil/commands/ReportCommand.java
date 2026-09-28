package me.jade.ariServerUtil.commands;

import me.jade.ariServerUtil.AriServerUtil;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class ReportCommand implements CommandExecutor {
    private final AriServerUtil plugin;

    public ReportCommand(AriServerUtil plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can report.");
            return true;
        }
        if (!player.hasPermission(Permissions.REPORT)) {
            Text.send(player, plugin.configs().message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /report <player> <reason>");
            return true;
        }
        String targetName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.identities().resolve(targetName).thenAccept(found -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (found.isEmpty()) {
                Text.send(player, plugin.configs().message("player-not-found"));
            } else {
                plugin.reports().submit(player, found.get(), reason);
            }
        }));
        return true;
    }
}

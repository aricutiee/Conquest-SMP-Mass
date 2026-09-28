package me.jade.ariServerUtil.commands;

import me.jade.ariServerUtil.AriServerUtil;
import me.jade.ariServerUtil.model.ReportStatus;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ReportsCommand implements CommandExecutor {
    private final AriServerUtil plugin;

    public ReportsCommand(AriServerUtil plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can open reports.");
            return true;
        }
        if (!player.hasPermission(Permissions.REPORTS)) {
            Text.send(player, plugin.configs().message("no-permission"));
            return true;
        }
        plugin.screens().openReports(player, ReportStatus.OPEN, 0);
        return true;
    }
}

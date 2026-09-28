package me.jade.ariServerUtil.commands;

import me.jade.ariServerUtil.AriServerUtil;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffModeCommand implements CommandExecutor {
    private final AriServerUtil plugin;

    public StaffModeCommand(AriServerUtil plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can use Staff Mode.");
            return true;
        }
        if (!player.hasPermission(Permissions.STAFFMODE)) {
            Text.send(player, plugin.configs().message("no-permission"));
            return true;
        }
        plugin.staffMode().toggle(player);
        return true;
    }
}

package me.jade.ariServerUtil.commands;

import me.jade.ariServerUtil.AriServerUtil;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffChatCommand implements CommandExecutor {
    private final AriServerUtil plugin;

    public StaffChatCommand(AriServerUtil plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can use staff chat.");
            return true;
        }
        if (!player.hasPermission(Permissions.STAFFCHAT)) {
            Text.send(player, plugin.configs().message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            plugin.chatControls().toggleStaffChat(player);
        } else {
            plugin.chatControls().staffChat(player, String.join(" ", args));
        }
        return true;
    }
}

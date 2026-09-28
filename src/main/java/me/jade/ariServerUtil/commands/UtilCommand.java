package me.jade.ariServerUtil.commands;

import me.jade.ariServerUtil.AriServerUtil;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class UtilCommand implements CommandExecutor, TabCompleter {
    private final AriServerUtil plugin;

    public UtilCommand(AriServerUtil plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("chat")) {
            if (!sender.hasPermission(Permissions.CHAT_LOCK)) {
                Text.send(sender, plugin.configs().message("no-permission"));
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("unlock")) {
                if (sender instanceof Player staff) plugin.chatControls().setLocked(staff, false);
                else plugin.chatControls().unlockFromConsole();
            }
            sender.sendMessage(plugin.chatControls().status());
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(Permissions.RELOAD)) {
                Text.send(sender, plugin.configs().message("no-permission"));
                return true;
            }
            plugin.reloadServerUtil();
            Text.send(sender, plugin.configs().message("reload"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can open the GUI.");
            return true;
        }
        plugin.screens().openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(Permissions.RELOAD)) {
            return List.of("reload");
        }
        return List.of();
    }
}

package dev.biomeraces;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class RaceCommand implements CommandExecutor, TabCompleter {
    private final RaceModule plugin;
    RaceCommand(RaceModule plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("roll")) {
            if (!sender.hasPermission("conquest.races.roll")) { plugin.message(sender, "no-permission"); return true; }
            if (args.length != 0) return false;
            if (sender instanceof Player player) plugin.rolls().start(player, true); else plugin.message(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { plugin.message(sender, "player-only"); return true; }
            if (plugin.state(player).base != null) info(player); else plugin.rolls().start(player, false);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("info") && args.length == 1) {
            if (sender instanceof Player player) info(player); else plugin.message(sender, "player-only"); return true;
        }
        if (!List.of("set", "reset", "reload").contains(sub)) return false;
        if (!admin(sender, sub)) { plugin.message(sender, "no-permission"); return true; }
        if (sub.equals("reload")) {
            if (args.length != 1) return false;
            try { plugin.reloadSettings(); plugin.message(sender, "reloaded"); }
            catch (Exception ex) { plugin.getLogger().severe("Reload rejected: " + ex.getMessage()); plugin.message(sender, "reload-failed"); }
            return true;
        }
        if (args.length != (sub.equals("set") ? 3 : 2)) return false;
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID uuid = target == null ? plugin.store().lookup(args[1]) : target.getUniqueId();
        if (uuid == null) { plugin.message(sender, "player-not-found"); return true; }
        Race race = sub.equals("set") ? Race.parse(args[2]) : null;
        if (sub.equals("set") && (race == null || race == Race.DRAGONBORN)) { plugin.message(sender, "invalid-race"); return true; }
        if (!setBase(uuid, race)) { plugin.message(sender, "roll-save-failed"); return true; }
        plugin.message(sender, race == null ? "reset" : "selected", "<race>", race == null ? "" : race.label());
        if (target != null && !target.equals(sender)) plugin.message(target, race == null ? "reset" : "selected", "<race>", race == null ? "" : race.label());
        return true;
    }
    private static boolean admin(CommandSender sender, String action) {
        return sender.isOp() || sender.hasPermission("biomeraces.admin." + action);
    }
    private boolean setBase(UUID uuid, Race race) {
        PlayerState state = plugin.store().get(uuid); Race old = state.base, pending = state.pendingRoll;
        state.base = race; state.pendingRoll = null;
        if (!plugin.persist()) { state.base = old; state.pendingRoll = pending; return false; }
        plugin.rolls().cancel(uuid); state.chain.reset();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.effects().clearSelf(player); plugin.runtime().updatePassives(player);
            if (race == null) plugin.rolls().queueAssignment(player);
        }
        return true;
    }
    private void info(Player player) {
        plugin.runtime().syncHands(player); PlayerState state = plugin.state(player); Race active = state.active();
        plugin.message(player, "info-heading", "<race>", active == null ? plugin.settings().message("none") : active.label());
        if (state.dragon) plugin.message(player, "info-base", "<race>", state.base == null ? plugin.settings().message("none") : state.base.label());
        if (active != null) for (String line : plugin.settings().yaml.getStringList(active.key() + ".description")) player.sendMessage(MiniMessage.miniMessage().deserialize(line));
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("roll") || args.length == 0) return List.of();
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.add("info");
            for (String sub : List.of("set", "reset", "reload")) if (admin(sender, sub)) values.add(sub);
        } else if (args.length == 2 && List.of("set", "reset").contains(args[0]) && admin(sender, args[0])) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (args.length == 3 && args[0].equals("set") && admin(sender, "set")) {
            RollSettings.BASE.forEach(race -> values.add(race.key()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}

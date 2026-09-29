package dev.turtleroles.anticheat;

import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

/** Optional integration: no global race/event movement bypasses are ever granted. */
public final class ConquestGrimIntegration implements Listener, CommandExecutor, AutoCloseable {
    private final JavaPlugin plugin;
    private Binding binding;
    private final ConquestIntel intel;
    interface Binding extends AutoCloseable {
        void status(CommandSender sender);
        @Override void close();
    }
    public ConquestGrimIntegration(JavaPlugin plugin) { this.plugin = plugin; this.intel = new ConquestIntel(plugin); }
    public void start() {
        Objects.requireNonNull(plugin.getCommand("conquestac")).setExecutor(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        intel.start();
        connect();
    }
    private void connect() {
        if (binding != null || !plugin.getServer().getPluginManager().isPluginEnabled("GrimAC")) return;
        try {
            binding = GrimBinding.connect(plugin, intel);
            intel.grimConnected(true);
            plugin.getLogger().info("GrimAC integration active: automatic console punishments blocked; movement/reach checks remain enabled.");
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Grim integration could not attach. Check /conquestac and Grim's punishments.yml before accepting players.", ex);
        }
    }
    @EventHandler public void enabled(PluginEnableEvent event) { if (event.getPlugin().getName().equals("GrimAC")) connect(); }
    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("GrimAC") && binding != null) { binding.close(); binding = null; intel.grimConnected(false); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("conquest.anticheat.status")) { sender.sendMessage("You do not have permission."); return true; }
        if (args.length != 0) return intel.command(sender,args);
        if(sender instanceof org.bukkit.entity.Player) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Conquest AC",net.kyori.adventure.text.format.TextColor.color(0xB477FF)));
            sender.sendMessage("Check a player: /conquestac <player>");
            sender.sendMessage("Detailed evidence: /conquestac details <player>");
            return true;
        }
        intel.status(sender);
        if (binding == null) sender.sendMessage("ConquestAC: Grim is absent or the integration failed. Check startup logs.");
        else binding.status(sender);
        if (plugin.getServer().getPluginManager().isPluginEnabled("MLSAC"))
            sender.sendMessage("Warning: MLSAC is also active. Overlapping movement checks can cause conflicting corrections.");
        return true;
    }
    @Override public void close() {
        if (binding != null) { binding.close(); binding = null; intel.grimConnected(false); }
        intel.close();
        HandlerList.unregisterAll(this);
    }
}

package dev.turtleroles.anticheat;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.GrimEventListener;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.atomic.AtomicLong;

/** API callbacks run on Grim's packet threads: no player/world Bukkit calls here. */
final class GrimBinding implements ConquestGrimIntegration.Binding {
    private final JavaPlugin plugin;
    private final GrimAbstractAPI api;
    private final AtomicLong blocked = new AtomicLong();
    private final GrimEventListener<CommandExecuteEvent> listener = this::action;
    static GrimBinding connect(JavaPlugin plugin) { return new GrimBinding(plugin, GrimAPIProvider.get()); }
    GrimBinding(JavaPlugin plugin, GrimAbstractAPI api) {
        this.plugin = plugin; this.api = java.util.Objects.requireNonNull(api, "Grim API is not ready");
        api.getEventBus().subscribe(plugin, CommandExecuteEvent.class, listener);
    }
    void action(CommandExecuteEvent event) {
        if (!GrimPunishmentPolicy.allows(event.getCommand())) {
            event.setCancelled(true);
            if (blocked.incrementAndGet() == 1) plugin.getLogger().warning("Blocked an automatic Grim action. Conquest allows alerts/logging, not console punishments. Check GrimAC/punishments.yml and the required alert prefix in messages.yml.");
        }
    }
    @Override public void status(CommandSender sender) {
        sender.sendMessage("ConquestAC: connected to GrimAC " + api.getGrimVersion());
        sender.sendMessage("Automatic console punishments: BLOCKED. Alerts/history and movement/reach corrections: allowed.");
        sender.sendMessage("Global movement exemptions: none. Suppressed automatic commands: " + blocked.get());
        var config = api.getConfigManager();
        sender.sendMessage("Simulation tolerance: " + config.getDoubleElse("Simulation.threshold", .001)
            + "; correction violation threshold: " + config.getDoubleElse("Simulation.setback-violation-threshold", 1));
        sender.sendMessage("Impossible reach hits blocked: " + config.getBooleanElse("Reach.block-impossible-hits", true));
        String format = config.getStringElse("alerts-format", "");
        if (!format.startsWith(GrimPunishmentPolicy.ALERT_PREFIX)) sender.sendMessage("Warning: Grim alerts-format must start with [ConquestAC] followed by a space for the notification guard.");
        sender.sendMessage("Connection timeouts, malformed-packet errors and packet-flood disconnections remain possible; these are not violation punishments.");
    }
    @Override public void close() { api.getEventBus().unregisterListener(plugin, listener); }
}

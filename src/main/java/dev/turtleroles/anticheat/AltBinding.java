package dev.turtleroles.anticheat;

import com.altdetector.events.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses the upstream event API; never changes identity or punishes a match. */
final class AltBinding implements Listener, AutoCloseable {
    private final ConquestIntel intel;
    AltBinding(JavaPlugin plugin, ConquestIntel intel) {
        this.intel=intel;
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void detected(AltDetectedEvent event) {
        var r=event.getResult();
        String links=r.linkedAccounts().stream().limit(12).map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
        // Store only known categorical tags. An upstream tag may contain an IP.
        String detail="Suspected match, not proof. Score="+String.format(java.util.Locale.ROOT,"%.2f",r.riskScore())
            +"; level="+r.level()+"; linked UUIDs="+links;
        intel.record(r.joiningPlayer(),r.joiningUsername(),"ALT",detail,true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void ban(AltBanEvent event) { event.setCancelled(true); }
    @Override public void close() {HandlerList.unregisterAll(this);}
}

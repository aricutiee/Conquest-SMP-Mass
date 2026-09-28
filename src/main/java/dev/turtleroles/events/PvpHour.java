package dev.turtleroles.events;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/** Independent locator window; never changes combat or competitive-event rewards. */
final class PvpHour implements Listener {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final PvpHourClock clock;
    private final BukkitTask task;

    PvpHour(JavaPlugin plugin, YamlConfiguration settings) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "pvp-hour-state.yml");
        data = YamlConfiguration.loadConfiguration(file);
        // Retire a previously automated window once. Later manual windows survive restarts.
        clock = new PvpHourClock(System.currentTimeMillis(), data.getBoolean("manual-only", false) ? data.getLong("ends-at") : 0, 0,
                Math.max(1, settings.getLong("pvp-hour.duration-seconds", 3600)) * 1000,
                Math.max(1, settings.getLong("pvp-hour.interval-seconds", 14400)) * 1000);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        apply(); save();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
        plugin.getLogger().info("PvP Hour ready: " + status());
    }
    boolean active() { return clock.active(); }
    String status() {
        long now = System.currentTimeMillis();
        return (active() ? "ON, " + Math.max(0, (clock.endsAt-now+999)/1000) + "s remaining" : "OFF")
                + " | manual starts only";
    }
    boolean start() {
        if (!clock.start(System.currentTimeMillis())) return false;
        save(); apply();
        announce("PVP HOUR HAS STARTED", "The locator bar is on for one hour.", true);
        return true;
    }
    boolean stop() {
        boolean changed = clock.stop();
        save(); apply();
        if (changed) announce("PVP HOUR HAS ENDED", "The locator bar is now off.", false);
        return changed;
    }
    private void tick() {
        long now = System.currentTimeMillis();
        if (clock.expired(now)) stop();
        apply();
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) { apply(event.getWorld(), active()); }
    private void apply() { for (World world : Bukkit.getWorlds()) apply(world, active()); }
    @SuppressWarnings("removal")
    private void apply(World world, boolean enabled) {
        if (!Boolean.valueOf(enabled).equals(world.getGameRuleValue(GameRule.LOCATOR_BAR)))
            world.setGameRule(GameRule.LOCATOR_BAR, enabled);
    }
    private void announce(String title, String detail, boolean sound) {
        Bukkit.broadcast(Component.text(title + ". " + detail, NamedTextColor.GOLD));
        for (var player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Component.text(title, NamedTextColor.RED), Component.text(detail, NamedTextColor.GOLD)));
            if (sound) player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    }
    private void save() {
        data.set("ends-at", clock.endsAt); data.set("next-start", null); data.set("automatic", false); data.set("manual-only", true);
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().severe("Cannot save PvP Hour: " + ex.getMessage()); }
    }
    void shutdown() {
        task.cancel(); save();
        for (World world : Bukkit.getWorlds()) apply(world, false);
    }
}

package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Highest-priority active message wins; event tracking cannot hide critical notices. */
public final class ActionBarBus {
    private static ActionBarBus instance;
    private record Entry(Component text, int priority, long until) {}
    private final Map<UUID, Map<String, Entry>> messages = new HashMap<>();

    public ActionBarBus(JavaPlugin plugin) {
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 5, 5);
    }

    public static void offer(Player player, String source, Component text, int priority, long ttlMillis) {
        if (instance == null) { player.sendActionBar(text); return; }
        instance.messages.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(source, new Entry(text, priority, System.currentTimeMillis() + ttlMillis));
    }

    public static void clear(Player player, String source) {
        if (instance == null) return;
        Map<String, Entry> entries = instance.messages.get(player.getUniqueId());
        if (entries != null) {
            entries.remove(source);
            if (entries.isEmpty()) {
                instance.messages.remove(player.getUniqueId());
                player.sendActionBar(Component.empty());
            }
        }
    }

    private void flush() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Entry> entries = messages.get(player.getUniqueId());
            if (entries == null) continue;
            entries.values().removeIf(entry -> entry.until() < now);
            Entry chosen = entries.values().stream().max(java.util.Comparator.comparingInt(Entry::priority)).orElse(null);
            if (chosen != null) player.sendActionBar(chosen.text());
            if (entries.isEmpty()) messages.remove(player.getUniqueId());
        }
    }

    public void shutdown() { messages.clear(); instance = null; }
}

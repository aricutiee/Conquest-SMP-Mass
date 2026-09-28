package me.jade.ariServerUtil.chatinput;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ChatInputService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public ChatInputService(JavaPlugin plugin, ServerUtilConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public void prompt(Player player, String prompt, Consumer<Response> callback, Runnable reopenPrevious) {
        cancel(player.getUniqueId(), false);
        long token = generation.incrementAndGet();
        Duration timeout = config.duration("chat-input.timeout", "60sec");
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session removed = sessions.remove(player.getUniqueId());
            if (removed != null && removed.token == token) {
                Text.send(player, config.message("chat-input.timeout"));
                removed.reopenPrevious.run();
            }
        }, Math.max(1L, timeout.toSeconds() * 20L));
        sessions.put(player.getUniqueId(), new Session(token, callback, reopenPrevious, task));
        Text.send(player, prompt);
        Text.send(player, config.message("chat-input.cancel-help"));
    }

    public void cancel(UUID uuid, boolean notify) {
        Session session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        session.timeout.cancel();
        Player player = Bukkit.getPlayer(uuid);
        if (notify && player != null) {
            Text.send(player, config.message("chat-input.cancelled"));
            session.reopenPrevious.run();
        }
    }

    public void invalidateAll() {
        generation.incrementAndGet();
        sessions.values().forEach(session -> session.timeout.cancel());
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String input = Text.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session current = sessions.remove(player.getUniqueId());
            if (current == null || current.token != session.token) {
                return;
            }
            current.timeout.cancel();
            if (input.equalsIgnoreCase("cancel")) {
                Text.send(player, config.message("chat-input.cancelled"));
                current.reopenPrevious.run();
                return;
            }
            current.callback.accept(new Response(player, input, current.reopenPrevious));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }

    public record Response(Player player, String input, Runnable reopenPrevious) {
        public void invalid(String message) {
            Text.send(player, message);
            reopenPrevious.run();
        }
    }

    private record Session(long token, Consumer<Response> callback, Runnable reopenPrevious, BukkitTask timeout) {
    }
}

package me.jade.ariServerUtil.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.chatinput.ChatInputService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.DurationParser;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.SlowModeTracker;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatControlService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private final ChatInputService chatInput;
    private final SlowModeTracker slowMode = new SlowModeTracker(Clock.systemUTC());
    private final Set<UUID> staffChatToggle = ConcurrentHashMap.newKeySet();
    private volatile boolean locked;
    private volatile Duration slowDelay = Duration.ZERO;
    private volatile List<Pattern> filters = List.of();

    public ChatControlService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit, ChatInputService chatInput) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        this.chatInput = chatInput;
        reloadState();
    }

    public boolean locked() {
        return locked;
    }

    public Duration slowDelay() {
        return slowDelay;
    }

    public String status() {
        return "Chat locked=" + locked + ", slowmode=" + slowDelay.toSeconds() + "s, filters=" + filters.size();
    }

    public void unlockFromConsole() {
        locked = false;
        save("chat_locked", "false");
        plugin.getLogger().info("Chat unlocked by server console; individual mutes and authentication remain enforced.");
    }

    public void setLocked(Player staff, boolean locked) {
        this.locked = locked;
        save("chat_locked", Boolean.toString(locked));
        Bukkit.broadcast(Text.mm((locked ? config.message("chat.locked") : config.message("chat.unlocked")).replace("<staff>", staff.getName())));
        audit.record(staff, locked ? "CHAT_LOCK" : "CHAT_UNLOCK", null, "", "", "success", "");
    }

    public void setSlow(Player staff, Duration delay) {
        this.slowDelay = delay;
        save("chat_slow", Long.toString(delay.toMillis()));
        Text.send(staff, "<green>Slow mode set to " + DurationParser.human(delay) + ".");
        audit.record(staff, "CHAT_SLOWMODE", null, "", DurationParser.human(delay), "success", "");
    }

    public void clear(Player staff) {
        for (int i = 0; i < config.integer("chat.clear-lines", 120, 10, 500); i++) {
            Bukkit.broadcast(net.kyori.adventure.text.Component.empty());
        }
        Bukkit.broadcast(Text.mm(config.message("chat.cleared").replace("<staff>", staff.getName())));
        audit.record(staff, "CHAT_CLEAR", null, "", "", "success", "");
    }

    public void staffChat(Player sender, String message) {
        String formatted = config.message("staffchat.format").replace("<staff>", sender.getName()).replace("<message>", message);
        Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission(Permissions.STAFFCHAT)).forEach(player -> Text.send(player, formatted));
        if (config.bool("staffchat.log", true)) {
            audit.record(sender, "STAFFCHAT", null, "", "redacted staff chat", "sent", "");
        }
    }

    public void toggleStaffChat(Player player) {
        if (!staffChatToggle.add(player.getUniqueId())) {
            staffChatToggle.remove(player.getUniqueId());
            Text.send(player, config.message("staffchat.toggle-off"));
        } else {
            Text.send(player, config.message("staffchat.toggle-on"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled() || chatInput.hasSession(event.getPlayer().getUniqueId())) {
            return;
        }
        Player player = event.getPlayer();
        String plain = Text.plain(event.message());
        if (staffChatToggle.contains(player.getUniqueId()) && player.hasPermission(Permissions.STAFFCHAT)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> staffChat(player, plain));
            return;
        }
        if (locked && !player.hasPermission(Permissions.CHAT_BYPASS)) {
            event.setCancelled(true);
            Text.send(player, config.message("chat.lock-blocked"));
            return;
        }
        if (!slowDelay.isZero() && !player.hasPermission(Permissions.CHAT_BYPASS)) {
            Duration remaining = slowMode.remaining(player.getUniqueId(), slowDelay);
            if (!remaining.isZero()) {
                event.setCancelled(true);
                Text.send(player, config.message("chat.slow-blocked").replace("<remaining>", DurationParser.human(remaining)));
                return;
            }
            slowMode.tryMark(player.getUniqueId(), slowDelay);
        }
        if (!player.hasPermission(Permissions.CHAT_FILTER_BYPASS)) {
            for (Pattern pattern : filters) {
                if (pattern.matcher(plain).find()) {
                    event.setCancelled(true);
                    Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission(Permissions.CHAT_FILTER)).forEach(staff ->
                            Text.send(staff, config.message("chat.filter-notify").replace("<player>", player.getName())));
                    audit.record(player, "CHAT_FILTER", player.getUniqueId(), player.getName(), "redacted", "blocked", "");
                    return;
                }
            }
        }
    }

    public void reloadState() {
        slowDelay = Duration.ofMillis(Long.parseLong(load("chat_slow", "0")));
        locked = Boolean.parseBoolean(load("chat_locked", "false"));
        List<Pattern> compiled = new ArrayList<>();
        for (String expression : config.list("chat.filter.patterns")) {
            if (expression.length() > 80) {
                plugin.getLogger().warning("Ignoring long chat filter pattern.");
                continue;
            }
            try {
                compiled.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException ex) {
                plugin.getLogger().warning("Ignoring invalid chat filter pattern: " + expression);
            }
        }
        filters = compiled;
    }

    private void save(String key, String value) {
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO state(key,value) VALUES(?,?)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        });
    }

    private String load(String key, String fallback) {
        try {
            return database.query(conn -> {
                try (var ps = conn.prepareStatement("SELECT value FROM state WHERE key=?")) {
                    ps.setString(1, key);
                    try (var rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : fallback;
                    }
                }
            }).join();
        } catch (Exception ex) {
            return fallback;
        }
    }
}

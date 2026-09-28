package dev.turtleroles.listener;

import dev.turtleroles.config.TurtleConfig;
import dev.turtleroles.punishment.PunishmentCase;
import dev.turtleroles.service.PresentationService;
import dev.turtleroles.service.PunishmentService;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

public final class ChatAndCommandListener implements Listener {
    private final Plugin plugin;
    private final PlayerRepository players;
    private final PunishmentService punishments;
    private final PresentationService presentation;
    private TurtleConfig config;

    public ChatAndCommandListener(Plugin plugin, PlayerRepository players, PunishmentService punishments, PresentationService presentation, TurtleConfig config) {
        this.plugin = plugin;
        this.players = players;
        this.punishments = punishments;
        this.presentation = presentation;
        this.config = config;
    }

    public void updateConfig(TurtleConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        try {
            PlayerRecord record = players.findByUuid(event.getPlayer().getUniqueId()).orElse(null);
            if (record != null) {
                Optional<PunishmentCase> mute = punishments.activeMute(record);
                if (mute.isPresent()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(muteMessage(mute.get()));
                    return;
                }
            }
            event.renderer((source, sourceDisplayName, message, viewer) -> {
                if (viewer instanceof org.bukkit.entity.Player player) {
                    return presentation.chatNameFor(source, player).append(Component.text(": ")).append(message);
                }
                return presentation.chatNameFor(source, source).append(Component.text(": ")).append(message);
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Mute check failed for chat.", e);
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Chat is temporarily unavailable because moderation storage is down."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String root = event.getMessage().split("\\s+", 2)[0].substring(1).toLowerCase(Locale.ROOT);
        int namespace = root.indexOf(':');
        String bare = namespace >= 0 ? root.substring(namespace + 1) : root;
        if (!config.mutedCommandAliases().contains(bare)) {
            return;
        }
        try {
            PlayerRecord record = players.findByUuid(event.getPlayer().getUniqueId()).orElse(null);
            if (record != null && punishments.activeMute(record).isPresent()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(muteMessage(punishments.activeMute(record).orElseThrow()));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Mute command check failed.", e);
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Commands are temporarily unavailable because moderation storage is down."));
        }
    }

    private Component muteMessage(PunishmentCase mute) {
        String remaining = mute.expiresAt() == null ? "permanent" : Duration.between(Instant.now(), mute.expiresAt()).toMinutes() + " minutes remaining";
        return Component.text("You are muted (" + remaining + "). Reason: " + mute.reason());
    }
}

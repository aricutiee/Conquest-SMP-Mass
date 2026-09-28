package dev.turtleroles.service;

import dev.turtleroles.config.TurtleConfig;
import dev.turtleroles.pack.PackStatus;
import dev.turtleroles.pack.PlayerPackState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ResourcePackService {
    private final Plugin plugin;
    private final PresentationService presentation;
    private final Map<UUID, PlayerPackState> states = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> timeouts = new ConcurrentHashMap<>();
    private TurtleConfig.ResourcePackSettings settings;
    private String revision = "unconfigured";

    public ResourcePackService(Plugin plugin, PresentationService presentation, TurtleConfig.ResourcePackSettings settings) {
        this.plugin = plugin;
        this.presentation = presentation;
        updateSettings(settings);
    }

    public void updateSettings(TurtleConfig.ResourcePackSettings settings) {
        boolean changed = this.settings != null && !this.settings.equals(settings);
        UUID oldId = this.settings == null ? settings.uuid() : this.settings.uuid();
        this.settings = settings;
        this.revision = settings.sha1().isBlank() ? "missing-sha1" : settings.sha1().toLowerCase();
        if (changed) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.removeResourcePack(oldId);
                sendOnJoin(player);
            }
        }
    }

    public void sendOnJoin(Player player) {
        forgetPlayer(player.getUniqueId());
        // Never trust a generic status that may belong to another plugin's pack.
        PackStatus initialStatus = PackStatus.NOT_SENT;
        states.put(player.getUniqueId(), new PlayerPackState(UUID.randomUUID(), revision, initialStatus, 0));
        presentation.setPackStatus(player.getUniqueId(), initialStatus);
        if (settings.enabled() && settings.autoSendOnJoin()) {
            send(player);
        }
    }

    public void send(Player player) {
        if (ClientCompatibility.bedrock(player)) return;
        if (!settings.enabled() || settings.url().isBlank() || settings.sha1().isBlank()) {
            plugin.getLogger().warning("Conquest SMP resource-pack delivery needs an enabled pack, URL and SHA-1. Text fallback remains active.");
            return;
        }
        PlayerPackState old = states.get(player.getUniqueId());
        int attempts = old == null ? 0 : old.attempts();
        if (attempts > settings.maxRetries()) {
            return;
        }
        byte[] hash;
        try {
            hash = HexFormat.of().parseHex(settings.sha1().replace(" ", ""));
            if (hash.length != 20) {
                throw new IllegalArgumentException("SHA-1 must be 20 bytes.");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid TurtleRoles resource-pack SHA-1; text fallback remains active.", e);
            return;
        }
        UUID connection = old == null ? UUID.randomUUID() : old.connectionId();
        states.put(player.getUniqueId(), new PlayerPackState(connection, revision, PackStatus.PENDING, attempts + 1));
        presentation.setPackStatus(player.getUniqueId(), PackStatus.PENDING);
        // Stack our identified pack without clearing packs from other plugins.
        player.addResourcePack(settings.uuid(), settings.url(), hash, settings.prompt(), settings.required());
        cancelTimeout(player.getUniqueId());
        String sentRevision = revision;
        boolean required = settings.required();
        timeouts.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PlayerPackState state = states.get(player.getUniqueId());
            if (state != null && state.connectionId().equals(connection) && state.revision().equals(sentRevision) && state.status() == PackStatus.PENDING) {
                timeouts.remove(player.getUniqueId());
                states.put(player.getUniqueId(), new PlayerPackState(connection, sentRevision, PackStatus.TIMED_OUT, state.attempts()));
                presentation.setPackStatus(player.getUniqueId(), PackStatus.TIMED_OUT);
                presentation.refreshAll();
                if (required && player.isOnline()) {
                    player.kick(Component.text("Please accept the Conquest SMP server resource pack, then rejoin."));
                }
            }
        }, Math.max(20L, settings.timeoutSeconds() * 20L)));
    }

    public void handleStatus(PlayerResourcePackStatusEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PlayerPackState old = states.get(playerId);
        if (old == null || !old.revision().equals(revision) || !settings.uuid().equals(event.getID())) {
            return;
        }
        PackStatus status = mapStatus(event.getStatus());
        if (old.status() == PackStatus.LOADED && status == PackStatus.PENDING) return;
        if (status != PackStatus.PENDING) cancelTimeout(playerId);
        states.put(playerId, new PlayerPackState(old.connectionId(), old.revision(), status, old.attempts()));
        presentation.setPackStatus(playerId, status);
        presentation.refreshAll();
        if (settings.required()
            && (status == PackStatus.DECLINED || status == PackStatus.FAILED)
            && event.getPlayer().isOnline()) {
            event.getPlayer().kick(Component.text(
                "Conquest SMP could not load its resource pack. Rejoin and accept the server pack to play."
            ));
        }
    }

    private PackStatus mapStatus(PlayerResourcePackStatusEvent.Status status) {
        if (status == null) return PackStatus.NOT_SENT;
        return switch (status) {
            case SUCCESSFULLY_LOADED -> PackStatus.LOADED;
            case DECLINED -> PackStatus.DECLINED;
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> PackStatus.FAILED;
            default -> PackStatus.PENDING;
        };
    }

    public PlayerPackState state(Player player) {
        return states.get(player.getUniqueId());
    }

    private void cancelTimeout(UUID playerId) {
        var task = timeouts.remove(playerId);
        if (task != null) task.cancel();
    }

    public void forgetPlayer(UUID playerId) {
        cancelTimeout(playerId);
        states.remove(playerId);
        presentation.forgetPlayer(playerId);
    }

    public void close() {
        timeouts.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        timeouts.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (states.containsKey(player.getUniqueId())) player.removeResourcePack(settings.uuid());
            presentation.forgetPlayer(player.getUniqueId());
        }
        states.clear();
    }
}

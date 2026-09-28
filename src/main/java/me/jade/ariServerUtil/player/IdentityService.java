package me.jade.ariServerUtil.player;

import me.jade.ariServerUtil.persistence.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class IdentityService implements Listener {
    private final Database database;

    public IdentityService(Database database) {
        this.database = database;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress address = player.getAddress();
        database.rememberPlayer(player.getUniqueId(), player.getName(), address == null || address.getAddress() == null ? "" : address.getAddress().getHostAddress());
    }

    public CompletableFuture<Optional<Database.PlayerIdentity>> resolve(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Database.PlayerIdentity(online.getUniqueId(), online.getName(), "", online.getFirstPlayed(), System.currentTimeMillis())));
        }
        try {
            UUID uuid = UUID.fromString(input);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            if (offline.hasPlayedBefore() && offline.getName() != null) {
                return CompletableFuture.completedFuture(Optional.of(new Database.PlayerIdentity(uuid, offline.getName(), "", offline.getFirstPlayed(), offline.getLastSeen())));
            }
        } catch (IllegalArgumentException ignored) {
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(input)) {
                return CompletableFuture.completedFuture(Optional.of(new Database.PlayerIdentity(offline.getUniqueId(), offline.getName(), "", offline.getFirstPlayed(), offline.getLastSeen())));
            }
        }
        return database.findPlayer(input);
    }
}

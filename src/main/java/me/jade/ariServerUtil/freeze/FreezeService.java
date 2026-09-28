package me.jade.ariServerUtil.freeze;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final AuditService audit;
    private final Map<UUID, Location> frozen = new ConcurrentHashMap<>();

    public FreezeService(JavaPlugin plugin, ServerUtilConfig config, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.audit = audit;
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    public void toggle(Player staff, Player target, String reason) {
        if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), false)) return;
        if (target.hasPermission(Permissions.FREEZE_BYPASS)) {
            Text.send(staff, "<red>That player bypasses freezes.");
            return;
        }
        if (frozen.remove(target.getUniqueId()) != null) {
            databaseRemove(target.getUniqueId());
            Text.send(target, config.message("freeze.unfrozen"));
            audit.record(staff, "UNFREEZE", target.getUniqueId(), target.getName(), reason, "success", "");
            return;
        }
        Location location = target.getLocation().clone();
        frozen.put(target.getUniqueId(), location);
        databaseSave(target, location, reason);
        Text.send(target, config.message("freeze.frozen"));
        target.showTitle(net.kyori.adventure.title.Title.title(Text.mm(config.string("freeze.title", "<red>Frozen")), Text.mm(config.string("freeze.subtitle", "<gray>Do not disconnect."))));
        audit.record(staff, "FREEZE", target.getUniqueId(), target.getName(), reason, "success", "");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location base = frozen.get(event.getPlayer().getUniqueId());
        if (base == null || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            Location allowed = base.clone();
            allowed.setYaw(event.getTo().getYaw());
            allowed.setPitch(event.getTo().getPitch());
            event.setTo(allowed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        String root = event.getMessage().split(" ")[0].toLowerCase();
        if (!config.list("freeze.allowed-commands").contains(root)) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), config.message("freeze.command-blocked"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL && isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && isFrozen(damager.getUniqueId())) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player victim && isFrozen(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission(Permissions.FREEZE)).forEach(player ->
                    Text.send(player, config.message("freeze.disconnect-alert").replace("<player>", event.getPlayer().getName())));
            audit.record(event.getPlayer(), "FROZEN_DISCONNECT", event.getPlayer().getUniqueId(), event.getPlayer().getName(), "", "logged", "");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Location base = frozen.get(event.getPlayer().getUniqueId());
        if (base != null) {
            event.getPlayer().teleport(base);
            Text.send(event.getPlayer(), config.message("freeze.frozen"));
        }
    }

    private void databaseSave(Player target, Location location, String reason) {
        if (plugin instanceof me.jade.ariServerUtil.AriServerUtil main) {
            main.database().execute(conn -> {
                try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO frozen(uuid,name,world,x,y,z,yaw,pitch,reason,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, target.getUniqueId().toString());
                    ps.setString(2, target.getName());
                    ps.setString(3, location.getWorld().getName());
                    ps.setDouble(4, location.getX());
                    ps.setDouble(5, location.getY());
                    ps.setDouble(6, location.getZ());
                    ps.setFloat(7, location.getYaw());
                    ps.setFloat(8, location.getPitch());
                    ps.setString(9, reason == null ? "" : reason);
                    ps.setLong(10, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            });
        }
    }

    private void databaseRemove(UUID uuid) {
        if (plugin instanceof me.jade.ariServerUtil.AriServerUtil main) {
            main.database().execute(conn -> {
                try (var ps = conn.prepareStatement("DELETE FROM frozen WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            });
        }
    }
}

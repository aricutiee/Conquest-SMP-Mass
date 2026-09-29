package dev.turtleroles.service;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.role.Role;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class RoleService {
    private final Plugin plugin;
    private final org.bukkit.configuration.file.YamlConfiguration benefits;
    private final java.nio.file.Path benefitsFile;
    private final Map<UUID, org.bukkit.permissions.PermissionAttachment> staffPermissions = new ConcurrentHashMap<>();
    private final PlayerRepository players;
    private final Map<UUID, PlayerRecord> recordsByUuid = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> loggedOperatorOverrides = ConcurrentHashMap.newKeySet();

    public RoleService(Plugin plugin, PlayerRepository players) {
        this.plugin = plugin;
        this.players = players;
        benefitsFile=plugin.getDataFolder().toPath().resolve("rank-benefits.yml");
        benefits=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(benefitsFile.toFile());
    }

    public PlayerRecord loadOrCreate(Player player) throws SQLException {
        PlayerRecord record = players.upsertKnownPlayer(player.getUniqueId(), player.getName());
        recordsByUuid.put(record.uuid(), record);
        return record;
    }

    public Optional<PlayerRecord> resolveKnown(String input) throws SQLException {
        Optional<PlayerRecord> record = players.resolveKnown(input);
        record.ifPresent(value -> recordsByUuid.put(value.uuid(), value));
        return record;
    }

    public Role roleOf(UUID uuid) {
        PlayerRecord cached = recordsByUuid.get(uuid);
        if (cached != null) {
            return cached.role();
        }
        try {
            Optional<PlayerRecord> record = players.findByUuid(uuid);
            record.ifPresent(value -> recordsByUuid.put(uuid, value));
            return record.map(PlayerRecord::role).orElse(Role.MEMBER);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load role for " + uuid, e);
            return Role.MEMBER;
        }
    }

    public Role effectiveRoleOf(UUID uuid) {
        return roleOf(uuid);
    }

    public Actor actor(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return Actor.systemConsole();
        }
        if (sender instanceof Player player) {
            return Actor.player(player.getUniqueId(), player.getName(), roleOf(player.getUniqueId()), false);
        }
        return new Actor(null, sender.getName(), Role.MEMBER, false, false);
    }

    public void setRole(PlayerRecord target, Role newRole, Actor actor, String reason) throws SQLException {
        players.setRole(target.uuid(), newRole, actor, reason);
        if ((target.role() == Role.OWNER || StaffAccess.managed(target.role())) && newRole != Role.OWNER && !StaffAccess.managed(newRole)) Bukkit.getOfflinePlayer(target.uuid()).setOp(false);
        PlayerRecord updated = players.findByUuid(target.uuid()).orElseThrow();
        recordsByUuid.put(updated.uuid(), updated);
        Player online = Bukkit.getPlayer(updated.uuid());
        if (online != null) {
            reconcileOp(online, updated.role());
        }
    }

    public void reconcileOnlineOps() {
        for(var operator:Bukkit.getOperators())if(roleOf(operator.getUniqueId())!=Role.OWNER)operator.setOp(false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcileOp(player, roleOf(player.getUniqueId()));
        }
    }

    public void reconcileOp(Player player, Role role) {
        var old = staffPermissions.remove(player.getUniqueId());
        if (old != null) {
            player.removeAttachment(old);
            if (!StaffAccess.managed(role) && role != Role.OWNER) player.setOp(false);
            player.closeInventory();
            player.updateCommands();
        }
        if (StaffAccess.managed(role)) {
            if (player.isOp()) player.setOp(false);
            var attachment = player.addAttachment(plugin);
            for(var permission:Bukkit.getPluginManager().getPermissions()) {
                if(permission.getDefault().getValue(true))attachment.setPermission(permission.getName(),true);
            }
            for(String root:java.util.List.of("op","deop","execute","function","reload","datapack","debug","schedule","jfr"))attachment.setPermission("minecraft.command."+root,false);
            for(String node:java.util.List.of("luckperms.*","permissions.*","bukkit.command.op","bukkit.command.deop","bukkit.command.reload"))attachment.setPermission(node,false);
            StaffAccess.permissions(role).forEach(node -> attachment.setPermission(node, true));
            attachment.setPermission("serverutil.punishments.overrideprotected", true);
            staffPermissions.put(player.getUniqueId(), attachment);
            player.updateCommands();
            return;
        }
        if (role == Role.OWNER && !player.isOp()) {
            player.setOp(true);
            loggedOperatorOverrides.remove(player.getUniqueId());
            plugin.getLogger().info("Reconciled OP for stored Owner " + player.getName() + ": true");
            return;
        }
        if (role != Role.OWNER && player.isOp()) player.setOp(false);
        if (!player.isOp()) {
            loggedOperatorOverrides.remove(player.getUniqueId());
        }
    }

    public void removePermissions(Player player) {
        var attachment = staffPermissions.remove(player.getUniqueId());
        if (attachment != null) { player.removeAttachment(attachment); player.setOp(false); }
    }

    public void closePermissions() {
        staffPermissions.forEach((uuid, attachment) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) { player.removeAttachment(attachment); player.setOp(false); }
        });
        staffPermissions.clear();
    }

    public int boosterTier(UUID id) {
        int base=roleOf(id)==Role.BOOSTER_X2?2:roleOf(id)==Role.BOOSTER?1:0;
        return Math.max(base,Math.clamp(benefits.getInt(id.toString()),0,2));
    }
    public void setBooster(UUID id,int tier) throws java.io.IOException {
        String old=benefits.saveToString();benefits.set(id.toString(),tier);
        try {AtomicYaml.save(benefits,benefitsFile);} catch(java.io.IOException ex) {
            try{benefits.loadFromString(old);}catch(Exception ignored){}throw ex;
        }
    }
    public PlayerRepository players() {
        return players;
    }
}

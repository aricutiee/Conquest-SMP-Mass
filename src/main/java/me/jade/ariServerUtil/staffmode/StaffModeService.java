package me.jade.ariServerUtil.staffmode;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.Items;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import me.jade.ariServerUtil.vanish.VanishService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffModeService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private final VanishService vanish;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final NamespacedKey toolKey;
    private BiConsumer<Player, StaffTool> toolHandler = (player, tool) -> { };

    public StaffModeService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit, VanishService vanish) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        this.vanish = vanish;
        this.toolKey = new NamespacedKey("shocksmp", "staff_tool");
    }

    public void setToolHandler(BiConsumer<Player, StaffTool> handler) {
        this.toolHandler = java.util.Objects.requireNonNull(handler);
    }

    private StaffTool identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return StaffTool.valueOf(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private void useTool(Player player, StaffTool tool) {
        if (tool == null || !active(player.getUniqueId())) return;
        // Inventory events must finish before opening a different inventory.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !active(player.getUniqueId())) return;
            if (tool != StaffTool.EXIT && !player.hasPermission(Permissions.STAFFMODE)) {
                Text.send(player, config.message("no-permission"));
                return;
            }
            toolHandler.accept(player, tool);
        });
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL
                || !active(event.getPlayer().getUniqueId())) return;
        StaffTool tool = identify(event.getItem());
        if (tool == null) return;
        // Air interactions can arrive pre-cancelled. Still handle the staff tool.
        event.setCancelled(true);
        useTool(event.getPlayer(), tool);
    }

    @EventHandler
    public void onUseEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !active(event.getPlayer().getUniqueId())) return;
        StaffTool tool = identify(event.getPlayer().getInventory().getItemInMainHand());
        if (tool == null) return;
        event.setCancelled(true);
        useTool(event.getPlayer(), tool);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (active(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (active(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    public boolean active(UUID uuid) {
        return active.contains(uuid);
    }

    public void toggle(Player player) {
        if (active(player.getUniqueId())) {
            disable(player);
        } else {
            enable(player);
        }
    }

    public void enable(Player player) {
        database.query(conn -> {
            try (var check = conn.prepareStatement("SELECT uuid FROM staff_backups WHERE uuid=?")) {
                check.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
            try (var ps = conn.prepareStatement("INSERT INTO staff_backups(uuid,name,inventory,armor,offhand,level,exp,gamemode,allow_flight,flying,health,food,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, Items.serialize(player.getInventory().getStorageContents()));
                ps.setString(4, Items.serialize(player.getInventory().getArmorContents()));
                ps.setString(5, Items.serialize(new ItemStack[]{player.getInventory().getItemInOffHand()}));
                ps.setInt(6, player.getLevel());
                ps.setFloat(7, player.getExp());
                ps.setString(8, player.getGameMode().name());
                ps.setInt(9, player.getAllowFlight() ? 1 : 0);
                ps.setInt(10, player.isFlying() ? 1 : 0);
                ps.setDouble(11, player.getHealth());
                ps.setInt(12, player.getFoodLevel());
                ps.setLong(13, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
            return true;
        }).thenAccept(saved -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!saved) {
                Text.send(player, "<red>An unresolved Staff Mode backup already exists. Disable Staff Mode to restore it first.");
                return;
            }
            active.add(player.getUniqueId());
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            giveTools(player);
            if (config.bool("staff-mode.enable-vanish", true)) {
                vanish.vanish(player);
            }
            player.setAllowFlight(config.bool("staff-mode.enable-flight", true));
            player.setInvulnerable(config.bool("staff-mode.invulnerable", true));
            audit.record(player, "STAFFMODE_ENABLE", player.getUniqueId(), player.getName(), "", "success", "");
        }));
    }

    public void disable(Player player) {
        database.query(conn -> {
            try (var ps = conn.prepareStatement("SELECT * FROM staff_backups WHERE uuid=?")) {
                ps.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Backup backup = new Backup(
                            Items.deserialize(rs.getString("inventory")),
                            Items.deserialize(rs.getString("armor")),
                            Items.deserialize(rs.getString("offhand")),
                            rs.getInt("level"),
                            rs.getFloat("exp"),
                            GameMode.valueOf(rs.getString("gamemode")),
                            rs.getInt("allow_flight") == 1,
                            rs.getInt("flying") == 1,
                            rs.getDouble("health"),
                            rs.getInt("food")
                    );
                    try (var delete = conn.prepareStatement("DELETE FROM staff_backups WHERE uuid=?")) {
                        delete.setString(1, player.getUniqueId().toString());
                        delete.executeUpdate();
                    }
                    return backup;
                }
            }
        }).thenAccept(backup -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (backup == null) {
                Text.send(player, "<red>No Staff Mode backup was found.");
                active.remove(player.getUniqueId());
                return;
            }
            player.getInventory().clear();
            player.getInventory().setStorageContents(backup.inventory);
            player.getInventory().setArmorContents(backup.armor);
            if (backup.offhand.length > 0 && backup.offhand[0] != null) {
                player.getInventory().setItemInOffHand(backup.offhand[0]);
            }
            player.setLevel(backup.level);
            player.setExp(backup.exp);
            player.setGameMode(backup.gameMode);
            player.setAllowFlight(backup.allowFlight);
            player.setFlying(backup.flying && backup.allowFlight);
            player.setHealth(Math.min(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), backup.health));
            player.setFoodLevel(backup.food);
            player.setInvulnerable(false);
            active.remove(player.getUniqueId());
            audit.record(player, "STAFFMODE_DISABLE", player.getUniqueId(), player.getName(), "", "success", "");
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        database.query(conn -> {
            try (var ps = conn.prepareStatement("SELECT uuid FROM staff_backups WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }).thenAccept(found -> {
            if (found) {
                active.add(uuid);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    giveTools(event.getPlayer());
                    Text.send(event.getPlayer(), "<yellow>Staff Mode backup found. Use /staffmode to restore your original inventory.");
                });
            }
        });
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (active(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && active(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && active(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && active(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getClickedInventory() == player.getInventory()) {
                useTool(player, identify(event.getCurrentItem()));
            }
        }
    }

    private void giveTools(Player player) {
        for (StaffTool staffTool : StaffTool.values()) {
            player.getInventory().setItem(staffTool.slot, tool(staffTool));
        }
    }

    private ItemStack tool(StaffTool tool) {
        ItemStack item = new ItemStack(tool.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm((tool == StaffTool.EXIT ? "<red>" : "<aqua>") + tool.title));
        meta.lore(java.util.List.of(Text.mm("<gray>Right-click to open.")));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
        item.setItemMeta(meta);
        return item;
    }

    private record Backup(ItemStack[] inventory, ItemStack[] armor, ItemStack[] offhand, int level, float exp, GameMode gameMode, boolean allowFlight, boolean flying, double health, int food) {
    }
}

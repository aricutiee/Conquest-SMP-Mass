package dev.turtleroles.migration;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.block.Container;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Retires only authenticated legacy crystal items, keeping a serialized recovery copy first. */
public final class RetiredShockCleanup implements Listener {
    private static final NamespacedKey TYPE = new NamespacedKey("shocksmppowersystem", "shock_type");
    private final JavaPlugin plugin;
    public RetiredShockCleanup(JavaPlugin plugin) { this.plugin = plugin; }
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::player);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::player), 20, 20);
    }
    public static boolean containsCrystal(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.getPersistentDataContainer().has(TYPE)) return true;
        if (meta instanceof BundleMeta bundle) return bundle.getItems().stream().anyMatch(RetiredShockCleanup::containsCrystal);
        if (meta instanceof BlockStateMeta block && block.getBlockState() instanceof Container container)
            return Arrays.stream(container.getInventory().getContents()).anyMatch(RetiredShockCleanup::containsCrystal);
        return false;
    }
    private ItemStack scrub(ItemStack original) {
        if (original == null || !original.hasItemMeta()) return original;
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(TYPE)) return null;
        if (meta instanceof BundleMeta bundle) {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack child : bundle.getItems()) { ItemStack clean = scrub(child); if (clean != null) items.add(clean); }
            bundle.setItems(items);
        }
        if (meta instanceof BlockStateMeta block && block.getBlockState() instanceof Container container) {
            Inventory inventory = container.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, scrub(inventory.getItem(slot)));
            block.setBlockState(container);
        }
        item.setItemMeta(meta);
        return item;
    }
    /** Failure to create the recovery copy leaves the original untouched. */
    private ItemStack retire(ItemStack original) {
        if (!containsCrystal(original)) return original;
        try {
            Path folder = plugin.getDataFolder().toPath().resolve("retired-crystals");
            Files.createDirectories(folder);
            Files.write(folder.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID() + ".item"),
                    original.serializeAsBytes(), StandardOpenOption.CREATE_NEW);
            return scrub(original);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not archive a retired crystal; item was kept: " + ex.getMessage());
            return original;
        }
    }
    private void inventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (containsCrystal(item)) inventory.setItem(slot, retire(item));
        }
    }
    private void player(Player player) {
        inventory(player.getInventory()); inventory(player.getEnderChest());
        if (containsCrystal(player.getItemOnCursor())) player.setItemOnCursor(retire(player.getItemOnCursor()));
        // These exact keys belong to the removed power system, never to event gear.
        for (Attribute type : List.of(Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.FLYING_SPEED,
                Attribute.JUMP_STRENGTH, Attribute.GRAVITY, Attribute.BLOCK_BREAK_SPEED, Attribute.ATTACK_SPEED)) {
            var attribute = player.getAttribute(type);
            if (attribute == null) continue;
            for (var modifier : new ArrayList<>(attribute.getModifiers())) {
                String key = modifier.getKey().toString();
                if (key.equals("shocksmp:healing_health") || key.equals("shocksmppowersystem:healing_health")
                        || key.startsWith("shocksmppowersystem:time_")) attribute.removeModifier(modifier);
            }
        }
        var health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && player.getHealth() > health.getValue()) player.setHealth(health.getValue());
    }
    @EventHandler public void join(PlayerJoinEvent event) { player(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) { inventory(event.getInventory()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(InventoryMoveItemEvent event) {
        if (containsCrystal(event.getItem())) { event.setCancelled(true); inventory(event.getSource()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        if (!containsCrystal(entity.getItemStack())) return;
        event.setCancelled(true);
        ItemStack clean = retire(entity.getItemStack());
        if (clean == null) entity.remove(); else entity.setItemStack(clean);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (containsCrystal(event.getItem())) { event.setCancelled(true); player(event.getPlayer()); }
    }
}

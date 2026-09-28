package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates authenticated event prizes without restricting vanilla maces or enchantments. */
public final class ShockMace {
    private static final Map<String, Integer> REQUIRED_IDS = Map.of(
            "density", 3, "wind_burst", 2, "unbreaking", 3, "mending", 1);
    private final NamespacedKey typeKey;
    private final NamespacedKey instanceKey;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<Enchantment, Integer> required = new LinkedHashMap<>();

    public ShockMace(JavaPlugin plugin) {
        this(requiredEnchantments());
    }

    ShockMace(Map<Enchantment, Integer> requiredEnchantments) {
        typeKey = new NamespacedKey("shocksmp", "shock_mace");
        instanceKey = new NamespacedKey("shocksmp", "shock_mace_instance");
        required.putAll(requiredEnchantments);
    }

    static Map<Enchantment, Integer> requiredEnchantments() {
        Map<Enchantment, Integer> levels = new LinkedHashMap<>();
        REQUIRED_IDS.forEach((key, level) -> levels.put(enchantment(key), level));
        return levels;
    }

    static Map<String, Integer> requiredSpecs() { return REQUIRED_IDS; }

    private static Enchantment enchantment(String id) {
        Enchantment value = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));
        if (value == null) throw new IllegalStateException("Missing Minecraft enchantment: " + id);
        return value;
    }

    Map<Enchantment, Integer> requiredLevels() { return Map.copyOf(required); }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mini.deserialize("<gradient:#55FFFF:#AA55FF><bold>⚡ JUGGERNAUT'S MACE ⚡</bold></gradient>"));
        meta.lore(List.of(mini.deserialize("<gray>Awarded to the top Juggernaut damage dealer.</gray>"),
                mini.deserialize("<aqua>Density III <gray>•</gray> Wind Burst II</aqua>"),
                mini.deserialize("<light_purple>Unbreaking III <gray>•</gray> Mending I</light_purple>")));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.setEnchantmentGlintOverride(true);
        required.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGenuine(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String instance = meta.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        Byte type = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.BYTE);
        if (type == null || type != 1 || instance == null) return false;
        try { UUID.fromString(instance); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    public void give(Player player) {
        player.sendMessage(mini.deserialize("<red>Maces can only be earned by defeating the Juggernaut.</red>"));
    }
}

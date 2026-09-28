package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/** Rewrites only Strength II potion items, preserving all unrelated meta. */
public final class StrengthPotions implements Listener {
    public static final int DURATION_TICKS = 8 * 60 * 20;
    private final JavaPlugin plugin;
    private int cursor;

    public StrengthPotions(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileOnline, 20, 20);
    }

    static boolean needsEightMinutes(boolean strongBase, int customAmplifier, int customTicks) {
        return strongBase || customAmplifier == 1 && customTicks != DURATION_TICKS;
    }

    public boolean normalize(ItemStack item) {
        if (item == null || item.getType() != Material.POTION && item.getType() != Material.SPLASH_POTION
                && item.getType() != Material.LINGERING_POTION) return false;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        boolean strongBase = meta.getBasePotionType() == PotionType.STRONG_STRENGTH;
        PotionEffect custom = meta.getCustomEffects().stream()
                .filter(effect -> effect.getType().equals(PotionEffectType.STRENGTH)).findFirst().orElse(null);
        if (!needsEightMinutes(strongBase, custom == null ? -1 : custom.getAmplifier(),
                custom == null ? -1 : custom.getDuration())) return false;
        if (strongBase) meta.setBasePotionType(PotionType.AWKWARD);
        meta.removeCustomEffect(PotionEffectType.STRENGTH);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, DURATION_TICKS, 1), true);
        if (!meta.hasDisplayName() && !meta.hasItemName())
            meta.itemName(Component.text((item.getType() == Material.SPLASH_POTION ? "Splash Potion" :
                    item.getType() == Material.LINGERING_POTION ? "Lingering Potion" : "Potion") + " of Strength II (8:00)"));
        item.setItemMeta(meta);
        return true;
    }

    public ItemStack drinkable() {
        return create(Material.POTION);
    }

    public ItemStack splash() { return create(Material.SPLASH_POTION); }

    private ItemStack create(Material material) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.STRONG_STRENGTH);
        item.setItemMeta(meta);
        normalize(item);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        for (ItemStack result : event.getResults()) normalize(result);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        List<ItemStack> items = new ArrayList<>(event.getLoot());
        boolean changed = false;
        for (ItemStack item : items) changed |= normalize(item);
        if (changed) event.setLoot(items);
    }

    @EventHandler public void onOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        for (int slot = 0; slot < Math.min(54, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (normalize(item)) inventory.setItem(slot, item);
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (normalize(current)) event.setCurrentItem(current);
        ItemStack cursorItem = event.getCursor();
        if (normalize(cursorItem)) event.setCursor(cursorItem);
        if (event.getWhoClicked() instanceof Player player)
            Bukkit.getScheduler().runTask(plugin, () -> reconcile(player));
    }

    @EventHandler public void onMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (normalize(item)) event.setItem(item);
    }

    @EventHandler public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack item = entity.getItemStack();
        if (normalize(item)) entity.setItemStack(item);
    }

    @EventHandler public void onSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack item = entity.getItemStack();
        if (normalize(item)) entity.setItemStack(item);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem().clone();
        if (normalize(item)) event.setItem(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThrown(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        ItemStack item = potion.getItem();
        if (normalize(item)) potion.setItem(item);
    }

    private void reconcileOnline() {
        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < Math.min(8, players.size()); i++)
            reconcile(players.get(Math.floorMod(cursor++, players.size())));
    }

    private void reconcile(Player player) {
        if (!player.isOnline()) return;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (normalize(item)) inventory.setItem(slot, item);
        }
    }
}

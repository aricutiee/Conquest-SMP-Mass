package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Automatic, persistent Juggernaut designation and scoped mythic loot. */
public final class JuggernautService implements Listener {
    static final String CLAIM_DENIAL = "You can’t claim multiple mythics in one event. Wait until an admin runs /event end.";
    private final JavaPlugin plugin;
    private final WarlordEquipment equipment;
    private final JuggernautLoadout factory;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey eventKey = new NamespacedKey("shocksmp", "event_id");
    private final NamespacedKey kindKey = new NamespacedKey("shocksmp", "event_item_kind");
    private final NamespacedKey instanceKey = new NamespacedKey("shocksmp", "event_item_instance");
    private final NamespacedKey issuedKey = new NamespacedKey("shocksmp", "event_reward_issued");
    private final NamespacedKey restoreKey = new NamespacedKey("shocksmp", "inventory_restore_id");
    private final NamespacedKey activeKey = new NamespacedKey("shocksmp", "juggernaut_active");
    private final NamespacedKey warlordActiveKey = new NamespacedKey("shocksmp", "warlord_active");
    private final NamespacedKey crownKey = new NamespacedKey("kingscrown", "crownsmp_crown");
    private final Map<UUID, Long> deniedAt = new HashMap<>();
    private UUID activePlayer;
    private UUID activeEvent;
    private boolean designated;
    private boolean warlord;
    private boolean lootRestricted;
    private Consumer<UUID> wonCallback = ignored -> {};
    private java.util.function.BiConsumer<org.bukkit.Location,Map<String,ItemStack>> purgeReward = (l,i) -> {};
    public void onPurgeReward(java.util.function.BiConsumer<org.bukkit.Location,Map<String,ItemStack>> callback){purgeReward=callback;}
    private Consumer<org.bukkit.Location> defeatReward = ignored -> {};
    public void onDefeatReward(Consumer<org.bukkit.Location> callback){defeatReward=callback;}
    public boolean activeMace(ItemStack item){return designated&&item!=null&&item.getType()==Material.MACE&&isLoadoutItem(item);}

    public JuggernautService(JavaPlugin plugin, YamlConfiguration settings, WarlordEquipment warlordEquipment,
                             ShockMace mace, StrengthPotions strength) {
        this.plugin = plugin;
        this.equipment = warlordEquipment;
        this.factory = new JuggernautLoadout(settings, warlordEquipment, mace, strength);
        this.file = new File(plugin.getDataFolder(), "juggernaut-state.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        activePlayer = parseUuid(data.getString("active.player"));
        activeEvent = parseUuid(data.getString("active.event"));
        if (activeEvent == null) activeEvent = parseUuid(data.getString("loot.event"));
        designated = data.getBoolean("active.designated") && activePlayer != null && activeEvent != null;
        warlord = data.getBoolean("active.warlord");
        lootRestricted = data.getBoolean("loot.restricted") && activeEvent != null;
    }

    public void onWon(Consumer<UUID> callback) { wonCallback = callback; }
    public boolean isActive(Player player) { return designated && player.getUniqueId().equals(activePlayer); }
    public boolean hasDesignation() { return designated; }
    public boolean isWarlord(Player player) { return isActive(player) && warlord; }
    public boolean wasWarlord() { return warlord; }
    public UUID eventId() { return activeEvent; }
    public boolean lootRestricted() { return lootRestricted; }

    public String checkLoadouts() {
        List<String> results = new ArrayList<>();
        for (boolean variant : new boolean[]{false, true}) {
            var kit = factory.create(variant, variant ? null : factory.newCrown());
            long splash = kit.storage().stream().filter(e -> e.item().getType() == Material.SPLASH_POTION).count();
            int xp = kit.storage().stream().filter(e -> e.item().getType() == Material.EXPERIENCE_BOTTLE)
                    .mapToInt(e -> e.item().getAmount()).sum();
            if (kit.storage().stream().anyMatch(e -> e.item().getType() == Material.POTION
                    || e.item().getType() == Material.NETHERITE_HELMET
                    || e.item().getAmount() > e.item().getMaxStackSize()))
                throw new IllegalStateException("Invalid potion, helmet, or stack in loadout");
            if (kit.armor().size() != 4 || !(variant ? "armor_helmet" : "crown").equals(kit.armor().get(3).kind()))
                throw new IllegalStateException("Crown must occupy the helmet slot");
            for (var entry : kit.storage()) {
                if (!entry.item().isSimilar(ItemStack.deserializeBytes(entry.item().serializeAsBytes())))
                    throw new IllegalStateException("Loadout metadata serialization failed");
            }
            results.add((variant ? "Warlord" : "Juggernaut") + ": " + kit.storage().size()
                    + "/36 slots, splash potions=" + splash + ", XP bottles=" + xp
                    + (variant ? ", Warlord helmet equipped, seven relics" : ", crown equipped, no spare helmet") + ", metadata roundtrip OK");
        }
        return String.join(" | ", results);
    }

    public void validateDesignation(Player target, boolean variant) {
        if (designated || data.contains("active.original.storage"))
            throw new IllegalStateException("A Juggernaut is already designated or possessions await restoration");
        ItemStack crown = variant ? null : findCrown(target.getInventory());
        if (crown != null && crown.getAmount() != 1)
            throw new IllegalStateException("Separate stacked crowns before designation");
        factory.create(variant, variant ? null : crown == null ? factory.newCrown() : crown.clone());
    }

    public boolean designate(Player target, UUID eventId, boolean variant) {
        if (designated || data.contains("active.original.storage")) return false;
        PlayerInventory inventory = target.getInventory();
        ItemStack crown = variant ? null : findCrown(inventory);
        boolean borrowed = crown != null;
        if (borrowed && crown.getAmount() != 1)
            throw new IllegalStateException("Separate stacked crowns before designation");
        if (!borrowed && !variant) crown = factory.newCrown();
        JuggernautLoadout.Set loadout = factory.create(variant, crown == null ? null : crown.clone());
        if (loadout.storage().size() > 36) throw new IllegalStateException("Loadout exceeds the player inventory");
        UUID id = target.getUniqueId();
        data.set("active.player", id.toString());
        data.set("active.event", eventId.toString());
        data.set("active.warlord", variant);
        data.set("active.borrowed-crown", borrowed);
        data.set("active.previous-glow", target.isGlowing());
        data.set("active.designated", false);
        data.set("active.restore-pending", true);
        data.set("active.original.storage", Arrays.asList(inventory.getStorageContents()));
        data.set("active.original.armor", Arrays.asList(inventory.getArmorContents()));
        data.set("active.original.offhand", inventory.getItemInOffHand().clone());
        if (!save()) {
            data.set("active", null);
            throw new IllegalStateException("Could not save the original inventory; no loadout was applied");
        }
        ItemStack[] storage = new ItemStack[36];
        for (int slot = 0; slot < loadout.storage().size(); slot++) {
            var entry = loadout.storage().get(slot);
            storage[slot] = tag(entry.item(), eventId, entry.kind(), entry.mythic());
        }
        ItemStack[] armor = new ItemStack[4];
        for (int slot = 0; slot < loadout.armor().size(); slot++) {
            var entry = loadout.armor().get(slot);
            armor[slot] = tag(entry.item(), eventId, entry.kind(), entry.mythic());
        }
        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        activePlayer = id;
        activeEvent = eventId;
        designated = true;
        warlord = variant;
        data.set("active.designated", true);
        data.set("active.restore-pending", false);
        target.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
        if (variant) target.getPersistentDataContainer().set(warlordActiveKey, PersistentDataType.BYTE, (byte) 1);
        target.setGlowing(true);
        save();
        target.sendMessage(Component.text("You are the " + (variant ? "Warlord" : "Juggernaut")
                + ". Your original inventory is saved and will return when the encounter ends.", NamedTextColor.GOLD));
        return true;
    }

    private ItemStack tag(ItemStack source, UUID eventId, String kind, boolean mythic) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(eventKey, PersistentDataType.STRING, eventId.toString());
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING,
                (mythic ? "mythic:" : "supply:") + kind);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        if (mythic) meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    private String kind(ItemStack item) {
        return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer()
                .get(kindKey, PersistentDataType.STRING) : null;
    }

    private String event(ItemStack item) {
        return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer()
                .get(eventKey, PersistentDataType.STRING) : null;
    }

    private boolean isLoadoutItem(ItemStack item) {
        return activeEvent != null && activeEvent.toString().equals(event(item)) && kind(item) != null && !isIssued(item);
    }

    private boolean isIssued(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(issuedKey, PersistentDataType.BYTE);
    }

    private boolean isRestrictedReward(ItemStack item) {
        if(item!=null&&item.getType()==Material.MACE)return false;
        return lootRestricted && activeEvent != null && activeEvent.toString().equals(event(item)) && isIssued(item);
    }

    private ItemStack issue(ItemStack source) {
        ItemStack reward = source.clone();
        reward.setAmount(1);
        ItemMeta meta = reward.getItemMeta();
        meta.getPersistentDataContainer().set(issuedKey, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(1);
        reward.setItemMeta(meta);
        return reward;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent death) {
        Player player = death.getEntity();
        if (!isActive(player)) return;
        Map<String, ItemStack> rewards = new LinkedHashMap<>();
        List<ItemStack> possessions = new ArrayList<>();
        possessions.addAll(Arrays.asList(player.getInventory().getStorageContents()));
        possessions.addAll(Arrays.asList(player.getInventory().getArmorContents()));
        possessions.add(player.getInventory().getItemInOffHand());
        for (ItemStack item : possessions) {
            String itemKind = kind(item);
            if (!isLoadoutItem(item) || itemKind == null || !itemKind.startsWith("mythic:")) continue;
            String instance = item.getItemMeta().getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
            if (instance != null) rewards.putIfAbsent(instance, item);
        }
        death.getDrops().removeIf(this::isLoadoutItem);
        Enchantment vanishing = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("vanishing_curse"));
        Map<String,ItemStack> scattered = new LinkedHashMap<>();
        for (ItemStack item : rewards.values()) {
            if(warlord) {
                String piece=kind(item).replace("mythic:", "").replace("armor_", "");
                if(WarlordRelics.PIECES.contains(piece))scattered.put(piece,issue(item));
                continue;
            }
            if(item.getType()==Material.MACE)continue; // Awarded once to the damage leader instead.
            if (vanishing != null && item.containsEnchantment(vanishing)) continue;
            death.getDrops().add(issue(item));
        }
        if (data.getBoolean("active.borrowed-crown")) removeOneOriginalCrown();
        if(warlord)purgeReward.accept(player.getLocation(),scattered);
        else defeatReward.accept(player.getLocation());
        designated = false;
        lootRestricted = !warlord;
        data.set("active.designated", false);
        data.set("active.restore-pending", true);
        data.set("loot.restricted", !warlord);
        data.set("loot.event", activeEvent.toString());
        player.getPersistentDataContainer().remove(activeKey);
        player.getPersistentDataContainer().remove(warlordActiveKey);
        equipment.clearEventBonuses(player);
        player.setGlowing(data.getBoolean("active.previous-glow"));
        save();
        if (death.getKeepInventory()) Bukkit.getScheduler().runTask(plugin, () -> stripLoadout(player));
        wonCallback.accept(player.getUniqueId());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getUniqueId().equals(activePlayer) && data.getBoolean("active.restore-pending"))
            Bukkit.getScheduler().runTask(plugin, () -> restore(event.getPlayer()));
    }

    public void end(UUID eventId) {
        if (activeEvent == null || !activeEvent.equals(eventId)) return;
        designated = false;
        lootRestricted = false;
        data.set("active.designated", false);
        data.set("active.restore-pending", data.contains("active.original.storage"));
        data.set("loot.restricted", false);
        data.set("loot.event", null);
        data.set("claims." + eventId, null);
        save();
        Player target = activePlayer == null ? null : Bukkit.getPlayer(activePlayer);
        if (target != null && !target.isDead()) restore(target);
        else if (!data.contains("active.original.storage")) { activeEvent = null; save(); }
    }

    private void restore(Player player) {
        if (activePlayer == null || !activePlayer.equals(player.getUniqueId())) return;
        if (designated) return;
        player.getPersistentDataContainer().remove(activeKey);
        player.getPersistentDataContainer().remove(warlordActiveKey);
        equipment.clearEventBonuses(player);
        player.setGlowing(data.getBoolean("active.previous-glow"));
        stripLoadout(player);
        PlayerInventory inv = player.getInventory();
        List<ItemStack> storage = readItems("active.original.storage", 36);
        List<ItemStack> armor = readItems("active.original.armor", 4);
        ItemStack offhand = data.getItemStack("active.original.offhand");
        int pending = 0;
        for (int i = 0; i < storage.size(); i++) {
            ItemStack original = storage.get(i);
            if (!present(original)) continue;
            String token = restoreToken("storage", i);
            ItemStack tagged = restoreTagged(original, token);
            if (hasRestoreToken(inv, token)) storage.set(i, null);
            else if (!present(inv.getItem(i))) { inv.setItem(i, tagged); storage.set(i, null); }
            else if (canFit(inv, tagged) && inv.addItem(tagged).isEmpty()) storage.set(i, null);
            else pending++;
        }
        ItemStack[] equipped = inv.getArmorContents();
        for (int i = 0; i < armor.size(); i++) {
            ItemStack original = armor.get(i);
            if (!present(original)) continue;
            String token = restoreToken("armor", i);
            ItemStack tagged = restoreTagged(original, token);
            if (hasRestoreToken(inv, token)) armor.set(i, null);
            else if (!present(equipped[i])) { equipped[i] = tagged; armor.set(i, null); }
            else if (canFit(inv, tagged) && inv.addItem(tagged).isEmpty()) armor.set(i, null);
            else pending++;
        }
        inv.setArmorContents(equipped);
        if (present(offhand)) {
            String token = restoreToken("offhand", 0);
            ItemStack tagged = restoreTagged(offhand, token);
            if (hasRestoreToken(inv, token)) offhand = null;
            else if (!present(inv.getItemInOffHand())) { inv.setItemInOffHand(tagged); offhand = null; }
            else if (canFit(inv, tagged) && inv.addItem(tagged).isEmpty()) offhand = null;
            else pending++;
        }
        data.set("active.original.storage", storage);
        data.set("active.original.armor", armor);
        data.set("active.original.offhand", offhand);
        if (pending == 0) {
            data.set("active", null);
            activePlayer = null;
            if (!lootRestricted) activeEvent = null;
        } else {
            player.sendMessage(Component.text(pending + " original items remain safely saved. Clear inventory space and ask an admin to run /juggernaut restore.", NamedTextColor.YELLOW));
        }
        if (save()) clearRestoreTokens(player);
    }

    public void restorePending(Player player) { restore(player); }

    private void stripLoadout(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++)
            if (isLoadoutItem(inv.getItem(slot))) inv.setItem(slot, null);
    }

    private void removeOneOriginalCrown() {
        List<ItemStack> storage = readItems("active.original.storage", 36);
        List<ItemStack> armor = readItems("active.original.armor", 4);
        for (List<ItemStack> items : List.of(storage, armor)) for (int i = 0; i < items.size(); i++)
            if (isCrown(items.get(i))) { items.set(i, null); data.set("active.original.storage", storage);
                data.set("active.original.armor", armor); return; }
        if (isCrown(data.getItemStack("active.original.offhand"))) data.set("active.original.offhand", null);
    }

    private ItemStack findCrown(PlayerInventory inventory) {
        for (ItemStack item : inventory.getContents()) if (isCrown(item)) return item;
        return null;
    }

    private boolean isCrown(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(crownKey, PersistentDataType.BYTE);
    }

    private List<ItemStack> readItems(String path, int size) {
        List<?> raw = data.getList(path, List.of());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++) items.add(i < raw.size() && raw.get(i) instanceof ItemStack stack ? stack.clone() : null);
        return items;
    }

    private boolean present(ItemStack item) { return item != null && !item.getType().isAir(); }

    private boolean canFit(PlayerInventory inventory, ItemStack item) {
        int room = 0;
        for (ItemStack existing : inventory.getStorageContents()) {
            if (!present(existing)) room += item.getMaxStackSize();
            else if (existing.isSimilar(item)) room += Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            if (room >= item.getAmount()) return true;
        }
        return false;
    }

    private String restoreToken(String source, int slot) {
        return activeEvent + ":" + activePlayer + ":" + source + ":" + slot;
    }

    private ItemStack restoreTagged(ItemStack source, String token) {
        ItemStack copy = source.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(restoreKey, PersistentDataType.STRING, token);
        copy.setItemMeta(meta);
        return copy;
    }

    private boolean hasRestoreToken(PlayerInventory inventory, String token) {
        for (ItemStack item : inventory.getContents()) if (present(item) && item.hasItemMeta()
                && token.equals(item.getItemMeta().getPersistentDataContainer().get(restoreKey, PersistentDataType.STRING)))
            return true;
        return false;
    }

    private void clearRestoreTokens(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!present(item) || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(restoreKey, PersistentDataType.STRING)) continue;
            meta.getPersistentDataContainer().remove(restoreKey);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
    }

    @EventHandler public void onItemSpawn(ItemSpawnEvent event) {
        if (isIssued(event.getEntity().getItemStack())) event.getEntity().setGlowing(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack item = entity.getItemStack();
        if (!isRestrictedReward(item)) return;
        if (!(event.getEntity() instanceof Player player)) { event.setCancelled(true); return; }
        if (alreadyClaimed(player) || player.getInventory().firstEmpty() == -1) {
            event.setCancelled(true);
            if (alreadyClaimed(player)) deny(player, CLAIM_DENIAL);
            return;
        }
        claim(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isRestrictedReward(event.getItem().getItemStack())) event.setCancelled(true);
    }

    private boolean alreadyClaimed(Player player) {
        boolean claimed = activeEvent != null && data.getBoolean("claims." + activeEvent + "." + player.getUniqueId());
        return !JuggernautRules.canClaim(lootRestricted, activeEvent, activeEvent, claimed);
    }

    private void claim(Player player) {
        if (activeEvent == null) return;
        data.set("claims." + activeEvent + "." + player.getUniqueId(), true);
        save();
        player.sendMessage(Component.text("Mythic claimed. Each armor piece counts as one claim until /event end.", NamedTextColor.GOLD));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean topSlot = event.getRawSlot() < event.getView().getTopInventory().getSize();
        boolean external = event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && event.getView().getTopInventory().getType() != InventoryType.PLAYER;
        int hotbarSlot = event.getHotbarButton();
        ItemStack hotbar = hotbarSlot >= 0 ? player.getInventory().getItem(hotbarSlot) : null;
        if (external && (isLoadoutItem(cursor) || isLoadoutItem(current) || isLoadoutItem(hotbar)
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && isLoadoutItem(player.getInventory().getItemInOffHand()))) {
            event.setCancelled(true); return;
        }
        if (topSlot && external && isRestrictedReward(current)) {
            if (alreadyClaimed(player)) { event.setCancelled(true); deny(player, CLAIM_DENIAL); }
            else if (player.getInventory().firstEmpty() == -1) event.setCancelled(true);
            else if (event.getAction().name().startsWith("PICKUP")
                    || event.getAction().name().startsWith("MOVE")
                    || event.getAction().name().startsWith("HOTBAR")) claim(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack cursor = event.getOldCursor();
        if (isLoadoutItem(cursor) && event.getRawSlots().stream().anyMatch(slot ->
                slot < event.getView().getTopInventory().getSize()
                        && event.getView().getTopInventory().getType() != InventoryType.CRAFTING))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (isLoadoutItem(event.getItem()) || isRestrictedReward(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLoadoutItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix())
                .anyMatch(this::isLoadoutItem)) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix())
                .anyMatch(this::isLoadoutItem)) event.setCancelled(true);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isActive(player)) {
            player.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
            if (warlord) player.getPersistentDataContainer().set(warlordActiveKey, PersistentDataType.BYTE, (byte) 1);
            player.setGlowing(true);
        } else {
            player.getPersistentDataContainer().remove(activeKey);
            player.getPersistentDataContainer().remove(warlordActiveKey);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getUniqueId().equals(activePlayer) && data.getBoolean("active.restore-pending")) restore(player);
        });
    }

    private void deny(Player player, String message) {
        long now = System.currentTimeMillis();
        if (now - deniedAt.getOrDefault(player.getUniqueId(), 0L) >= 2000) {
            deniedAt.put(player.getUniqueId(), now);
            player.sendMessage(Component.text(message, NamedTextColor.RED));
        }
    }

    private UUID parseUuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private boolean save() {
        try { data.save(file); return true; }
        catch (IOException ex) { plugin.getLogger().severe("Could not save Juggernaut event state: " + ex.getMessage()); return false; }
    }
}

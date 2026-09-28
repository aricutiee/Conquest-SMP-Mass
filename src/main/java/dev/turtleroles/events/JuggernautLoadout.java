package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates a complete loadout. No editor or pre-existing kit is required. */
final class JuggernautLoadout {
    record Entry(ItemStack item, String kind, boolean mythic) {}
    record Set(List<Entry> storage, List<Entry> armor) {}

    private final YamlConfiguration config;
    private final WarlordEquipment warlord;
    private final ShockMace mace;
    private final StrengthPotions strength;

    JuggernautLoadout(YamlConfiguration config, WarlordEquipment warlord,
                      ShockMace mace, StrengthPotions strength) {
        this.config = config;
        this.warlord = warlord;
        this.mace = mace;
        this.strength = strength;
    }

    static Map<String, Integer> armorSpecs(String piece) {
        Map<String, Integer> specs = new LinkedHashMap<>();
        specs.put("protection", piece.equals("helmet")||piece.equals("boots")?6:5);
        specs.put("unbreaking", 4);
        specs.put("mending", 1);
        if (piece.equals("helmet")) { specs.put("respiration", 3); specs.put("aqua_affinity", 1); }
        if (piece.equals("leggings")) specs.put("swift_sneak", 3);
        if (piece.equals("boots")) {
            specs.put("feather_falling", 4); specs.put("depth_strider", 3); specs.put("soul_speed", 3);
        }
        return specs;
    }

    static Map<String, Integer> swordSpecs() {
        return Map.of("sharpness", 5, "unbreaking", 3, "mending", 1,
                "fire_aspect", 2, "looting", 3, "sweeping_edge", 3);
    }

    static Map<String, Integer> axeSpecs() {
        return Map.of("sharpness", 5, "efficiency", 5, "unbreaking", 3,
                "mending", 1, "fortune", 3);
    }

    static Map<String, Integer> pickSpecs() {
        return Map.of("efficiency", 5, "unbreaking", 3, "mending", 1, "fortune", 3);
    }

    static Map<String, Integer> spearCoreSpecs() {
        return Map.of("lunge", 3, "unbreaking", 3, "mending", 1);
    }

    Set create(boolean isWarlord, ItemStack crown) {
        List<Entry> storage = new ArrayList<>();
        List<Entry> armor = new ArrayList<>();
        if (isWarlord) {
            WarlordRelics relics = new WarlordRelics(null, config);
            for(String piece:List.of("boots","leggings","chestplate","helmet"))
                armor.add(new Entry(relics.create(piece),"armor_"+piece,true));
            for(String piece:List.of("sword","axe","pickaxe"))
                storage.add(new Entry(relics.create(piece),piece,true));
        } else {
        for (String piece : List.of("boots", "leggings", "chestplate")) {
            ItemStack item = isWarlord ? warlord.armor(piece) : namedArmor(piece);
            enchant(item, armorSpecs(piece), "juggernaut.enchantments.armor-"+piece+".");
            Entry entry = new Entry(item, "armor_" + piece, true);
            armor.add(entry);
        }
        enchant(crown,armorSpecs("helmet"),"juggernaut.enchantments.armor-helmet.");
        armor.add(new Entry(crown, "crown", true));

        ItemStack sword = isWarlord ? warlord.blade() : named(Material.NETHERITE_SWORD,
                "Juggernaut's Sword", "A mythic prize from the Juggernaut encounter.");
        enchant(sword, swordSpecs(), "juggernaut.enchantments.sword.");
        storage.add(new Entry(sword, "sword", true));
        ItemStack axe = named(Material.NETHERITE_AXE, "Juggernaut's Axe",
                "A mythic prize from the Juggernaut encounter.");
        enchant(axe, axeSpecs(), "juggernaut.enchantments.axe.");
        storage.add(new Entry(axe, "axe", true));
        ItemStack pick = named(Material.DIAMOND_PICKAXE, "Juggernaut's Pickaxe", "Temporary event tool.");
        enchant(pick, pickSpecs(), "juggernaut.enchantments.pickaxe.");
        storage.add(new Entry(pick, "pickaxe", false));
        storage.add(new Entry(mace.create(), "shock_mace", true));
        ItemStack spear = named(Material.NETHERITE_SPEAR, "Juggernaut's Spear", "Temporary event weapon.");
        enchant(spear, spearCoreSpecs(), "juggernaut.enchantments.spear.");
        // Additional combat enchants are applied only when this Paper/Minecraft
        // version reports that the actual netherite spear accepts them.
        for (String extra : List.of("sharpness", "fire_aspect", "looting")) {
            Enchantment enchantment = require(extra);
            if (enchantment.canEnchantItem(spear))
                spear.addUnsafeEnchantment(enchantment, enchantment.getMaxLevel());
        }
        storage.add(new Entry(spear, "spear", false));

        }

        addPotions(storage, longPotion(PotionType.LONG_SWIFTNESS), "speed_potion",
                count("speed-potions", 3));
        addPotions(storage, strength.splash(), "strength_potion",
                count("strength-potions", 8));
        addPotions(storage, longPotion(PotionType.LONG_FIRE_RESISTANCE), "fire_potion",
                count("fire-resistance-potions", 3));
        addPotions(storage, longPotion(PotionType.LONG_TURTLE_MASTER), "turtle_potion",
                count("turtle-master-potions", 2));
        addPotions(storage, longPotion(PotionType.STRONG_HEALING), "healing_potion",
                count("healing-potions", 4));
        addStacks(storage, Material.EXPERIENCE_BOTTLE, "experience_bottle", count("experience-bottles", 128));
        addStacks(storage, Material.WIND_CHARGE, "wind_charge", count("wind-charges", 64));
        addStacks(storage, Material.BREEZE_ROD, "breeze_rod", count("breeze-rods", 128));
        addStacks(storage, Material.COBWEB, "cobweb", count("cobwebs", 128));
        if (storage.size() > 36) throw new IllegalStateException("Configured Juggernaut loadout exceeds 36 inventory slots");
        return new Set(List.copyOf(storage), List.copyOf(armor));
    }

    ItemStack newCrown() {
        Plugin crownPlugin = Bukkit.getPluginManager().getPlugin("KingsCrown");
        if (crownPlugin == null || !crownPlugin.isEnabled())
            throw new IllegalStateException("KingsCrown must be enabled for the Juggernaut loadout");
        try {
            Object manager = crownPlugin.getClass().getMethod("b").invoke(crownPlugin);
            Object item = manager.getClass().getMethod("c").invoke(manager);
            if (item instanceof ItemStack stack) return stack;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not create a genuine KingsCrown item", ex);
        }
        throw new IllegalStateException("KingsCrown returned no wearable crown");
    }

    private ItemStack namedArmor(String piece) {
        Material material = Material.valueOf("NETHERITE_" + piece.toUpperCase());
        String title = "Juggernaut's " + Character.toUpperCase(piece.charAt(0)) + piece.substring(1);
        return named(material, title, "A mythic prize from the Juggernaut encounter.");
    }

    private ItemStack named(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.RED));
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY),
                Component.text("Genuine event equipment", NamedTextColor.DARK_RED)));
        item.setItemMeta(meta);
        return item;
    }

    private void enchant(ItemStack item, Map<String, Integer> specs, String configPath) {
        ItemMeta meta = item.getItemMeta();
        for (Map.Entry<String, Integer> entry : specs.entrySet()) {
            Enchantment enchantment = require(entry.getKey());
            if (entry.getKey().equals("sweeping_edge") && !enchantment.canEnchantItem(item)) continue;
            int level = Math.max(1, Math.min(10, config.getInt(configPath + entry.getKey(), entry.getValue())));
            meta.addEnchant(enchantment, level, true);
        }
        item.setItemMeta(meta);
    }

    private Enchantment require(String id) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));
        if (enchantment == null) throw new IllegalStateException("Missing enchantment on this server: " + id);
        return enchantment;
    }

    private ItemStack longPotion(PotionType type) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private void addPotions(List<Entry> entries, ItemStack potion, String kind, int count) {
        for (int i = 0; i < count; i++) entries.add(new Entry(potion.clone(), kind, false));
    }

    private void addStacks(List<Entry> entries, Material material, String kind, int amount) {
        ItemStack sample = new ItemStack(material);
        while (amount > 0) {
            int size = Math.min(amount, sample.getMaxStackSize());
            ItemStack stack = sample.clone(); stack.setAmount(size);
            entries.add(new Entry(stack, kind, false));
            amount -= size;
        }
    }

    private int count(String path, int fallback) {
        return Math.max(0, Math.min(256, config.getInt("juggernaut.loadout." + path, fallback)));
    }
}

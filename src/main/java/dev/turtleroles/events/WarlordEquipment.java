package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Genuine tagged equipment, with cooldowns saved outside player inventories. */
public final class WarlordEquipment implements Listener {
    private final JavaPlugin plugin;
    private final dev.turtleroles.combat.CooldownBars cooldownBars = new dev.turtleroles.combat.CooldownBars();
    private final YamlConfiguration settings;
    private final NamespacedKey pieceKey;
    private final NamespacedKey bladeKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey eventWarlordKey = new NamespacedKey("shocksmp", "warlord_active");
    private final File stateFile;
    private final YamlConfiguration state;
    private final Map<UUID, EffectState> strength = new HashMap<>();
    private final Map<UUID, EffectState> absorption = new HashMap<>();
    private final Map<UUID, Combo> combos = new HashMap<>();

    private record EffectState(PotionEffect previous, long previousExpiresAt, long ownExpiresAt) {}
    private record Combo(UUID target, String weapon, long lastHit, int hit) {}

    public WarlordEquipment(JavaPlugin plugin, YamlConfiguration settings) {
        this.plugin = plugin;
        this.settings = settings;
        pieceKey = new NamespacedKey("shocksmp", "warlord_piece");
        bladeKey = new NamespacedKey("shocksmp", "warlord_blade");
        instanceKey = new NamespacedKey("shocksmp", "warlord_instance");
        stateFile = new File(plugin.getDataFolder(), "warlord-state.yml");
        state = YamlConfiguration.loadConfiguration(stateFile);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    public ItemStack blade() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Warlord's Blade", NamedTextColor.DARK_RED));
        meta.lore(List.of(Component.text("Three strikes build a lethal rhythm.", NamedTextColor.GRAY),
                Component.text("Only the genuine tagged blade has this power.", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(bladeKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack armor(String slot) {
        Material material = switch (slot) {
            case "helmet" -> Material.NETHERITE_HELMET;
            case "chestplate" -> Material.NETHERITE_CHESTPLATE;
            case "leggings" -> Material.NETHERITE_LEGGINGS;
            case "boots" -> Material.NETHERITE_BOOTS;
            default -> throw new IllegalArgumentException("Unknown armor slot");
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Warlord's " + Character.toUpperCase(slot.charAt(0)) + slot.substring(1), NamedTextColor.DARK_RED));
        meta.lore(List.of(Component.text("Full set: Strength II", NamedTextColor.RED),
                Component.text("Survive at four hearts: four absorption hearts", NamedTextColor.GOLD),
                Component.text("Only the genuine tagged set has this power.", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, slot);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBlade(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(bladeKey, PersistentDataType.BYTE);
    }

    public boolean fullSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        String[] slots = {"boots", "leggings", "chestplate", "helmet"};
        Material[] materials = {Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET};
        if (armor.length < 4) return false;
        boolean eventCrown = player.getPersistentDataContainer().has(eventWarlordKey, PersistentDataType.BYTE)
                && armor[3] != null && armor[3].hasItemMeta()
                && armor[3].getItemMeta().getPersistentDataContainer().has(
                        NamespacedKey.fromString("kingscrown:crownsmp_crown"), PersistentDataType.BYTE);
        for (int i = 0; i < 4; i++) {
            if (i == 3 && eventCrown) continue;
            ItemStack item = armor[i];
            if (item == null || item.getType() != materials[i] || !item.hasItemMeta()
                    || !slots[i].equals(item.getItemMeta().getPersistentDataContainer().get(pieceKey, PersistentDataType.STRING))) return false;
        }
        return true;
    }

    public void clearEventBonuses(Player player) {
        clearOwnedStrength(player);
        clearOwnedAbsorption(player);
        combos.remove(player.getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            cooldownBars.timer(player,"armor","Warlord armor recovery",state.getLong(id+".cooldown-until")-now,Math.max(1,settings.getLong("warlord.armor.cooldown-seconds",60))*1000);
            if (!fullSet(player)) {
                clearOwnedStrength(player);
                clearOwnedAbsorption(player);
                continue;
            }
            PotionEffect current = player.getPotionEffect(PotionEffectType.STRENGTH);
            if (current == null || current.getAmplifier() < 1) {
                strength.putIfAbsent(id, new EffectState(current,
                        current == null ? 0 : current.getDuration() < 0 ? Long.MAX_VALUE : now + current.getDuration() * 50L,
                        Long.MAX_VALUE));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 35, 1, true, false, false));
            } else if (strength.containsKey(id) && current.getAmplifier() == 1 && current.getDuration() < 20) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 35, 1, true, false, false));
            }
            double threshold = settings.getDouble("warlord.armor.threshold-health", 8.0);
            if (player.getHealth() > threshold && !state.getBoolean(id + ".armed", true)) {
                state.set(id + ".armed", true);
                save();
            }
            EffectState active = absorption.get(id);
            if (active != null && now >= active.ownExpiresAt()) clearOwnedAbsorption(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !fullSet(player) || event.getFinalDamage() <= 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead() || !fullSet(player)) return;
            UUID id = player.getUniqueId();
            long now = System.currentTimeMillis();
            double threshold = settings.getDouble("warlord.armor.threshold-health", 8.0);
            if (player.getHealth() <= 0 || player.getHealth() > threshold || !state.getBoolean(id + ".armed", true)
                    || now < state.getLong(id + ".cooldown-until")) return;
            PotionEffect current = player.getPotionEffect(PotionEffectType.ABSORPTION);
            if (current != null && current.getAmplifier() >= 1) return;
            long duration = Math.max(20, settings.getLong("warlord.armor.absorption-seconds", 15) * 20);
            absorption.put(id, new EffectState(current,
                    current == null ? 0 : current.getDuration() < 0 ? Long.MAX_VALUE : now + current.getDuration() * 50L,
                    now + duration * 50));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int) duration, 1, true, false, true));
            state.set(id + ".armed", false);
            state.set(id + ".cooldown-until", now + settings.getLong("warlord.armor.cooldown-seconds", 60) * 1000);
            save();
            player.sendMessage(Component.text("Warlord's armor granted four absorption hearts.", NamedTextColor.GOLD));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBladeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || event.getFinalDamage() <= 0) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!isBlade(weapon)) { combos.remove(attacker.getUniqueId()); return; }
        String instance = weapon.getItemMeta().getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        long now = System.currentTimeMillis();
        long timeout = Math.max(100, settings.getLong("warlord.blade.timeout-millis", 3000));
        Combo prior = combos.get(attacker.getUniqueId());
        int hit = prior != null && prior.target().equals(event.getEntity().getUniqueId())
                && java.util.Objects.equals(prior.weapon(), instance) && now - prior.lastHit() <= timeout ? prior.hit() + 1 : 1;
        double step = Math.max(1.0, settings.getDouble("warlord.blade.step-multiplier", 1.10));
        event.setDamage(event.getDamage() * EventRules.bladeMultiplier(hit, step));
        if (hit >= 3) combos.remove(attacker.getUniqueId());
        else combos.put(attacker.getUniqueId(), new Combo(event.getEntity().getUniqueId(), instance, now, hit));
        String key = settings.getString("warlord.blade.sound", "minecraft:entity.player.attack.crit");
        if (key == null || key.isBlank()) key = "minecraft:entity.player.attack.crit";
        attacker.playSound(attacker.getLocation(), key, 0.8f, hit == 3 ? 1.35f : 0.9f + hit * 0.1f);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { cooldownBars.hideAll(event.getPlayer()); combos.remove(event.getPlayer().getUniqueId()); }

    private void clearOwnedStrength(Player player) {
        EffectState owned = strength.remove(player.getUniqueId());
        if (owned == null) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.STRENGTH);
        if (current != null && current.getAmplifier() == 1 && current.isAmbient() && !current.hasParticles()
                && !wearingCrown(player)) {
            player.removePotionEffect(PotionEffectType.STRENGTH);
            restore(player, owned, PotionEffectType.STRENGTH);
        }
    }

    private void clearOwnedAbsorption(Player player) {
        EffectState owned = absorption.remove(player.getUniqueId());
        if (owned == null) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.ABSORPTION);
        if (current != null && current.getAmplifier() == 1 && current.isAmbient() && !current.hasParticles()) {
            player.removePotionEffect(PotionEffectType.ABSORPTION);
            restore(player, owned, PotionEffectType.ABSORPTION);
        }
    }

    private void restore(Player player, EffectState state, PotionEffectType type) {
        PotionEffect before = state.previous();
        if (before == null || state.previousExpiresAt() <= System.currentTimeMillis()) return;
        int ticks = before.getDuration() < 0 ? -1 : (int) Math.max(1, (state.previousExpiresAt() - System.currentTimeMillis()) / 50);
        player.addPotionEffect(new PotionEffect(type, ticks, before.getAmplifier(), before.isAmbient(), before.hasParticles(), before.hasIcon()));
    }

    private boolean wearingCrown(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.hasItemMeta() && helmet.getItemMeta().getPersistentDataContainer()
                .has(NamespacedKey.fromString("kingscrown:crownsmp_crown"), PersistentDataType.BYTE);
    }

    public void shutdown() {
        cooldownBars.close();
        for (Player player : Bukkit.getOnlinePlayers()) { clearOwnedStrength(player); clearOwnedAbsorption(player); }
        save();
    }

    private void save() {
        try { state.save(stateFile); } catch (IOException ex) { plugin.getLogger().warning("Could not save Warlord cooldowns: " + ex.getMessage()); }
    }
}

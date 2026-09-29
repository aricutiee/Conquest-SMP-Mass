package dev.turtleroles.combat;

import dev.turtleroles.service.GameplayBypass;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.*;
import java.util.function.LongSupplier;

/** PvP timers persist in player data. No inventory removal, punishment or synthetic damage. */
public final class ConquestCombat implements Listener, AutoCloseable {
    private static final NamespacedKey TAG = new NamespacedKey("conquestsmp", "combat_until");
    private static final NamespacedKey MACE = new NamespacedKey("conquestsmp", "mace_until");
    private static final NamespacedKey GLIDING = new NamespacedKey("conquestsmp", "was_gliding");
    private final CooldownBars cooldownBars = new CooldownBars();
    private SpearLunges spears;
    private final JavaPlugin plugin;
    private final LongSupplier clock;
    private final long tagMillis, maceMillis;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> notices = new HashMap<>();
    private final Set<UUID> gliding = new HashSet<>();
    private final String title;
    private BukkitTask ticker;

    public ConquestCombat(JavaPlugin plugin) {
        this(plugin, settings(plugin), System::currentTimeMillis);
    }

    ConquestCombat(JavaPlugin plugin, YamlConfiguration settings, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
        tagMillis = Math.max(1, settings.getLong("tag-seconds", 45)) * 1000;
        maceMillis = Math.max(0, settings.getLong("mace-after-glide-seconds", 10)) * 1000;

        title = settings.getString("bossbar", "Combat: {seconds}s | Elytra and tridents disabled");
    }

    private static YamlConfiguration settings(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "combat.yml");
        if (!file.exists()) plugin.saveResource("combat.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        spears = new SpearLunges(plugin, settings(plugin), clock);
        spears.start();
        plugin.getServer().getOnlinePlayers().forEach(this::restore);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
        }, 1, 2);
        plugin.getLogger().info("Conquest combat enabled: " + tagMillis / 1000
                + "s PvP tag, " + maceMillis / 1000 + "s post-glide mace lock; pearls prohibited; crystal/anchor player damage blocked.");
    }

    private long expiry(Player player, NamespacedKey key) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
    }
    private void expiry(Player player, NamespacedKey key, long until) {
        player.getPersistentDataContainer().set(key, PersistentDataType.LONG, until);
    }
    public boolean tagged(Player player) { return !GameplayBypass.allowed(plugin, player) && expiry(player, TAG) > clock.getAsLong(); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        if (tagged(event.getPlayer()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Commands are disabled while you are in combat.");
        }
    }
    boolean maceLocked(Player player) {
        return !GameplayBypass.allowed(plugin, player) && (player.isGliding() || gliding.contains(player.getUniqueId()) || expiry(player, MACE) > clock.getAsLong());
    }

    private void tag(Player player) {
        if (GameplayBypass.allowed(plugin, player)) return;
        expiry(player, TAG, clock.getAsLong() + tagMillis);
        if (player.isGliding()) {
            stopGlide(player);
            player.setGliding(false);
        }
        if (player.getActiveItem().getType() == Material.TRIDENT) player.clearActiveItem();
        player.setRiptiding(false);
        refreshBar(player);
    }

    private void startGlide(Player player) {
        gliding.add(player.getUniqueId());
        player.getPersistentDataContainer().set(GLIDING, PersistentDataType.BYTE, (byte) 1);
    }
    private void stopGlide(Player player) {
        gliding.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(GLIDING);
        expiry(player, MACE, clock.getAsLong() + maceMillis);
    }
    private void restore(Player player) {
        if (player.getPersistentDataContainer().has(GLIDING, PersistentDataType.BYTE)) stopGlide(player);
        refresh(player);
    }
    void refresh(Player player) {
        if (GameplayBypass.allowed(plugin, player)) { hide(player); cooldownBars.hideAll(player); return; }

        if (player.isGliding()) {
            startGlide(player);
            if (tagged(player)) { stopGlide(player); player.setGliding(false); }
        } else if (gliding.contains(player.getUniqueId())) stopGlide(player);
        refreshBar(player);
        long left = expiry(player, MACE) - clock.getAsLong();
        if (player.isGliding() || gliding.contains(player.getUniqueId()))
            cooldownBars.show(player, "mace", "Mace unavailable while gliding", 1);
        else cooldownBars.timer(player, "mace", "Mace recovery", left, maceMillis);
    }
    private void refreshBar(Player player) {
        long left = expiry(player, TAG) - clock.getAsLong();
        if (left <= 0 || player.isDead()) { hide(player); return; }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = BossBar.bossBar(Component.empty(), 1, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.name(Component.text(title.replace("{seconds}", Long.toString((left + 999) / 1000)), NamedTextColor.LIGHT_PURPLE));
        bar.progress(Math.min(1f, Math.max(0f, (float) left / tagMillis)));
    }
    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
    private void deny(Player player, String text) {
        long now = clock.getAsLong();
        if (now - notices.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) >= 1000) {
            notices.put(player.getUniqueId(), now);
            player.sendActionBar(Component.text(text, NamedTextColor.RED));
        }
    }
    private boolean deniedAttack(Player player) {
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held == Material.TRIDENT && tagged(player)) {
            deny(player, "Tridents are disabled during combat."); return true;
        }
        if (held == Material.MACE && maceLocked(player)) {
            deny(player, "Maces are disabled while gliding and for " + maceMillis / 1000 + " seconds afterward."); return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void attack(PrePlayerAttackEntityEvent event) {
        if (deniedAttack(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void guardDamage(EntityDamageEvent event) {
        if(event.isCancelled())return;
        if(event.getEntity() instanceof Player && event.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION){
            Entity direct=event.getDamageSource().getDirectEntity();
            if(event instanceof EntityDamageByEntityEvent hit)direct=hit.getDamager();
            if(direct instanceof org.bukkit.entity.minecart.ExplosiveMinecart){event.setCancelled(true);return;}
            if(direct instanceof TNTPrimed)event.setDamage(event.getDamage()*.5);
        }
        if (event.getEntity() instanceof Player && harmlessExplosion(event)) { event.setCancelled(true); return; }
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (hit.getDamager() instanceof Player attacker
                    && (hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                    && deniedAttack(attacker)) event.setCancelled(true);
            // Also catches a trident thrown before its owner became tagged.
            if (hit.getDamager() instanceof Trident trident && trident.getShooter() instanceof Player player
                    && tagged(player)) event.setCancelled(true);
        }
    }
    static boolean harmlessExplosion(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return false;
        if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof EnderCrystal) return true;
        if (event.getDamageSource().getDirectEntity() instanceof EnderCrystal) return true;
        // Paper retains this snapshot even after the anchor block has become air.
        return event instanceof EntityDamageByBlockEvent hit && hit.getDamagerBlockState() != null
                && hit.getDamagerBlockState().getType() == Material.RESPAWN_ANCHOR;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player victim) || !victim.getWorld().getPVP()) return;
        double dealt = event.getFinalDamage();
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
            dealt -= event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
        if (dealt <= 0) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        tag(attacker); tag(victim);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void glideGuard(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && tagged(player)) {
            event.setCancelled(true); deny(player, "Elytra is disabled during combat.");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void glideChanged(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.isGliding()) startGlide(player); else stopGlide(player);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (GameplayBypass.allowed(plugin, event.getPlayer())) return;
        if (event.getAction().isRightClick() && event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL) {
            event.setUseItemInHand(Event.Result.DENY); deny(event.getPlayer(), "Pearls aren't allowed on this server.");
        }
        if (event.getAction().isRightClick() && event.getItem() != null
                && event.getItem().getType() == Material.TRIDENT && tagged(event.getPlayer())) {
            event.setUseItemInHand(Event.Result.DENY);
            deny(event.getPlayer(), "Tridents are disabled during combat.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player && tagged(player))
            event.setCancelled(true);
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player && !GameplayBypass.allowed(plugin, player)) {
            event.setCancelled(true); deny(player, "Pearls aren't allowed on this server.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !GameplayBypass.allowed(plugin, player) && event.getItem().getItemStack().getType() == Material.ENDER_PEARL) {
            event.setCancelled(true); deny(player, "Pearls aren't allowed on this server.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pearlTeleport(PlayerTeleportEvent event) {
        if (GameplayBypass.allowed(plugin, event.getPlayer())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            event.setCancelled(true); deny(event.getPlayer(), "Pearls aren't allowed on this server.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void riptide(PlayerRiptideEvent event) {
        if (tagged(event.getPlayer())) { event.setCancelled(true); deny(event.getPlayer(), "Tridents are disabled during combat."); }
    }
    @EventHandler public void join(PlayerJoinEvent event) { restore(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding() || gliding.contains(player.getUniqueId())) stopGlide(player);
        cooldownBars.hideAll(player);
        hide(player); notices.remove(player.getUniqueId());
    }
    @EventHandler public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        player.getPersistentDataContainer().remove(TAG);
        if (player.isGliding() || gliding.contains(player.getUniqueId())) stopGlide(player);
        cooldownBars.hideAll(player);
        hide(player);
    }
    @Override public void close() {
        if (ticker != null) ticker.cancel();
        if (spears != null) spears.close();
        cooldownBars.close();
        HandlerList.unregisterAll(this);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isGliding() || gliding.contains(player.getUniqueId())) stopGlide(player);
            hide(player);
        }
        bars.clear(); notices.clear(); gliding.clear();
    }
}

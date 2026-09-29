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
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void arrowIgnition(EntityCombustByEntityEvent event) {
        if(event.getEntity() instanceof Player && (event.getCombuster() instanceof Arrow || event.getCombuster() instanceof SpectralArrow))
            event.setCancelled(true);
    }
    private static final NamespacedKey TAG = new NamespacedKey("conquestsmp", "combat_until");
    private static final NamespacedKey OPPONENTS = new NamespacedKey("conquestsmp", "combat_opponents");
    private static final NamespacedKey UNTRACKED = new NamespacedKey("conquestsmp", "combat_untracked_until");
    private final Map<UUID,Long> truceOffers = new HashMap<>();
    private static final java.util.Set<String> TRUCE_WORDS=java.util.Set.of("my bad","mb","bro","mb og","og");
    private static final NamespacedKey MACE = new NamespacedKey("conquestsmp", "mace_until");
    private static final NamespacedKey GLIDING = new NamespacedKey("conquestsmp", "was_gliding");
    private final CooldownBars cooldownBars = new CooldownBars();
    private SpearLunges spears;
    private TotemLimit totems;
    private static final NamespacedKey APPLE = new NamespacedKey("conquestsmp", "enchanted_apple_until");
    private HappyGhasts happyGhasts;
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
        happyGhasts=new HappyGhasts(plugin,this);
        totems=new TotemLimit(plugin);
        plugin.getServer().getPluginManager().registerEvents(totems,plugin);
        spears = new SpearLunges(plugin, settings(plugin), clock);
        spears.start();
        plugin.getServer().getOnlinePlayers().forEach(this::restore);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
        }, 1, 2);
        plugin.getLogger().info("Conquest combat enabled: " + tagMillis / 1000
                + "s PvP tag, " + maceMillis / 1000 + "s post-glide mace lock; pearls prohibited; crystal/anchor/bed player damage blocked; TNT minecarts capped at 4 hearts.");
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
        boolean entering=!tagged(player);
        expiry(player, TAG, clock.getAsLong() + tagMillis);
        HappyGhasts.dismount(player);
        if(entering && java.util.concurrent.ThreadLocalRandom.current().nextInt(3)==0)
            player.sendMessage(Component.text("Tip: If both you and your opponent say 'my bad', 'mb', 'bro' or 'mb og' in public chat, you agree to end combat with each other.",NamedTextColor.LIGHT_PURPLE));
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
        if(tagged(player))HappyGhasts.dismount(player);
        if(totems!=null)totems.enforce(player);
        cooldownBars.timer(player,"enchanted-apple","Enchanted golden apple",expiry(player,APPLE)-clock.getAsLong(),60000);
        refreshBar(player);
        long left = expiry(player, MACE) - clock.getAsLong();
        if (player.isGliding() || gliding.contains(player.getUniqueId()))
            cooldownBars.show(player, "mace", "Mace unavailable while gliding", 1);
        else cooldownBars.timer(player, "mace", "Mace recovery", left, maceMillis);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void appleCheck(PlayerItemConsumeEvent event) {
        Player p=event.getPlayer();
        if(event.getItem().getType()!=Material.ENCHANTED_GOLDEN_APPLE||GameplayBypass.allowed(plugin,p))return;
        long left=expiry(p,APPLE)-clock.getAsLong();
        if(left>0){event.setCancelled(true);deny(p,"Wait "+((left+999)/1000)+"s before eating another enchanted golden apple.");}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void appleConsumed(PlayerItemConsumeEvent event) {
        Player p=event.getPlayer();
        if(event.getItem().getType()==Material.ENCHANTED_GOLDEN_APPLE&&!GameplayBypass.allowed(plugin,p)) {
            expiry(p,APPLE,clock.getAsLong()+60000);
            cooldownBars.timer(p,"enchanted-apple","Enchanted golden apple",60000,60000);
        }
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
        // Paper retains this snapshot even after the exploding block has become air.
        if (event instanceof EntityDamageByBlockEvent hit) {
            Material type = hit.getDamagerBlockState() != null ? hit.getDamagerBlockState().getType()
                    : hit.getDamager() != null ? hit.getDamager().getType() : Material.AIR;
            return type == Material.RESPAWN_ANCHOR || type.name().endsWith("_BED");
        }
        return false;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void limitExplosions(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;
        if (harmlessExplosion(event)) { event.setCancelled(true); return; }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        if (event.getDamageSource().getDirectEntity() instanceof org.bukkit.entity.minecart.ExplosiveMinecart
                || event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof org.bukkit.entity.minecart.ExplosiveMinecart) {
            // Four hearts of final health damage per explosion, retaining vanilla reductions.
            WeaponDamageCaps.cap(event, 8.0);
        }
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
        trackOpponent(attacker,victim); trackOpponent(victim,attacker);
        truceOffers.remove(attacker.getUniqueId());truceOffers.remove(victim.getUniqueId());
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
    static boolean trucePhrase(String message) {
        return TRUCE_WORDS.contains(message.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?,]+$", "").trim().replaceAll("\\s+", " "));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void chatTruce(io.papermc.paper.event.player.AsyncChatEvent event) {
        if(!trucePhrase(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.originalMessage())))return;
        UUID id=event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin,()-> {
            Player player=plugin.getServer().getPlayer(id);if(player!=null && player.isOnline())offerTruce(player);
        });
    }
    private Map<UUID,Long> opponents(Player player) {
        var result=new HashMap<UUID,Long>();
        String saved=player.getPersistentDataContainer().getOrDefault(OPPONENTS,PersistentDataType.STRING, "");
        for(String entry:saved.split(","))try {
            String[] pair=entry.split("=");long until=Long.parseLong(pair[1]);
            if(until>clock.getAsLong())result.put(UUID.fromString(pair[0]),until);
        }catch(RuntimeException ignored){}
        return result;
    }
    private void saveOpponents(Player player,Map<UUID,Long> opponents) {
        String saved=opponents.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(","));
        if(saved.isEmpty())player.getPersistentDataContainer().remove(OPPONENTS);
        else player.getPersistentDataContainer().set(OPPONENTS,PersistentDataType.STRING,saved);
    }
    private void trackOpponent(Player player,Player opponent) {
        if(GameplayBypass.allowed(plugin,player))return;
        var peers=opponents(player);
        long known=peers.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        // Preserve pre-upgrade/external combat timers that have no known opponent.
        if(expiry(player,TAG)>Math.max(clock.getAsLong(),known))expiry(player,UNTRACKED,Math.max(expiry(player,UNTRACKED),expiry(player,TAG)));
        peers.put(opponent.getUniqueId(),clock.getAsLong()+tagMillis);saveOpponents(player,peers);
    }
    void offerTruce(Player player) {
        if(!tagged(player))return;
        long now=clock.getAsLong();truceOffers.entrySet().removeIf(e->e.getValue()<=now);
        truceOffers.put(player.getUniqueId(),expiry(player,TAG));
        for(UUID id:new HashSet<>(opponents(player).keySet())) {
            Player opponent=plugin.getServer().getPlayer(id);
            if(opponent==null || !opponent.isOnline() || !tagged(opponent) || truceOffers.getOrDefault(id,0L)<=now)continue;
            if(!opponents(opponent).containsKey(player.getUniqueId()))continue;
            endPair(player,opponent);endPair(opponent,player);
            var message=Component.text("Truce accepted with ",NamedTextColor.LIGHT_PURPLE);
            player.sendMessage(message.append(Component.text(opponent.getName()+". "+(tagged(player)?"Other combat is still active.":"You are out of combat."))));
            opponent.sendMessage(message.append(Component.text(player.getName()+". "+(tagged(opponent)?"Other combat is still active.":"You are out of combat."))));
        }
    }
    private void endPair(Player player,Player opponent) {
        var peers=opponents(player);peers.remove(opponent.getUniqueId());saveOpponents(player,peers);
        long remaining=Math.max(expiry(player,UNTRACKED),peers.values().stream().mapToLong(Long::longValue).max().orElse(0L));
        if(remaining>clock.getAsLong())expiry(player,TAG,remaining);else player.getPersistentDataContainer().remove(TAG);
        refreshBar(player);
    }
    @EventHandler public void join(PlayerJoinEvent event) { restore(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        truceOffers.remove(player.getUniqueId());
        if (player.isGliding() || gliding.contains(player.getUniqueId())) stopGlide(player);
        cooldownBars.hideAll(player);
        hide(player); notices.remove(player.getUniqueId());
    }
    @EventHandler public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        player.getPersistentDataContainer().remove(TAG);
        player.getPersistentDataContainer().remove(OPPONENTS);player.getPersistentDataContainer().remove(UNTRACKED);
        truceOffers.remove(player.getUniqueId());
        if (player.isGliding() || gliding.contains(player.getUniqueId())) stopGlide(player);
        cooldownBars.hideAll(player);
        hide(player);
    }
    @Override public void close() {
        if (ticker != null) ticker.cancel();
        if (spears != null) spears.close();
        if(happyGhasts!=null)HandlerList.unregisterAll(happyGhasts);
        cooldownBars.close();
        if(totems!=null)HandlerList.unregisterAll(totems);
        HandlerList.unregisterAll(this);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isGliding() || gliding.contains(player.getUniqueId())) stopGlide(player);
            hide(player);
        }
        bars.clear(); notices.clear(); gliding.clear();truceOffers.clear();
    }
}

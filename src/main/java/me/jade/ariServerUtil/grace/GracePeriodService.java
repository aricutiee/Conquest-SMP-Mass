package me.jade.ariServerUtil.grace;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.announcements.AnnouncementService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.DurationParser;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GracePeriodService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private final AnnouncementService announcements;
    private final GraceSidebar sidebar = new GraceSidebar();
    private final GraceCountdown countdown = new GraceCountdown();
    private final Map<UUID, NeedsSnapshot> needsSnapshots = new HashMap<>();
    private final Map<UUID, Difficulty> worldDifficulties = new HashMap<>();
    private Instant endTime;

    public GracePeriodService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit, AnnouncementService announcements) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        this.announcements = announcements;
        load();
        if (active()) enableWorldPeace();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
    }

    public boolean active() {
        return endTime != null && endTime.isAfter(Instant.now());
    }

    public Duration remaining() {
        return active() ? Duration.between(Instant.now(), endTime) : Duration.ZERO;
    }

    public void start(Player staff, Duration duration, boolean replace) {
        if (duration.isNegative() || duration.isZero()) {
            Text.send(staff, "<red>Grace duration must be greater than zero.");
            return;
        }
        if (active() && !replace) {
            Text.send(staff, "<red>A grace period is already active.");
            return;
        }
        endTime = Instant.now().plus(duration);
        countdown.reset();
        save();
        Bukkit.broadcast(Text.mm(config.message("grace.start").replace("<duration>", DurationParser.human(duration))));
        announcements.broadcast(config.string("grace.start-title", "<green><bold>GRACE PERIOD STARTED"),
                config.string("grace.start-subtitle", "<gray>PvP is disabled for <yellow><duration>")
                        .replace("<duration>", DurationParser.human(duration)));
        sidebar.update(Bukkit.getOnlinePlayers(), GraceCountdown.secondsRemaining(endTime, Instant.now()));
        enableWorldPeace();
        Bukkit.getOnlinePlayers().forEach(this::applyProtection);
        audit.record(staff, "GRACE_START", null, "", DurationParser.human(duration), "success", "");
    }

    public void stop(Player staff) {
        endTime = null;
        countdown.reset();
        sidebar.close();
        restoreEveryone();
        restoreWorldDifficulties();
        save();
        Bukkit.broadcast(Text.mm(config.message("grace.stop")));
        audit.record(staff, "GRACE_STOP", null, "", "", "success", "");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!active() || !(event.getEntity() instanceof Player)) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker == null) return;
        event.setCancelled(true);
        Text.send(attacker, config.message("grace.blocked"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!active() || !(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!active() || !(event.getEntity() instanceof Player player)) return;
        event.setCancelled(true);
        applyProtection(player);
    }

    private void tick() {
        if (endTime == null) return;
        long seconds = GraceCountdown.secondsRemaining(endTime, Instant.now());
        if (seconds == 0) {
            endTime = null;
            sidebar.close();
            countdown.reset();
            restoreEveryone();
            restoreWorldDifficulties();
            save();
            Bukkit.broadcast(Text.mm(config.message("grace.expired")));
            announcements.broadcast(config.string("grace.end-title", "<red><bold>GRACE PERIOD ENDED"),
                    config.string("grace.end-subtitle", "<yellow>PvP is now enabled!"));
            return;
        }
        Bukkit.getOnlinePlayers().forEach(this::applyProtection);
        sidebar.update(Bukkit.getOnlinePlayers(), seconds);
        if (countdown.shouldShowTitle(seconds)) {
            var title = net.kyori.adventure.title.Title.title(Text.mm("<red><bold>" + seconds),
                    Text.mm(config.string("grace.countdown-subtitle", "<yellow>Grace period ending...")),
                    net.kyori.adventure.title.Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO));
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.showTitle(title);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            });
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (active()) {
            applyProtection(event.getPlayer());
            sidebar.update(java.util.List.of(event.getPlayer()), GraceCountdown.secondsRemaining(endTime, Instant.now()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (active()) makeWorldPeaceful(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        Difficulty original = worldDifficulties.get(event.getWorld().getUID());
        if (original != null) event.getWorld().setDifficulty(original);
    }

    public void close() {
        restoreEveryone();
        restoreWorldDifficulties();
        sidebar.close();
    }

    private void enableWorldPeace() {
        if (!config.bool("grace.set-world-peaceful", true)) return;
        Bukkit.getWorlds().forEach(this::makeWorldPeaceful);
    }

    private void makeWorldPeaceful(World world) {
        worldDifficulties.putIfAbsent(world.getUID(), world.getDifficulty());
        if (world.getDifficulty() != Difficulty.PEACEFUL) world.setDifficulty(Difficulty.PEACEFUL);
    }

    private void restoreWorldDifficulties() {
        Bukkit.getWorlds().forEach(world -> {
            Difficulty original = worldDifficulties.get(world.getUID());
            if (original != null && world.getDifficulty() != original) world.setDifficulty(original);
        });
        worldDifficulties.clear();
    }

    private void applyProtection(Player player) {
        needsSnapshots.computeIfAbsent(player.getUniqueId(), ignored ->
                new NeedsSnapshot(player.getFoodLevel(), player.getSaturation(), player.getExhaustion()));
        if (player.getFoodLevel() != 20) player.setFoodLevel(20);
        if (player.getSaturation() != 20.0f) player.setSaturation(20.0f);
        if (player.getExhaustion() != 0.0f) player.setExhaustion(0.0f);
        if (player.getFireTicks() > 0) player.setFireTicks(0);
    }

    private void restoreEveryone() {
        Bukkit.getOnlinePlayers().forEach(this::restore);
        needsSnapshots.clear();
    }

    private void restore(Player player) {
        NeedsSnapshot snapshot = needsSnapshots.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
    }

    private record NeedsSnapshot(int foodLevel, float saturation, float exhaustion) {}

    private void save() {
        String savedEnd = endTime == null ? "0" : Long.toString(endTime.toEpochMilli());
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO state(key,value) VALUES('grace_end',?)")) {
                ps.setString(1, savedEnd);
                ps.executeUpdate();
            }
        });
    }

    private void load() {
        try {
            String value = database.query(conn -> {
                try (var ps = conn.prepareStatement("SELECT value FROM state WHERE key='grace_end'")) {
                    try (var rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : "0";
                    }
                }
            }).join();
            long millis = Long.parseLong(value);
            endTime = millis > System.currentTimeMillis() ? Instant.ofEpochMilli(millis) : null;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not load grace period state: " + ex.getMessage());
        }
    }
}

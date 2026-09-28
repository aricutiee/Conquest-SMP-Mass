package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One target and one hunter per participant, with a fair final duel. */
public final class Assassins implements Listener {
    private static final NamedTextColor[] COLORS = {NamedTextColor.AQUA, NamedTextColor.GOLD, NamedTextColor.GREEN,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.BLUE, NamedTextColor.RED};
    private final JavaPlugin plugin;
    private final YamlConfiguration settings;
    private final File file;
    private final YamlConfiguration state;
    private final Random random = new Random();
    private final Set<UUID> enrolled = new HashSet<>();
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, NamedTextColor> colors = new HashMap<>();
    private final Map<UUID, Long> disconnects = new HashMap<>();
    private BukkitTask task;
    private boolean enrolling;
    private boolean running;
    private long joinClosesAt;
    private Runnable onReady = () -> {};
    private Runnable onFinish = () -> {};
    private Consumer<Player> prize = ignored -> {};

    public Assassins(JavaPlugin plugin, YamlConfiguration settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "assassins-state.yml");
        state = YamlConfiguration.loadConfiguration(file);
        if (state.getBoolean("running") || state.getBoolean("enrolling")) {
            plugin.getLogger().warning("An interrupted Assassins event was cancelled safely after restart.");
            state.set("running", false); state.set("enrolling", false); save();
        }
    }

    public boolean active() { return enrolling || running; }
    public boolean enrolling() { return enrolling; }
    public int count() { return enrolled.size(); }

    public void openJoinWindow(Runnable ready, Runnable finish, Consumer<Player> prize) {
        if (active()) throw new IllegalStateException("Assassins is active");
        this.onReady = ready; this.onFinish = finish; this.prize = prize;
        enrolled.clear(); targets.clear(); colors.clear(); disconnects.clear();
        state.set("rewarded", null);
        enrolling = true;
        joinClosesAt = System.currentTimeMillis() + Math.max(10, settings.getLong("assassins.join-window-seconds", 60)) * 1000;
        state.set("enrolling", true); save();
        Bukkit.broadcast(Component.text("Assassins enrollment is open! /events assassins join", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("You hunt one target. Attacking your hunter eliminates YOU from the event."
                + " With two players left, retaliation is disabled for the final duel.", NamedTextColor.YELLOW));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    public void join(Player player) {
        if (!enrolling) { player.sendMessage(Component.text("Assassins enrollment is closed.", NamedTextColor.RED)); return; }
        if (!enrolled.add(player.getUniqueId())) { player.sendMessage(Component.text("You are already enrolled.", NamedTextColor.YELLOW)); return; }
        player.sendMessage(Component.text("Joined Assassins. Attacking the player hunting you eliminates you from this event."
                + " This rule is off in the final duel.", NamedTextColor.GREEN));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (enrolling) {
            if (now >= joinClosesAt) begin();
            return;
        }
        if (!running) return;
        long grace = Math.max(5, settings.getLong("assassins.disconnect-grace-seconds", 60)) * 1000;
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(disconnects.entrySet()))
            if (now - entry.getValue() >= grace) eliminate(entry.getKey(), "disconnect timeout");
        for (Map.Entry<UUID, UUID> entry : targets.entrySet()) {
            Player hunter = Bukkit.getPlayer(entry.getKey());
            if (hunter == null || !hunter.isOnline()) continue;
            UUID targetId = entry.getValue();
            Player target = Bukkit.getPlayer(targetId);
            String name = target != null ? target.getName() : name(targetId);
            Component text = Component.text("Target: ", NamedTextColor.GRAY)
                    .append(Component.text(name, colors.getOrDefault(hunter.getUniqueId(), NamedTextColor.YELLOW)).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            if (target == null || !target.isOnline()) text = text.append(Component.text("  Offline", NamedTextColor.GRAY));
            else if (hunter.getWorld().equals(target.getWorld())) text = text.append(Component.text("  X "
                    + target.getLocation().getBlockX() + " Y " + target.getLocation().getBlockY()
                    + " Z " + target.getLocation().getBlockZ(), NamedTextColor.WHITE));
            else text = text.append(Component.text("  Another dimension: " + dimension(target.getWorld()), NamedTextColor.GRAY));
            ActionBarBus.offer(hunter, "assassins", text, 50, 700);
        }
    }

    private void begin() {
        enrolling = false; state.set("enrolling", false);
        enrolled.removeIf(id -> Bukkit.getPlayer(id) == null);
        int minimum = Math.max(3, settings.getInt("assassins.minimum-participants", 3));
        if (enrolled.size() < minimum) {
            Bukkit.broadcast(Component.text("Assassins cancelled: at least " + minimum + " online players were needed.", NamedTextColor.RED));
            stop(false); return;
        }
        targets.putAll(EventRules.cycle(new ArrayList<>(enrolled), random));
        targets.keySet().forEach(id -> colors.put(id, randomColor()));
        running = true; state.set("running", true); save();
        Bukkit.broadcast(Component.text("Assassins has begun with " + enrolled.size() + " players!", NamedTextColor.RED));
        onReady.run();
    }

    private NamedTextColor randomColor() { return COLORS[random.nextInt(COLORS.length)]; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!running || !(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
        if (attacker == null || !targets.containsKey(attacker.getUniqueId()) || !targets.containsKey(victim.getUniqueId())) return;
        // The final two must be allowed to fight each other.
        if (targets.size() > 2 && targets.get(victim.getUniqueId()).equals(attacker.getUniqueId())) {
            eliminate(attacker.getUniqueId(), "attacked their hunter");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!running) return;
        UUID victim = event.getEntity().getUniqueId();
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent fatal)) return;
        Player attacker = fatal.getDamager() instanceof Player player ? player
                : fatal.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
        if (attacker != null && victim.equals(targets.get(attacker.getUniqueId())))
            eliminate(victim, "killed by their assigned hunter");
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (running && targets.containsKey(id)) disconnects.put(id, System.currentTimeMillis());
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { disconnects.remove(event.getPlayer().getUniqueId()); }

    private void eliminate(UUID id, String reason) {
        if (!running || !targets.containsKey(id)) return;
        Map<UUID, UUID> old = new HashMap<>(targets);
        targets.clear(); targets.putAll(EventRules.removeFromCycle(old, id));
        enrolled.remove(id); disconnects.remove(id); colors.remove(id);
        Player removed = Bukkit.getPlayer(id);
        if (removed != null) ActionBarBus.clear(removed, "assassins");
        for (Map.Entry<UUID, UUID> entry : targets.entrySet())
            if (!entry.getValue().equals(old.get(entry.getKey()))) colors.put(entry.getKey(), randomColor());
        Bukkit.broadcast(Component.text(name(id) + " was eliminated from Assassins (" + reason + ").", NamedTextColor.YELLOW));
        if (targets.size() == 2) Bukkit.broadcast(Component.text("Final duel! Retaliation rule is now disabled.", NamedTextColor.GOLD));
        if (targets.size() == 1) finishWinner(targets.keySet().iterator().next());
    }

    private void finishWinner(UUID winnerId) {
        Player winner = Bukkit.getPlayer(winnerId);
        String name = name(winnerId);
        Bukkit.broadcast(Component.text("Assassins winner: " + name + "!", NamedTextColor.GOLD));
        for (Player player : Bukkit.getOnlinePlayers()) player.showTitle(Title.title(
                Component.text("ASSASSINS WINNER", NamedTextColor.GOLD), Component.text(name, NamedTextColor.YELLOW)));
        if (winner != null && !state.getBoolean("rewarded." + winnerId)) {
            state.set("rewarded." + winnerId, true); save();
            prize.accept(winner);
        }
        stop(false);
    }

    public void stop(boolean announce) {
        if (!active() && task == null) return;
        if (task != null) { task.cancel(); task = null; }
        if (announce) Bukkit.broadcast(Component.text("Assassins was stopped.", NamedTextColor.YELLOW));
        for (UUID id : targets.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) ActionBarBus.clear(player, "assassins");
        }
        enrolling = false; running = false;
        enrolled.clear(); targets.clear(); colors.clear(); disconnects.clear();
        state.set("enrolling", false); state.set("running", false); save();
        onFinish.run();
    }

    private String name(UUID id) {
        String result = Bukkit.getOfflinePlayer(id).getName();
        return result == null ? id.toString().substring(0, 8) : result;
    }
    private String dimension(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "The Nether";
            case THE_END -> "The End";
            default -> world.getName();
        };
    }
    private void save() {
        try { state.save(file); } catch (IOException ex) { plugin.getLogger().warning("Could not save Assassins recovery state: " + ex.getMessage()); }
    }
}

package dev.biomeraces;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import java.time.Duration;
import java.util.*;
import java.util.random.RandomGenerator;

/** One animation per UUID. The award is written before the reveal, and the draw before animation. */
public final class RaceRoll implements Listener, AutoCloseable {
    private final RaceModule plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> queued = new HashMap<>();
    private final RandomGenerator random = RandomGenerator.getDefault();
    private long ticks;
    static final class Session {
        final Race result;
        final boolean preview;
        final long start;
        final RollSettings settings;
        final int[] frameTicks;
        int nextFrame;
        Race last;
        Session(Race result, boolean preview, long start, RollSettings settings) {
            this.result = result; this.preview = preview; this.start = start;
            this.settings = settings; this.frameTicks = settings.frameTicks();
        }
    }
    RaceRoll(RaceModule plugin) { this.plugin = plugin; }
    public void queueAssignment(Player player) {
        if (plugin.state(player).base == null && !sessions.containsKey(player.getUniqueId()))
            queued.putIfAbsent(player.getUniqueId(), ticks + settings().joinDelay);
    }
    private RollSettings settings() { return new RollSettings(plugin.settings().yaml); }
    public boolean start(Player player, boolean preview) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) { plugin.message(player, "roll-busy"); return false; }
        if (!player.isOnline() || player.isDead()) { plugin.message(player, "roll-unavailable"); return false; }
        PlayerState state = plugin.state(player);
        if (!preview && state.base != null) return false;
        if (preview && (state.pendingRoll != null || (state.base == null && queued.containsKey(id)))) {
            plugin.message(player, "roll-busy"); return false;
        }
        RollSettings config = settings();
        Race result = preview ? config.choose(random) : state.pendingRoll;
        if (result == null) { result = config.choose(random); state.pendingRoll = result; }
        if (!preview && !plugin.persist()) { plugin.message(player, "roll-save-failed"); return false; }
        queued.remove(id);
        Session session = new Session(result, preview, ticks, config);
        sessions.put(id, session);
        showFrame(player, session);
        return true;
    }
    public void tick() {
        ticks++;
        for (UUID id : List.copyOf(queued.keySet())) {
            if (ticks < queued.get(id)) continue;
            queued.remove(id); Player player = Bukkit.getPlayer(id);
            if (player != null && !player.isDead()) start(player, false);
        }
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id); Session session = sessions.get(id);
            if (player == null || !player.isOnline() || player.isDead()) { cancel(id); continue; }
            if (!session.preview && plugin.state(player).base != null) { cancel(id); continue; }
            long elapsed = ticks - session.start;
            if (elapsed >= session.settings.duration) { finish(player, session); continue; }
            if (session.nextFrame < session.frameTicks.length && elapsed >= session.frameTicks[session.nextFrame]) showFrame(player, session);
        }
    }
    private void showFrame(Player player, Session session) {
        List<Race> choices = RollSettings.BASE.stream().filter(race -> race != session.last).toList();
        Race displayed = choices.get(random.nextInt(choices.size())); session.last = displayed;
        Component subtitle = mm(session.preview ? "roll.preview-subtitle" : "roll.spinning-subtitle");
        player.showTitle(Title.title(name(displayed, session.settings), subtitle,
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)));
        double fraction = session.nextFrame / (double) Math.max(1, session.settings.frames - 1);
        float high = (float) plugin.settings().yaml.getDouble("roll.tick-pitch-start", 1.8);
        float low = (float) plugin.settings().yaml.getDouble("roll.tick-pitch-end", .7);
        sound(player, "tick", (float) (high + (low - high) * fraction));
        session.nextFrame++;
    }
    private void finish(Player player, Session session) {
        if (!session.preview) {
            PlayerState state = plugin.state(player);
            state.base = session.result; state.pendingRoll = null;
            if (!plugin.persist()) {
                state.base = null; state.pendingRoll = session.result;
                cancel(player.getUniqueId()); plugin.message(player, "roll-save-failed"); return;
            }
            state.chain.reset(); plugin.effects().clearSelf(player); plugin.runtime().updatePassives(player);
        }
        sessions.remove(player.getUniqueId());
        player.showTitle(Title.title(name(session.result, session.settings),
            mm(session.preview ? "roll.preview-result-subtitle" : "roll.result-subtitle"),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(750))));
        sound(player, "reveal", (float) plugin.settings().yaml.getDouble("roll.reveal-pitch", 1));
        if (!session.preview) plugin.message(player, "selected", "<race>", session.result.label());
    }
    private Component name(Race race, RollSettings settings) {
        return Component.text(race.label(), settings.color(race)).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
    }
    private Component mm(String path) { return MiniMessage.miniMessage().deserialize(plugin.settings().yaml.getString(path, "")); }
    private void sound(Player player, String key, float pitch) {
        float volume = (float) plugin.settings().yaml.getDouble("roll." + key + "-volume");
        if (volume > 0) player.playSound(player.getLocation(), plugin.settings().yaml.getString("roll." + key + "-sound"), SoundCategory.PLAYERS, volume, pitch);
    }
    public boolean running(UUID id) { return sessions.containsKey(id); }
    public void cancel(UUID id) {
        queued.remove(id);
        if (sessions.remove(id) != null) { Player player = Bukkit.getPlayer(id); if (player != null) player.clearTitle(); }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { queueAssignment(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent event) { cancel(event.getEntity().getUniqueId()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { queueAssignment(event.getPlayer()); }
    @Override public void close() { for (UUID id : List.copyOf(sessions.keySet())) cancel(id); queued.clear(); }
}

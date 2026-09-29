package dev.biomeraces;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.*;
import java.util.Objects;
import java.util.logging.Logger;

/** Races share Conquest's plugin lifecycle, with their own validated configuration and storage. */
public final class RaceModule implements AutoCloseable {
    private final JavaPlugin host;
    private Settings settings;
    private PlayerStore store;
    private Effects effects;
    private RaceRuntime runtime;
    private CombatListener combat;
    private RaceRoll rolls;
    private BukkitTask ticker;
    private boolean enabled;
    public RaceModule(JavaPlugin host) { this.host = host; }
    public boolean enable() {
        try {
            migrate();
            settings = Settings.load(this);
            store = new PlayerStore(getDataFolder().toPath().resolve("race-players.yml"));
            effects = new Effects(this); runtime = new RaceRuntime(this); combat = new CombatListener(this);
            rolls = new RaceRoll(this);
            var manager = host.getServer().getPluginManager();
            manager.registerEvents(effects, host); manager.registerEvents(runtime, host);
            manager.registerEvents(combat, host); manager.registerEvents(rolls, host);
            RaceCommand command = new RaceCommand(this);
            var race = Objects.requireNonNull(host.getCommand("race"));
            race.setExecutor(command); race.setTabCompleter(command);
            var roll = Objects.requireNonNull(host.getCommand("roll"));
            roll.setExecutor(command); roll.setTabCompleter(command);
            enabled = true;
            for (Player player : Bukkit.getOnlinePlayers()) {
                effects.recover(player); runtime.endDragon(player, false);
                store.remember(player.getName(), player.getUniqueId()); rolls.queueAssignment(player);
            }
            ticker = Bukkit.getScheduler().runTaskTimer(host, () -> { combat.prune(); runtime.tick(); rolls.tick(); }, 1, 1);
            getLogger().info("Conquest races enabled for Paper 1.21.11; first-join rolls and cooldowns are saved by UUID.");
            return true;
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot enable races. Disabling Conquest to protect saved data.", ex);
            host.getServer().getPluginManager().disablePlugin(host); return false;
        }
    }
    private void migrate() throws IOException {
        Path folder = getDataFolder().toPath(); Files.createDirectories(folder);
        Path legacy = folder.resolveSibling("BiomeRaces");
        copyIfAbsent(legacy.resolve("players.yml"), folder.resolve("race-players.yml"));
        copyIfAbsent(legacy.resolve("config.yml"), folder.resolve("races.yml"));
        if (!Files.exists(folder.resolve("races.yml"))) host.saveResource("races.yml", false);
    }
    static void copyIfAbsent(Path source, Path target) throws IOException {
        if (Files.exists(target) || !Files.isRegularFile(source)) return;
        Path staged = target.resolveSibling(target.getFileName() + ".migration");
        Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(staged, target); }
    }
    @Override public void close() {
        enabled = false;
        if (ticker != null) ticker.cancel();
        if (rolls != null) { rolls.close(); HandlerList.unregisterAll(rolls); }
        if (runtime != null) { runtime.shutdown(); HandlerList.unregisterAll(runtime); }
        if (combat != null) HandlerList.unregisterAll(combat);
        if (effects != null) { effects.close(); HandlerList.unregisterAll(effects); }
        if (store != null) persist();
    }
    public void reloadSettings() throws Exception {
        Settings next = Settings.load(this);
        rolls.close(); runtime.shutdown(); effects.close(); settings = next;
        for (Player player : Bukkit.getOnlinePlayers()) { runtime.updatePassives(player); rolls.queueAssignment(player); }
    }
    public boolean persist() {
        try { store.save(); return true; }
        catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not save race choices/cooldowns. Check disk permissions and free space.", ex);
            return false;
        }
    }
    public void message(CommandSender sender, String key, String... replacements) {
        String text = settings.message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(MiniMessage.miniMessage().deserialize(text));
    }
    public void visual(Race race, Location location, Player privateViewer) {
        Settings.Visual visual = settings.visual(race);
        for (Particle particle : visual.particles()) {
            Object data = particle.getDataType() == org.bukkit.block.data.BlockData.class ? visual.block().createBlockData() : null;
            if (privateViewer != null) privateViewer.spawnParticle(particle, location, visual.count(), .25, .2, .25, .01, data);
            else location.getWorld().spawnParticle(particle, location, visual.count(), .25, .2, .25, .01, data);
        }
        if (visual.volume() > 0 && !visual.sound().isBlank()) {
            if (privateViewer != null) privateViewer.playSound(location, visual.sound(), SoundCategory.PLAYERS, visual.volume(), visual.pitch());
            else location.getWorld().playSound(location, visual.sound(), SoundCategory.PLAYERS, visual.volume(), visual.pitch());
        }
    }
    public boolean paidReroll(Player player,long price) {
        if(!enabled||!player.isOnline()||player.isDead()||rolls.running(player.getUniqueId())||state(player).base==null||RaceRuntime.holdsEgg(player)||state(player).dragon){player.sendMessage("Finish your current roll or transformation before rerolling.");return false;}
        if(host instanceof dev.turtleroles.TurtleRolesPlugin conquest&&conquest.combat().tagged(player)){player.sendMessage("You cannot reroll during combat.");return false;}
        try {
            Race result=new RollSettings(settings.yaml).choose(java.util.random.RandomGenerator.getDefault());
            if(!store.purchaseReroll(player.getUniqueId(),result,price)){player.sendMessage("You need "+price+" shards to reroll.");return false;}
        }catch(IOException ex){getLogger().log(java.util.logging.Level.SEVERE,"Could not save paid reroll",ex);player.sendMessage("Purchase could not be saved. No shards were spent.");return false;}
        state(player).chain.reset();effects.clearSelf(player);runtime.updatePassives(player);
        if(!rolls.start(player,false))rolls.queueAssignment(player);
        return true;
    }
    public JavaPlugin host() { return host; }
    public boolean isEnabled() { return enabled; }
    public File getDataFolder() { return host.getDataFolder(); }
    public InputStream getResource(String name) { return host.getResource(name); }
    public Logger getLogger() { return host.getLogger(); }
    public Settings settings() { return settings; }
    public PlayerStore store() { return store; }
    public PlayerState state(Player player) { return store.get(player.getUniqueId()); }
    public Effects effects() { return effects; }
    public RaceRuntime runtime() { return runtime; }
    public CombatListener combat() { return combat; }
    public RaceRoll rolls() { return rolls; }
}

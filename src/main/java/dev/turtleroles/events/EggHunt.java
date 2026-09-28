package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Outdoor, non-block egg displays with persistent crash cleanup. */
public final class EggHunt implements Listener {
    private record Egg(UUID hitbox, UUID display, String world, int x, int y, int z, boolean rare) {}
    private final JavaPlugin plugin;
    private final YamlConfiguration settings;
    private final NamespacedKey marker;
    private final File recoveryFile;
    private final YamlConfiguration recovery;
    private final Map<UUID, Egg> eggs = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private final Map<UUID, Objective> objectives = new HashMap<>();
    private final List<String> recoveryEntries = new ArrayList<>();
    private final EggSpawnLedger ledger = new EggSpawnLedger();
    private final Set<String> pendingChunks = new LinkedHashSet<>();
    private final Random random = new Random();
    private BukkitTask spawnTask;
    private BukkitTask runningTask;
    private UUID eventId;
    private final List<World> worlds = new ArrayList<>();
    private int minimumSpacing, initialEggs, placementSerial, playerCursor;
    private long preparationDeadline;
    private long endsAt;
    private Duration duration;
    private Runnable onReady = () -> {};
    private Runnable onFinish = () -> {};

    public EggHunt(JavaPlugin plugin, YamlConfiguration settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.marker = new NamespacedKey("shocksmp", "egg_hunt_display");
        this.recoveryFile = new File(plugin.getDataFolder(), "egg-hunt-recovery.yml");
        this.recovery = YamlConfiguration.loadConfiguration(recoveryFile);
        recoveryEntries.addAll(recovery.getStringList("entities"));
        Bukkit.getScheduler().runTask(plugin, this::cleanLoadedRecovery);
    }

    public boolean preparingOrRunning() { return eventId != null; }

    public void prepare(Player admin, Duration requested, Runnable ready, Runnable finish) {
        if (eventId != null) throw new IllegalStateException("Egg Hunt is already active");
        worlds.clear();
        List<String> configured = settings.getStringList("egg-hunt.worlds");
        if (configured.isEmpty()) configured = List.of(settings.getString("egg-hunt.world", "world"));
        for (String name : configured) {
            World candidate = Bukkit.getWorld(name);
            if (candidate != null && !worlds.contains(candidate)) worlds.add(candidate);
        }
        if (worlds.isEmpty()) {
            admin.sendMessage(Component.text("Egg Hunt needs at least one loaded configured world. Check events.yml.", NamedTextColor.RED));
            finish.run(); return;
        }
        minimumSpacing = Math.max(2, settings.getInt("egg-hunt.minimum-spacing", 8));
        initialEggs = Math.max(1, Math.min(16, settings.getInt("egg-hunt.initial-eggs", 4)));
        this.duration = requested;
        this.onReady = ready;
        this.onFinish = finish;
        eventId = UUID.randomUUID();
        placementSerial = 0;
        ledger.clear();
        pendingChunks.clear();
        preparationDeadline = System.currentTimeMillis() + Math.max(5,
                settings.getLong("egg-hunt.preparation-timeout-seconds", 30)) * 1000;
        admin.sendMessage(Component.text("Preparing Egg Hunt around exploring survival/adventure players in "
                + worlds.stream().map(World::getName).toList() + ". Only already-loaded outdoor terrain is used."
                + " Initial target: " + initialEggs + " eggs; active limit: " + globalLimit() + ".", NamedTextColor.YELLOW));
        int interval = Math.max(1, Math.min(100, settings.getInt("egg-hunt.placement-interval-ticks", 20)));
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> spawnTick(admin), 1, interval);
    }

    private void spawnTick(Player admin) {
        if (eventId == null) return;
        List<? extends Player> eligible = Bukkit.getOnlinePlayers().stream().filter(this::eligible).toList();
        if (runningTask != null && eggs.size() >= globalLimit()
                && settings.getBoolean("egg-hunt.retire-distant-when-full", true)) retireDistant(eligible);
        int attempts = Math.max(1, Math.min(64, settings.getInt("egg-hunt.scan-attempts-per-cycle", 12)));
        int placements = Math.max(1, Math.min(8, settings.getInt("egg-hunt.max-placements-per-cycle", 2)));
        for (int i = 0, placed = 0; i < attempts && placed < placements && eggs.size() < globalLimit()
                && !eligible.isEmpty(); i++) {
            Player player = eligible.get(Math.floorMod(playerCursor++, eligible.size()));
            if (areaCount(player) >= areaLimit()) continue;
            Location candidate = candidate(player);
            if (candidate != null && placeAt(candidate)) placed++;
        }
        if (runningTask == null) {
            if (eggs.size() >= initialEggs) startRunning();
            else if (System.currentTimeMillis() >= preparationDeadline) {
                if (!eggs.isEmpty()) {
                    admin.sendMessage(Component.text("Starting with " + eggs.size() + "/" + initialEggs
                            + " initial eggs; more can appear as players explore.", NamedTextColor.YELLOW));
                    startRunning();
                } else {
                    admin.sendMessage(Component.text("No safe loaded outdoor locations near eligible players. Egg Hunt was cancelled.", NamedTextColor.RED));
                    stop(false);
                }
            }
        }
    }

    private boolean eligible(Player player) {
        return player.isOnline() && (player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE) && worlds.contains(player.getWorld());
    }

    private int globalLimit() { return Math.max(1, Math.min(256, settings.getInt("egg-hunt.global-active-limit", 48))); }
    private int areaLimit() { return Math.max(1, Math.min(globalLimit(), settings.getInt("egg-hunt.per-player-area-limit", 8))); }

    private int areaCount(Player player) {
        int radius = Math.max(16, settings.getInt("egg-hunt.spawn-max-distance", 48));
        return ledger.near(player.getWorld().getName(), player.getLocation().getBlockX(),
                player.getLocation().getBlockZ(), radius);
    }

    private Location candidate(Player player) {
        World world = player.getWorld();
        int max = Math.max(16, settings.getInt("egg-hunt.spawn-max-distance", 48));
        int min = Math.max(8, Math.min(max - 1, settings.getInt("egg-hunt.spawn-min-distance", 18)));
        if (!pendingChunks.isEmpty() && random.nextBoolean()) {
            String nearby = pendingChunks.stream().filter(key -> key.startsWith(world.getName() + ":"))
                    .filter(key -> {
                        String[] parts = key.substring(world.getName().length() + 1).split(":");
                        int cx = Integer.parseInt(parts[0]) * 16 + 8;
                        int cz = Integer.parseInt(parts[1]) * 16 + 8;
                        int dx = cx - player.getLocation().getBlockX(), dz = cz - player.getLocation().getBlockZ();
                        return dx * dx + dz * dz <= (max + 16) * (max + 16);
                    }).findFirst().orElse(null);
            if (nearby != null) {
                pendingChunks.remove(nearby);
                String[] parts = nearby.substring(world.getName().length() + 1).split(":");
                int x = Integer.parseInt(parts[0]) * 16 + random.nextInt(16);
                int z = Integer.parseInt(parts[1]) * 16 + random.nextInt(16);
                return new Location(world, x, 0, z);
            }
        }
        double heading = Math.atan2(player.getLocation().getDirection().getZ(), player.getLocation().getDirection().getX());
        double angle = heading + (random.nextDouble() * 2 - 1) * Math.toRadians(110);
        int distance = min + random.nextInt(max - min + 1);
        return new Location(world, player.getLocation().getBlockX() + Math.round(Math.cos(angle) * distance),
                0, player.getLocation().getBlockZ() + Math.round(Math.sin(angle) * distance));
    }

    private boolean placeAt(Location candidate) {
        World world = candidate.getWorld();
        int x = candidate.getBlockX(), z = candidate.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        // A 3x3 ground check can cross a chunk boundary. Never load its neighbor.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
        EggSpawnLedger.Spot spot = new EggSpawnLedger.Spot(world.getName(), x, z);
        if (!ledger.canPlace(spot, globalLimit(), Math.max(1,
                settings.getInt("egg-hunt.max-spawns-per-chunk", 4)), minimumSpacing)) return false;
        WorldBorder border = world.getWorldBorder();
        if (!border.isInside(new Location(world, x + .5, world.getSeaLevel(), z + .5))) return false;
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) return false;
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!safeGround(ground.getType()) || world.getBlockAt(x, y, z).getType() != Material.AIR
                || world.getBlockAt(x, y + 1, z).getType() != Material.AIR
                || world.getBlockAt(x, y, z).getLightFromSky() < 14) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (!safeGround(world.getBlockAt(x + dx, y - 1, z + dz).getType())) return false;
        int minDistance = Math.max(8, settings.getInt("egg-hunt.spawn-min-distance", 18));
        for (Player player : Bukkit.getOnlinePlayers()) if (eligible(player) && player.getWorld().equals(world)) {
            int dx = player.getLocation().getBlockX() - x, dz = player.getLocation().getBlockZ() - z;
            if (dx * dx + dz * dz < minDistance * minDistance) return false;
        }
        Egg egg = spawnEgg(world, x, y, z);
        ledger.placed(egg.hitbox(), spot);
        saveRecovery();
        return true;
    }

    private String chunkKey(String world, int x, int z) {
        return EggSpawnLedger.chunk(new EggSpawnLedger.Spot(world, x, z));
    }

    private void retireDistant(Collection<? extends Player> players) {
        int distance = Math.max(32, settings.getInt("egg-hunt.retire-distance", 112));
        int budget = Math.max(1, Math.min(8, settings.getInt("egg-hunt.max-retire-per-cycle", 2)));
        for (Egg egg : new ArrayList<>(eggs.values())) {
            if (budget <= 0 || eggs.size() < globalLimit()) break;
            boolean near = false;
            for (Player player : players) if (player.getWorld().getName().equals(egg.world())) {
                int dx = egg.x() - player.getLocation().getBlockX();
                int dz = egg.z() - player.getLocation().getBlockZ();
                if (dx * dx + dz * dz <= distance * distance) { near = true; break; }
            }
            if (!near) { retire(egg); budget--; }
        }
    }

    private void retire(Egg egg) {
        eggs.remove(egg.hitbox());
        ledger.retired(egg.hitbox());
        World world = Bukkit.getWorld(egg.world());
        if (world != null && world.isChunkLoaded(egg.x() >> 4, egg.z() >> 4)) {
            removeEntity(egg.hitbox()); removeEntity(egg.display());
        }
        // The persistent entity tag handles unloaded chunks on their next load.
        // Retired eggs must not make the recovery file grow with exploration.
        recoveryEntries.remove(encode(egg));
        saveRecovery();
    }

    private boolean safeGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, MYCELIUM, MOSS_BLOCK, SAND, RED_SAND,
                    GRAVEL, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private Egg spawnEgg(World world, int x, int y, int z) {
        boolean rare = EventRules.rare(placementSerial++, settings.getInt("egg-hunt.rare-one-in", 8), eventId);
        BlockDisplay display = world.spawn(new Location(world, x, y, z), BlockDisplay.class, entity -> {
            entity.setBlock(Material.DRAGON_EGG.createBlockData());
            entity.getPersistentDataContainer().set(marker, PersistentDataType.STRING, eventId.toString());
        });
        Interaction hitbox = world.spawn(new Location(world, x + .5, y, z + .5), Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.0f);
            entity.getPersistentDataContainer().set(marker, PersistentDataType.STRING, eventId.toString());
        });
        Egg egg = new Egg(hitbox.getUniqueId(), display.getUniqueId(), world.getName(), x, y, z, rare);
        eggs.put(hitbox.getUniqueId(), egg);
        recoveryEntries.add(encode(egg));
        return egg;
    }

    private void startRunning() {
        endsAt = System.currentTimeMillis() + duration.toMillis();
        for (Player player : Bukkit.getOnlinePlayers()) attachBoard(player);
        Bukkit.broadcast(Component.text("Egg Hunt has begun! Click the dragon eggs to score.", NamedTextColor.GOLD));
        onReady.run();
        runningTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runningTick, 0, 20);
    }

    private void runningTick() {
        if (System.currentTimeMillis() >= endsAt) { stop(true); return; }
        for (Egg egg : eggs.values()) {
            World world = Bukkit.getWorld(egg.world());
            if (!egg.rare() || world == null || !world.isChunkLoaded(egg.x() >> 4, egg.z() >> 4)) continue;
            for (int n = 0; n < 8; n++) {
                double angle = n * Math.PI / 4;
                world.spawnParticle(Particle.DUST, egg.x() + .5 + Math.cos(angle) * .7,
                        egg.y() + .3, egg.z() + .5 + Math.sin(angle) * .7,
                        1, new Particle.DustOptions(Color.fromRGB(160, 45, 220), 1.2f));
            }
        }
        updateBoards();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!eggs.containsKey(event.getRightClicked().getUniqueId())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) collect(event.getPlayer(), event.getRightClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!eggs.containsKey(event.getEntity().getUniqueId())) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) collect(player, event.getEntity().getUniqueId());
    }

    private void collect(Player player, UUID id) {
        if (runningTask == null) return;
        Egg egg = eggs.remove(id);
        if (egg == null) return;
        ledger.collected(id);
        removeEntity(egg.hitbox());
        removeEntity(egg.display());
        recoveryEntries.remove(encode(egg));
        saveRecovery();
        int points = egg.rare() ? settings.getInt("egg-hunt.rare-points", 20) : settings.getInt("egg-hunt.normal-points", 5);
        int score = scores.merge(player.getUniqueId(), points, Integer::sum);
        player.sendMessage(Component.text((egg.rare() ? "Rare egg! +" : "Egg collected! +") + points
                + " points. Your score: " + score, egg.rare() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN));
        updateBoards();
    }

    private void attachBoard(Player player) {
        if (previousBoards.containsKey(player.getUniqueId())) return;
        Scoreboard original = player.getScoreboard();
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        syncTeams(original, board);
        Objective objective = board.registerNewObjective("shock_eggs", Criteria.DUMMY,
                Component.text("EGG HUNT", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        previousBoards.put(player.getUniqueId(), original);
        objectives.put(player.getUniqueId(), objective);
        player.setScoreboard(board);
        updateBoard(player, objective);
    }

    private void syncTeams(Scoreboard from, Scoreboard to) {
        for (Team existing : new ArrayList<>(to.getTeams()))
            if (from.getTeam(existing.getName()) == null) existing.unregister();
        for (Team source : from.getTeams()) {
            Team copy = to.getTeam(source.getName());
            if (copy == null) copy = to.registerNewTeam(source.getName());
            copy.prefix(source.prefix()); copy.suffix(source.suffix());
            if (source.color() != null) copy.color(NamedTextColor.nearestTo(source.color()));
            copy.setAllowFriendlyFire(source.allowFriendlyFire());
            copy.setCanSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
            for (String entry : new HashSet<>(copy.getEntries()))
                if (!source.hasEntry(entry)) copy.removeEntry(entry);
            for (String entry : source.getEntries()) copy.addEntry(entry);
        }
    }

    private void updateBoards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Objective objective = objectives.get(player.getUniqueId());
            if (objective == null) attachBoard(player);
            else updateBoard(player, objective);
        }
    }

    private void updateBoard(Player player, Objective objective) {
        if (objective.getScoreboard() == null) return;
        Scoreboard board = objective.getScoreboard();
        Scoreboard original = previousBoards.get(player.getUniqueId());
        if (original != null) syncTeams(original, board);
        for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);
        long seconds = Math.max(0, (endsAt - System.currentTimeMillis() + 999) / 1000);
        objective.getScore("§eTime: §f" + seconds + "s").setScore(15);
        objective.getScore("§aYour score: §f" + scores.getOrDefault(player.getUniqueId(), 0)).setScore(14);
        List<Map.Entry<UUID, Integer>> leaders = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder())).limit(10).toList();
        int line = 12;
        for (Map.Entry<UUID, Integer> leader : leaders) {
            String name = Bukkit.getOfflinePlayer(leader.getKey()).getName();
            objective.getScore("§6" + (name == null ? leader.getKey().toString().substring(0, 8) : name)
                    + " §f" + leader.getValue()).setScore(line--);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (runningTask != null) Bukkit.getScheduler().runTask(plugin, () -> {
            previousBoards.remove(event.getPlayer().getUniqueId());
            objectives.remove(event.getPlayer().getUniqueId());
            attachBoard(event.getPlayer());
        });
    }

    public void stop(boolean announceWinner) {
        if (eventId == null) return;
        eventId = null;
        if (spawnTask != null) { spawnTask.cancel(); spawnTask = null; }
        if (runningTask != null) { runningTask.cancel(); runningTask = null; }
        if (announceWinner) {
            int high = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            List<String> winners = scores.entrySet().stream().filter(e -> e.getValue() == high)
                    .map(e -> Bukkit.getOfflinePlayer(e.getKey()).getName()).map(n -> n == null ? "Unknown" : n).toList();
            String message = winners.isEmpty() ? "Egg Hunt ended with no collectors."
                    : winners.size() == 1 ? "Egg Hunt winner: " + winners.getFirst() + " with " + high + " points!"
                    : "Egg Hunt tie: " + String.join(", ", winners) + " with " + high + " points each!";
            Bukkit.broadcast(Component.text(message, NamedTextColor.GOLD));
        } else Bukkit.broadcast(Component.text("Egg Hunt was cancelled.", NamedTextColor.YELLOW));
        for (Egg egg : eggs.values()) {
            World world = Bukkit.getWorld(egg.world());
            if (world != null && world.isChunkLoaded(egg.x() >> 4, egg.z() >> 4)) {
                removeEntity(egg.hitbox()); removeEntity(egg.display());
            }
            recoveryEntries.remove(encode(egg));
        }
        saveRecovery();
        for (Map.Entry<UUID, Scoreboard> entry : previousBoards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) player.setScoreboard(entry.getValue());
        }
        previousBoards.clear(); objectives.clear(); eggs.clear(); scores.clear();
        ledger.clear(); pendingChunks.clear(); worlds.clear();
        saveRecovery();
        onFinish.run();
    }

    private void removeEntity(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    private String encode(Egg egg) {
        return egg.world() + "," + egg.x() + "," + egg.y() + "," + egg.z() + "," + egg.hitbox() + "," + egg.display();
    }

    private void saveRecovery() {
        recovery.set("entities", new ArrayList<>(recoveryEntries));
        recovery.set("active-event", eventId == null ? null : eventId.toString());
        // An interrupted event is not resumed after restart. Its PDC-tagged
        // entities are cleaned on chunk/entity load, so the per-event farm
        // prevention counters only need to live in memory while it runs.
        recovery.set("placed-per-chunk", null);
        recovery.set("collected-per-chunk", null);
        try { recovery.save(recoveryFile); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save Egg Hunt recovery data: " + ex.getMessage()); }
    }

    private void cleanLoadedRecovery() {
        for (String line : new ArrayList<>(recoveryEntries)) {
            String[] parts = line.split(",");
            if (parts.length != 6) continue;
            try { if (eggs.containsKey(UUID.fromString(parts[4]))) continue; }
            catch (IllegalArgumentException ignored) { recoveryEntries.remove(line); continue; }
            World target = Bukkit.getWorld(parts[0]);
            if (target == null) continue;
            try {
                int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[3]);
                if (!target.isChunkLoaded(x >> 4, z >> 4)) continue;
                removeEntity(UUID.fromString(parts[4])); removeEntity(UUID.fromString(parts[5]));
                recoveryEntries.remove(line);
            } catch (IllegalArgumentException ignored) { recoveryEntries.remove(line); }
        }
        List<org.bukkit.Chunk> loaded = new ArrayList<>();
        for (World target : Bukkit.getWorlds()) loaded.addAll(List.of(target.getLoadedChunks()));
        if (!loaded.isEmpty()) {
            final int[] cursor = {0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (int n = 0; n < 4 && cursor[0] < loaded.size(); n++) cleanChunk(loaded.get(cursor[0]++));
                if (cursor[0] >= loaded.size()) task[0].cancel();
            }, 1, 1);
        }
        saveRecovery();
    }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        cleanChunk(event.getChunk());
        if (eventId != null && worlds.contains(event.getWorld())) {
            if (pendingChunks.size() >= 512) pendingChunks.remove(pendingChunks.iterator().next());
            pendingChunks.add(chunkKey(event.getWorld().getName(), event.getChunk().getX() << 4,
                    event.getChunk().getZ() << 4));
        }
    }

    private void cleanChunk(org.bukkit.Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        cleanEntities(List.of(chunk.getEntities()));
        boolean changed = recoveryEntries.removeIf(line -> {
            String[] parts = line.split(",");
            if (parts.length != 6 || !parts[0].equals(worldName)) return false;
            try {
                int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[3]);
                return (x >> 4) == chunk.getX() && (z >> 4) == chunk.getZ()
                        && !eggs.containsKey(UUID.fromString(parts[4]));
            } catch (IllegalArgumentException ignored) { return true; }
        });
        if (changed) saveRecovery();
    }

    @EventHandler public void onEntitiesLoad(EntitiesLoadEvent event) {
        cleanEntities(event.getEntities());
    }

    private void cleanEntities(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            String tagged = entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
            if (tagged != null && (eventId == null || !tagged.equals(eventId.toString())
                    || !eggs.containsKey(entity.getUniqueId()) && eggs.values().stream().noneMatch(egg -> egg.display().equals(entity.getUniqueId()))))
                entity.remove();
        }
    }
}

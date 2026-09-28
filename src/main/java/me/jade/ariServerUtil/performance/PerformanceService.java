package me.jade.ariServerUtil.performance;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PerformanceService {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final AuditService audit;
    private volatile Metrics metrics = new Metrics(List.of(), "unknown");
    private final AtomicBoolean cleanupRunning = new AtomicBoolean();

    public PerformanceService(JavaPlugin plugin, ServerUtilConfig config, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.audit = audit;
        Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L, Math.max(20L, config.integer("performance.sample-interval-seconds", 30, 5, 600) * 20L));
    }

    public Metrics metrics() {
        return metrics;
    }

    public void sample() {
        List<String> lines = new ArrayList<>();
        double[] tps = Bukkit.getTPS();
        lines.add("TPS: " + format(tps[0]) + ", " + format(tps[1]) + ", " + format(tps[2]));
        lines.add("MSPT: " + averageTickTime());
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long max = runtime.maxMemory() / 1024 / 1024;
        lines.add("Memory: " + used + " MB / " + max + " MB");
        lines.add("Online players: " + Bukkit.getOnlinePlayers().size());
        int loadedChunks = Bukkit.getWorlds().stream().mapToInt(world -> world.getLoadedChunks().length).sum();
        lines.add("Loaded chunks: " + loadedChunks);
        for (World world : Bukkit.getWorlds()) {
            long entities = world.getEntities().size();
            long dropped = world.getEntitiesByClass(Item.class).size();
            lines.add(world.getName() + ": entities " + entities + ", dropped items " + dropped);
        }
        metrics = new Metrics(lines, "ok");
    }

    public int previewCleanup() {
        return disposableEntities().size();
    }

    public void cleanup(org.bukkit.entity.Player staff) {
        if (!config.bool("performance.cleanup.enabled", false)) {
            Text.send(staff, "<red>Entity cleanup is disabled in config.");
            return;
        }
        if (!cleanupRunning.compareAndSet(false, true)) {
            Text.send(staff, "<red>A cleanup is already running.");
            return;
        }
        List<Entity> entities = disposableEntities();
        Iterator<Entity> iterator = entities.iterator();
        int batch = config.integer("performance.cleanup.batch-size", 100, 1, 1000);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int removed = 0;
            while (iterator.hasNext() && removed < batch) {
                Entity entity = iterator.next();
                if (entity.isValid()) {
                    entity.remove();
                    removed++;
                }
            }
            if (!iterator.hasNext()) {
                cleanupRunning.set(false);
                audit.record(staff, "PERFORMANCE_CLEANUP", null, "", "disposable entities", "removed " + entities.size(), "");
                task.cancel();
            }
        }, 1L, 1L);
    }

    private List<Entity> disposableEntities() {
        List<Entity> entities = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (protectedEntity(entity)) {
                    continue;
                }
                if (entity instanceof Item || entity instanceof Arrow || entity instanceof ExperienceOrb) {
                    entities.add(entity);
                }
            }
        }
        return entities;
    }

    private boolean protectedEntity(Entity entity) {
        if (entity instanceof Player || entity instanceof Villager || entity instanceof Tameable tameable && tameable.isTamed() || entity instanceof ArmorStand || entity instanceof Boss) {
            return true;
        }
        if (entity.customName() != null || !entity.getPersistentDataContainer().isEmpty()) {
            return true;
        }
        return config.list("performance.cleanup.protected-types").contains(entity.getType().name());
    }

    private String averageTickTime() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getAverageTickTime");
            return format(((Number) method.invoke(Bukkit.getServer())).doubleValue());
        } catch (ReflectiveOperationException ignored) {
            return "unavailable";
        }
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public record Metrics(List<String> lines, String databaseStatus) {
    }
}

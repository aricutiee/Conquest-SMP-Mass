package me.jade.ariServerUtil.restart;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.DurationParser;
import me.jade.ariServerUtil.util.RestartMilestones;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RestartService {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private Instant deadline;
    private String scheduler = "";
    private Duration previousRemaining = Duration.ofDays(999);

    public RestartService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean active() {
        return deadline != null && deadline.isAfter(Instant.now());
    }

    public Duration remaining() {
        return active() ? Duration.between(Instant.now(), deadline) : Duration.ZERO;
    }

    public String scheduler() {
        return scheduler;
    }

    public void schedule(Player staff, Duration duration, boolean replace) {
        if (active() && !replace) {
            Text.send(staff, "<red>A restart countdown is already running.");
            return;
        }
        deadline = Instant.now().plus(duration);
        previousRemaining = duration.plusSeconds(1);
        scheduler = staff.getName();
        save();
        announce(config.message("restart.scheduled").replace("<duration>", DurationParser.human(duration)).replace("<staff>", staff.getName()), duration);
        audit.record(staff, "RESTART_SCHEDULE", null, "", DurationParser.human(duration), "success", "");
    }

    public void cancel(Player staff) {
        deadline = null;
        save();
        announce(config.message("restart.cancelled").replace("<staff>", staff.getName()), Duration.ZERO);
        audit.record(staff, "RESTART_CANCEL", null, "", "", "success", "");
    }

    private void tick() {
        if (!active()) {
            return;
        }
        Duration now = remaining();
        for (Duration milestone : RestartMilestones.dueMilestones(previousRemaining, now, milestones())) {
            announce(config.message("restart.milestone").replace("<remaining>", DurationParser.human(milestone)), milestone);
        }
        previousRemaining = now;
        if (now.isZero() || now.isNegative()) {
            audit.record(Bukkit.getConsoleSender(), "RESTART_EXECUTE", null, "", "", "success", "");
            String command = config.string("restart.command", "restart");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private List<Duration> milestones() {
        List<Duration> durations = new ArrayList<>();
        for (String value : config.list("restart.milestones")) {
            try {
                durations.add(DurationParser.parse(value));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid restart milestone " + value);
            }
        }
        return durations;
    }

    private void announce(String message, Duration remaining) {
        Bukkit.broadcast(Text.mm(message));
        Sound sound = config.sound("restart.sound", Sound.BLOCK_NOTE_BLOCK_PLING);
        Bukkit.getOnlinePlayers().forEach(player -> {
            dev.turtleroles.events.ActionBarBus.offer(player, "restart", Text.mm(message), 100, 1500);
            player.playSound(player.getLocation(), sound, 1f, 1f);
        });
    }

    private void save() {
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO state(key,value) VALUES('restart_deadline',?),('restart_scheduler',?)")) {
                ps.setString(1, deadline == null ? "0" : Long.toString(deadline.toEpochMilli()));
                ps.setString(2, scheduler == null ? "" : scheduler);
                ps.executeUpdate();
            }
        });
    }

    private void load() {
        try {
            List<String> state = database.query(conn -> {
                String deadlineValue = "0";
                String schedulerValue = "";
                try (var ps = conn.prepareStatement("SELECT key,value FROM state WHERE key IN('restart_deadline','restart_scheduler')")) {
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (rs.getString(1).equals("restart_deadline")) {
                                deadlineValue = rs.getString(2);
                            } else {
                                schedulerValue = rs.getString(2);
                            }
                        }
                    }
                }
                return List.of(deadlineValue, schedulerValue);
            }).join();
            long millis = Long.parseLong(state.get(0));
            deadline = millis > System.currentTimeMillis() ? Instant.ofEpochMilli(millis) : null;
            scheduler = state.get(1);
            previousRemaining = remaining().plusSeconds(1);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not load restart countdown: " + ex.getMessage());
        }
    }
}

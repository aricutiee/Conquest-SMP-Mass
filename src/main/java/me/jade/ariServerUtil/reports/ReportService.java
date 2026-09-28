package me.jade.ariServerUtil.reports;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.model.ReportRecord;
import me.jade.ariServerUtil.model.ReportStatus;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.ReportTransitions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ReportService {
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ReportService(ServerUtilConfig config, Database database, AuditService audit) {
        this.config = config;
        this.database = database;
        this.audit = audit;
    }

    public void submit(Player reporter, Database.PlayerIdentity target, String reason) {
        if (reporter.getUniqueId().equals(target.uuid())) {
            Text.send(reporter, config.message("reports.no-self"));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = config.duration("reports.cooldown", "60sec").toMillis();
        long last = cooldowns.getOrDefault(reporter.getUniqueId(), 0L);
        if (now - last < cooldownMillis) {
            Text.send(reporter, config.message("reports.cooldown").replace("<remaining>", Long.toString((cooldownMillis - (now - last)) / 1000)));
            return;
        }
        cooldowns.put(reporter.getUniqueId(), now);
        database.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO reports(reporter_uuid,reporter_name,reported_uuid,reported_name,reason,created_at,status) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, reporter.getUniqueId().toString());
                ps.setString(2, reporter.getName());
                ps.setString(3, target.uuid().toString());
                ps.setString(4, target.name());
                ps.setString(5, reason);
                ps.setLong(6, Instant.now().toEpochMilli());
                ps.setString(7, ReportStatus.OPEN.name());
                ps.executeUpdate();
            }
        });
        Text.send(reporter, config.message("reports.submitted"));
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(Permissions.REPORTS))
                .forEach(player -> Text.send(player, config.message("reports.staff-notify").replace("<reporter>", reporter.getName()).replace("<player>", target.name()).replace("<reason>", reason)));
        audit.record(reporter, "REPORT_SUBMIT", target.uuid(), target.name(), reason, "success", "");
    }

    public CompletableFuture<List<ReportRecord>> reports(ReportStatus filter) {
        return database.query(conn -> {
            List<ReportRecord> records = new ArrayList<>();
            String sql = filter == null ? "SELECT * FROM reports ORDER BY created_at DESC LIMIT 500" : "SELECT * FROM reports WHERE status=? ORDER BY created_at DESC LIMIT 500";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (filter != null) {
                    ps.setString(1, filter.name());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(readReport(rs));
                    }
                }
            }
            return records;
        });
    }

    public void transition(CommandSender staff, long id, ReportStatus to, String result) {
        database.execute(conn -> {
            ReportStatus from;
            try (PreparedStatement find = conn.prepareStatement("SELECT status FROM reports WHERE id=?")) {
                find.setLong(1, id);
                try (ResultSet rs = find.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                    from = ReportStatus.valueOf(rs.getString(1));
                }
            }
            if (!ReportTransitions.canTransition(from, to)) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE reports SET status=?, assigned_staff_uuid=?, resolution_time=?, resolution_result=? WHERE id=?")) {
                ps.setString(1, to.name());
                ps.setString(2, staff instanceof Player player ? player.getUniqueId().toString() : "");
                if (to == ReportStatus.RESOLVED || to == ReportStatus.REJECTED) {
                    ps.setLong(3, Instant.now().toEpochMilli());
                } else {
                    ps.setObject(3, null);
                }
                ps.setString(4, result == null ? "" : result);
                ps.setLong(5, id);
                ps.executeUpdate();
            }
        });
        audit.record(staff, "REPORT_" + to.name(), null, "", result, "success", Long.toString(id));
    }

    public void addNote(Player staff, UUID targetUuid, String targetName, String note) {
        database.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO staff_notes(target_uuid,target_name,staff_uuid,staff_name,note,created_at) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, targetName);
                ps.setString(3, staff.getUniqueId().toString());
                ps.setString(4, staff.getName());
                ps.setString(5, note);
                ps.setLong(6, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
        });
        audit.record(staff, "STAFF_NOTE", targetUuid, targetName, "private note", "success", "");
    }

    private ReportRecord readReport(ResultSet rs) throws Exception {
        String assigned = rs.getString("assigned_staff_uuid");
        long resolution = rs.getLong("resolution_time");
        return new ReportRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("reporter_uuid")),
                rs.getString("reporter_name"),
                UUID.fromString(rs.getString("reported_uuid")),
                rs.getString("reported_name"),
                rs.getString("reason"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                ReportStatus.valueOf(rs.getString("status")),
                assigned == null || assigned.isBlank() ? null : UUID.fromString(assigned),
                rs.getString("staff_notes"),
                rs.wasNull() ? null : Instant.ofEpochMilli(resolution),
                rs.getString("resolution_result")
        );
    }
}

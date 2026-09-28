package me.jade.ariServerUtil.gui;

import me.jade.ariServerUtil.announcements.AnnouncementService;
import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.chat.ChatControlService;
import me.jade.ariServerUtil.chatinput.ChatInputService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.freeze.FreezeService;
import me.jade.ariServerUtil.grace.GracePeriodService;
import me.jade.ariServerUtil.lockdown.LockdownService;
import me.jade.ariServerUtil.model.PunishmentRecord;
import me.jade.ariServerUtil.model.PunishmentType;
import me.jade.ariServerUtil.model.ReportRecord;
import me.jade.ariServerUtil.model.ReportStatus;
import me.jade.ariServerUtil.performance.PerformanceService;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.player.IdentityService;
import me.jade.ariServerUtil.punishments.PunishmentService;
import me.jade.ariServerUtil.reports.ReportService;
import me.jade.ariServerUtil.restart.RestartService;
import me.jade.ariServerUtil.rollback.RollbackService;
import me.jade.ariServerUtil.staffmode.StaffModeService;
import me.jade.ariServerUtil.staffmode.StaffTool;
import me.jade.ariServerUtil.util.*;
import me.jade.ariServerUtil.vanish.VanishService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuiController {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final GuiManager gui;
    private final ChatInputService input;
    private final IdentityService identities;
    private final PunishmentService punishments;
    private final ReportService reports;
    private final FreezeService freeze;
    private final RollbackService rollbacks;
    private final AnnouncementService announcements;
    private final GracePeriodService grace;
    private final VanishService vanish;
    private final StaffModeService staffMode;
    private final LockdownService lockdown;
    private final ChatControlService chat;
    private final RestartService restart;
    private final PerformanceService performance;
    private final AuditService audit;

    public GuiController(JavaPlugin plugin, ServerUtilConfig config, Database database, GuiManager gui, ChatInputService input, IdentityService identities, PunishmentService punishments, ReportService reports, FreezeService freeze, RollbackService rollbacks, AnnouncementService announcements, GracePeriodService grace, VanishService vanish, StaffModeService staffMode, LockdownService lockdown, ChatControlService chat, RestartService restart, PerformanceService performance, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.gui = gui;
        this.input = input;
        this.identities = identities;
        this.punishments = punishments;
        this.reports = reports;
        this.freeze = freeze;
        this.rollbacks = rollbacks;
        this.announcements = announcements;
        this.grace = grace;
        this.vanish = vanish;
        this.staffMode = staffMode;
        this.lockdown = lockdown;
        this.chat = chat;
        this.restart = restart;
        this.performance = performance;
        this.audit = audit;
    }

    public void openStaffTool(Player player, StaffTool tool) {
        switch (tool) {
            case PLAYERS, FREEZE, INVENTORY -> openStaffTargets(player, tool, 0);
            case REPORTS -> openReports(player, ReportStatus.OPEN, 0);
            case EXIT -> confirm(player, "Exit Staff Mode?", () -> staffMode.disable(player), () -> player.closeInventory());
        }
    }

    private void openStaffTargets(Player staff, StaffTool tool, int page) {
        String permission = switch (tool) {
            case PLAYERS -> Permissions.PLAYERS;
            case FREEZE -> Permissions.FREEZE;
            default -> Permissions.INV_VIEW;
        };
        require(staff, permission, () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().filter(staff::canSee).toList());
            int currentPage = Math.max(0, Math.min(page, Math.max(0, (players.size() - 1) / 45)));
            List<Player> shown = Pagination.page(players, currentPage, 45);
            Inventory inv = gui.create(staff, "staff-targets", "<dark_gray>" + tool.title, 54, click -> {
                if (click.slot() == 45) { openStaffTargets(staff, tool, currentPage - 1); return; }
                if (click.slot() == 53) { openStaffTargets(staff, tool, currentPage + 1); return; }
                if (click.slot() == 49) { staff.closeInventory(); return; }
                if (click.slot() < 0 || click.slot() >= shown.size()) return;
                require(staff, permission, () -> {
                    Player target = Bukkit.getPlayer(shown.get(click.slot()).getUniqueId());
                    if (target == null || !staff.canSee(target)) {
                        Text.send(staff, "<red>That player is no longer available.");
                        openStaffTargets(staff, tool, currentPage);
                        return;
                    }
                    if (tool == StaffTool.FREEZE) openFreezeTool(staff, target);
                    else if (tool == StaffTool.PLAYERS) openManage(staff, target);
                    else openStaffInspector(staff, target);
                });
            });
            for (int i = 0; i < shown.size(); i++) {
                Player target = shown.get(i);
                inv.setItem(i, gui.plain(Material.PLAYER_HEAD, "<aqua>" + target.getName(), List.of(
                        tool == StaffTool.FREEZE ? "<gray>Open freeze controls." : tool == StaffTool.PLAYERS ? "<gray>Open player management." : "<gray>Inspect inventory without changing it.")));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Previous page", List.of()));
            inv.setItem(49, gui.plain(Material.BARRIER, "<red>Close", List.of()));
            inv.setItem(53, gui.plain(Material.ARROW, "<yellow>Next page", List.of()));
            staff.openInventory(inv);
        });
    }

    private void openFreezeTool(Player staff, Player target) {
        require(staff, Permissions.FREEZE, () -> {
            Inventory inv = gui.create(staff, "staff-freeze", "<dark_gray>Freeze: " + target.getName(), 27, click -> {
                if (click.slot() == 18) { openStaffTargets(staff, StaffTool.FREEZE, 0); return; }
                if (click.slot() == 13) require(staff, Permissions.FREEZE, () -> {
                    if (!target.isOnline() || !staff.canSee(target)) {
                        openStaffTargets(staff, StaffTool.FREEZE, 0);
                        return;
                    }
                    freeze.toggle(staff, target, "Staff Mode freeze tool");
                    openFreezeTool(staff, target);
                });
            });
            inv.setItem(13, gui.plain(Material.PACKED_ICE, freeze.isFrozen(target.getUniqueId()) ? "<green>Unfreeze player" : "<red>Freeze player", List.of("<gray>Click to change freeze status.")));
            inv.setItem(18, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        });
    }

    private void openStaffInspector(Player staff, Player target) {
        if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) return;
        require(staff, Permissions.INV_VIEW, () -> {
            Inventory inv = gui.create(staff, "staff-inspect", "<dark_gray>Inspect: " + target.getName(), 54, click -> {
                if (click.slot() == 49) openStaffTargets(staff, StaffTool.INVENTORY, 0);
            });
            inv.setContents(Arrays.copyOf(Items.cloneContents(target.getInventory().getContents()), 54));
            inv.setItem(49, gui.plain(Material.ARROW, "<yellow>Back", List.of("<gray>Read-only inventory snapshot.")));
            staff.openInventory(inv);
            audit.record(staff, "INVENTORY_VIEW", target.getUniqueId(), target.getName(), "Staff Mode inspector", "opened", "");
        });
    }

    public void openMain(Player player) {
        if (!player.hasPermission(Permissions.USE)) {
            Text.send(player, config.message("no-permission"));
            return;
        }
        Inventory inv = gui.create(player, "main", "<dark_gray>ServerUtil", 54, click -> {
            Player viewer = (Player) click.event().getWhoClicked();
            switch (click.slot()) {
                case 10 -> promptPlayer(viewer, this::openPunishments, Permissions.PUNISHMENTS, "punishments.prompt-player");
                case 11 -> openOnlinePlayers(viewer, 0);
                case 12 -> openReports(viewer, ReportStatus.OPEN, 0);
                case 13 -> promptPlayer(viewer, this::openRollbacks, Permissions.ROLLBACK, "rollbacks.prompt-player");
                case 14 -> require(viewer, Permissions.ANNOUNCE, () -> promptAnnouncement(viewer));
                case 15 -> openGrace(viewer);
                case 16 -> require(viewer, Permissions.VANISH, () -> {
                    vanish.toggle(viewer);
                    openMain(viewer);
                });
                case 19 -> require(viewer, Permissions.STAFFMODE, () -> {
                    staffMode.toggle(viewer);
                    viewer.closeInventory();
                });
                case 20 -> openKeyAll(viewer);
                case 21 -> openLockdown(viewer);
                case 22 -> openChatControls(viewer);
                case 23 -> openRestart(viewer);
                case 24 -> openPerformance(viewer);
                case 25 -> openAudit(viewer, 0);
                case 31 -> viewer.performCommand("conquestsmp:role");
                case 32 -> {
                    if (plugin instanceof dev.turtleroles.TurtleRolesPlugin combined && combined.survival() != null)
                        combined.survival().openDimensions(viewer);
                }
                case 33 -> {
                    if (plugin instanceof dev.turtleroles.TurtleRolesPlugin combined && combined.lootDrops() != null)
                        combined.lootDrops().open(viewer);
                }
                case 49 -> viewer.closeInventory();
                default -> {
                }
            }
        });
        set(inv, 10, Material.IRON_AXE, "Punishments", Permissions.PUNISHMENTS, "Warns, mutes, bans, inventory clears.");
        set(inv, 11, Material.PLAYER_HEAD, "Player Management", Permissions.PLAYERS, "Manage online players and history.");
        set(inv, 12, Material.WRITABLE_BOOK, "Reports", Permissions.REPORTS, "View and resolve player reports.");
        set(inv, 13, Material.CHEST, "Inventory Rollbacks", Permissions.ROLLBACK, "Restore saved death inventories.");
        set(inv, 14, Material.BELL, "Announcements", Permissions.ANNOUNCE, "Send a title announcement.");
        set(inv, 15, Material.GOLDEN_APPLE, "Grace Period", Permissions.GRACE, "Control PvP grace periods.");
        set(inv, 16, Material.ENDER_EYE, "Vanish", Permissions.VANISH, vanish.isVanished(player.getUniqueId()) ? "Currently vanished." : "Currently visible.");
        set(inv, 19, Material.NETHER_STAR, "Staff Mode", Permissions.STAFFMODE, staffMode.active(player.getUniqueId()) ? "Currently active." : "Currently inactive.");
        set(inv, 20, Material.TRIPWIRE_HOOK, "Key All", Permissions.KEYALL, "Distribute cloned deposited items.");
        set(inv, 21, Material.BARRIER, "Server Lockdown", Permissions.LOCKDOWN, lockdown.locked() ? "Locked: " + lockdown.reason() : "Unlocked.");
        set(inv, 22, Material.OAK_SIGN, "Chat Controls", Permissions.CHAT_LOCK, "Clear, lock, slow, filter, staff chat.");
        set(inv, 23, Material.CLOCK, "Restart Manager", Permissions.RESTART, restart.active() ? "Remaining: " + DurationParser.human(restart.remaining()) : "No countdown.");
        set(inv, 24, Material.COMPARATOR, "Performance Dashboard", Permissions.PERFORMANCE, "Cached server metrics and cleanup tools.");
        set(inv, 25, Material.BOOK, "Audit Logs", Permissions.AUDIT, "Review and export staff actions.");
        inv.setItem(31, gui.plain(Material.NAME_TAG, "<green>Roles", List.of("<gray>Open the TurtleRoles role menu.")));
        inv.setItem(32, gui.plain(Material.END_PORTAL_FRAME, "<light_purple>Dimension Access", List.of("<gray>Open or close the Nether and End.", "<gray>Closed dimensions send players to spawn.")));
        inv.setItem(33, gui.plain(Material.CHEST, "<light_purple>Loot Drops", List.of("<gray>Spawn a loot drop now.", "<gray>Toggle the four-hour schedule.")));
        inv.setItem(49, gui.plain(Material.RED_STAINED_GLASS_PANE, "<red>Close", List.of("<gray>Exit the administration GUI.")));
        player.openInventory(inv);
    }

    public void openOnlinePlayers(Player player, int page) {
        require(player, Permissions.PLAYERS, () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            Inventory inv = gui.create(player, "players", "<dark_gray>Players", 54, click -> {
                if (click.slot() == 45) {
                    openMain(player);
                    return;
                }
                int index = page * 45 + click.slot();
                if (index >= 0 && index < players.size()) {
                    openManage(player, players.get(index));
                }
            });
            List<Player> shown = Pagination.page(players, page, 45);
            for (int i = 0; i < shown.size(); i++) {
                Player target = shown.get(i);
                inv.setItem(i, gui.plain(Material.PLAYER_HEAD, "<green>" + target.getName(), List.of(
                        "<gray>UUID: " + target.getUniqueId(),
                        "<gray>World: " + target.getWorld().getName(),
                        "<gray>Ping: " + target.getPing()
                )));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            player.openInventory(inv);
        });
    }

    public void openManage(Player staff, Player target) {
        if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) return;
        require(staff, Permissions.PLAYERS, () -> {
            Inventory inv = gui.create(staff, "manage", "<dark_gray>Manage " + target.getName(), 54, click -> {
                if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) return;
                Player viewer = (Player) click.event().getWhoClicked();
                switch (click.slot()) {
                    case 10 -> require(viewer, Permissions.HEAL, () -> {
                        target.setHealth(Math.min(target.getMaxHealth(), target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
                        audit.record(viewer, "PLAYER_HEAL", target.getUniqueId(), target.getName(), "", "success", "");
                    });
                    case 11 -> require(viewer, Permissions.FEED, () -> {
                        target.setFoodLevel(20);
                        audit.record(viewer, "PLAYER_FEED", target.getUniqueId(), target.getName(), "", "success", "");
                    });
                    case 12 -> require(viewer, Permissions.EFFECTS, () -> {
                        target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
                        target.setFireTicks(0);
                        audit.record(viewer, "PLAYER_EFFECTS_CLEAR", target.getUniqueId(), target.getName(), "", "success", "");
                    });
                    case 13 -> require(viewer, Permissions.GAMEMODE, () -> {
                        target.setGameMode(nextGameMode(target.getGameMode()));
                        audit.record(viewer, "PLAYER_GAMEMODE", target.getUniqueId(), target.getName(), target.getGameMode().name(), "success", "");
                    });
                    case 14 -> require(viewer, Permissions.INV_VIEW, () -> openInventoryEditor(viewer, target, false));
                    case 15 -> require(viewer, Permissions.EC_VIEW, () -> openEnderEditor(viewer, target, false));
                    case 16 -> require(viewer, Permissions.FREEZE, () -> {
                        freeze.toggle(viewer, target, "GUI toggle");
                        openManage(viewer, target);
                    });
                    case 19 -> openPunishments(viewer, new Database.PlayerIdentity(target.getUniqueId(), target.getName(), "", target.getFirstPlayed(), target.getLastSeen()));
                    case 20 -> openHistory(viewer, target.getUniqueId(), target.getName(), 0);
                    case 21 -> require(viewer, Permissions.REPORT_NOTES, () -> promptNote(viewer, target));
                    case 22 -> openReportsFor(viewer, target);
                    case 45 -> openOnlinePlayers(viewer, 0);
                    default -> {
                    }
                }
            });
            inv.setItem(4, gui.plain(Material.PLAYER_HEAD, "<aqua>" + target.getName(), playerInfo(staff, target)));
            set(inv, 10, Material.POTION, "Heal", Permissions.HEAL, "Restore health.");
            set(inv, 11, Material.COOKED_BEEF, "Feed", Permissions.FEED, "Restore hunger.");
            set(inv, 12, Material.MILK_BUCKET, "Clear Effects", Permissions.EFFECTS, "Extinguish and clear potion effects.");
            set(inv, 13, Material.GRASS_BLOCK, "Change Gamemode", Permissions.GAMEMODE, "Cycle gamemode.");
            set(inv, 14, Material.CHEST, "Inventory", Permissions.INV_VIEW, "View or edit inventory.");
            set(inv, 15, Material.ENDER_CHEST, "Ender Chest", Permissions.EC_VIEW, "View or edit ender chest.");
            set(inv, 16, Material.PACKED_ICE, freeze.isFrozen(target.getUniqueId()) ? "Unfreeze" : "Freeze", Permissions.FREEZE, "Toggle freeze.");
            set(inv, 19, Material.IRON_AXE, "Punishments", Permissions.PUNISHMENTS, "Open punishment menu.");
            set(inv, 20, Material.BOOK, "History", Permissions.HISTORY, "View player history.");
            set(inv, 21, Material.PAPER, "Staff Notes", Permissions.REPORT_NOTES, "Add a private note.");
            set(inv, 22, Material.WRITABLE_BOOK, "Reports", Permissions.REPORTS, "View reports involving this player.");
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        });
    }

    public void openPunishments(Player staff, Database.PlayerIdentity target) {
        require(staff, Permissions.PUNISHMENTS, () -> {
            Inventory inv = gui.create(staff, "punish", "<dark_gray>Punish " + target.name(), 54, click -> {
                Player viewer = (Player) click.event().getWhoClicked();
                switch (click.slot()) {
                    case 10 -> reasonThenPunish(viewer, target, PunishmentType.WARNING, null, Permissions.PUNISH_WARN);
                    case 11 -> durationReasonPunish(viewer, target, PunishmentType.TEMP_MUTE, Permissions.PUNISH_TEMPMUTE);
                    case 12 -> confirmPunish(viewer, target, PunishmentType.MUTE, null, Permissions.PUNISH_MUTE);
                    case 13 -> durationReasonPunish(viewer, target, PunishmentType.TEMP_BAN, Permissions.PUNISH_TEMPBAN);
                    case 14 -> confirmPunish(viewer, target, PunishmentType.BAN, null, Permissions.PUNISH_BAN);
                    case 15 -> confirmPunish(viewer, target, PunishmentType.CLEAR_INVENTORY, null, Permissions.PUNISH_CLEAR_INV);
                    case 16 -> confirmPunish(viewer, target, PunishmentType.CLEAR_ENDER_CHEST, null, Permissions.PUNISH_CLEAR_ENDER);
                    case 20 -> openHistory(viewer, target.uuid(), target.name(), 0);
                    case 45 -> openMain(viewer);
                    default -> {
                    }
                }
            });
            set(inv, 10, Material.PAPER, "Warning", Permissions.PUNISH_WARN, "Send and record a warning.");
            set(inv, 11, Material.CLOCK, "Temporary Mute", Permissions.PUNISH_TEMPMUTE, "Requires duration and reason.");
            set(inv, 12, Material.BARRIER, "Permanent Mute", Permissions.PUNISH_MUTE, "Requires confirmation.");
            set(inv, 13, Material.REDSTONE_TORCH, "Temporary Ban", Permissions.PUNISH_TEMPBAN, "Requires duration and reason.");
            set(inv, 14, Material.LAVA_BUCKET, "Permanent Ban", Permissions.PUNISH_BAN, "Requires confirmation.");
            set(inv, 15, Material.CHEST, "Clear Inventory", Permissions.PUNISH_CLEAR_INV, "Target must be online.");
            set(inv, 16, Material.ENDER_CHEST, "Clear Ender Chest", Permissions.PUNISH_CLEAR_ENDER, "Target must be online.");
            set(inv, 20, Material.BOOK, "Player History", Permissions.HISTORY, "View and revoke active punishments.");
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        });
    }

    public void openReports(Player staff, ReportStatus status, int page) {
        require(staff, Permissions.REPORTS, () -> reports.reports(status).thenAccept(records -> Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inv = gui.create(staff, "reports", "<dark_gray>Reports " + status, 54, click -> {
                if (click.slot() == 45) {
                    openMain(staff);
                    return;
                }
                if (click.slot() == 46) {
                    openReports(staff, previousStatus(status), 0);
                    return;
                }
                if (click.slot() == 47) {
                    openReports(staff, nextStatus(status), 0);
                    return;
                }
                int index = page * 45 + click.slot();
                if (index >= 0 && index < records.size()) {
                    ReportRecord report = records.get(index);
                    openReportDetail(staff, report);
                }
            });
            List<ReportRecord> shown = Pagination.page(records, page, 45);
            for (int i = 0; i < shown.size(); i++) {
                ReportRecord report = shown.get(i);
                inv.setItem(i, gui.plain(Material.WRITABLE_BOOK, "<yellow>Report #" + report.id(), List.of(
                        "<gray>Status: " + report.status(),
                        "<gray>Reporter: " + report.reporterName(),
                        "<gray>Reported: " + report.reportedName(),
                        "<gray>Reason: " + report.reason(),
                        Bukkit.getPlayer(report.reportedUuid()) == null ? "<red>Offline" : "<green>Online"
                )));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            inv.setItem(46, gui.plain(Material.COMPASS, "<yellow>Previous Filter", List.of()));
            inv.setItem(47, gui.plain(Material.COMPASS, "<yellow>Next Filter", List.of()));
            staff.openInventory(inv);
        })));
    }

    public void openHistory(Player staff, UUID targetUuid, String targetName, int page) {
        require(staff, Permissions.HISTORY, () -> database.query(conn -> {
            List<String> lines = new ArrayList<>();
            try (var ps = conn.prepareStatement("SELECT id,timestamp,type,staff_name,detail,related_id FROM history WHERE target_uuid=? ORDER BY timestamp DESC LIMIT 500")) {
                ps.setString(1, targetUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lines.add("#" + rs.getLong(1) + " " + Instant.ofEpochMilli(rs.getLong(2)) + " " + rs.getString(3) + " by " + rs.getString(4) + " " + rs.getString(5) + " " + rs.getString(6));
                    }
                }
            }
            return lines;
        }).thenAccept(lines -> Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inv = gui.create(staff, "history", "<dark_gray>History " + targetName, 54, click -> {
                if (click.slot() == 45) {
                    openMain(staff);
                }
            });
            List<String> shown = Pagination.page(lines, page, 45);
            for (int i = 0; i < shown.size(); i++) {
                inv.setItem(i, gui.plain(Material.PAPER, "<yellow>History", List.of("<gray>" + shown.get(i))));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        })));
    }

    public void openAudit(Player staff, int page) {
        require(staff, Permissions.AUDIT, () -> database.query(conn -> {
            List<String> lines = new ArrayList<>();
            try (var ps = conn.prepareStatement("SELECT id,timestamp,staff_name,action,target_name,reason,result FROM audit ORDER BY timestamp DESC LIMIT 500")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lines.add(rs.getLong(1) + "," + Instant.ofEpochMilli(rs.getLong(2)) + "," + safe(rs.getString(3)) + "," + safe(rs.getString(4)) + "," + safe(rs.getString(5)) + "," + safe(rs.getString(6)) + "," + safe(rs.getString(7)));
                    }
                }
            }
            return lines;
        }).thenAccept(lines -> Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inv = gui.create(staff, "audit", "<dark_gray>Audit Logs", 54, click -> {
                if (click.slot() == 45) {
                    openMain(staff);
                } else if (click.slot() == 53) {
                    exportAudit(staff, lines);
                }
            });
            for (int i = 0; i < Pagination.page(lines, page, 45).size(); i++) {
                inv.setItem(i, gui.plain(Material.PAPER, "<yellow>Audit", List.of("<gray>" + Pagination.page(lines, page, 45).get(i))));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            set(inv, 53, Material.FEATHER, "Export CSV", Permissions.AUDIT_EXPORT, "Writes audit-export.csv in the plugin folder.");
            staff.openInventory(inv);
        })));
    }

    public void openRollbacks(Player staff, Database.PlayerIdentity target) {
        require(staff, Permissions.ROLLBACK, () -> rollbacks.snapshotSummaries(target.uuid()).thenAccept(lines -> Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inv = gui.create(staff, "rollbacks", "<dark_gray>Rollbacks " + target.name(), 54, click -> {
                if (click.slot() == 45) {
                    openMain(staff);
                    return;
                }
                if (click.slot() >= 0 && click.slot() < lines.size()) {
                    String line = lines.get(click.slot());
                    long id = Long.parseLong(line.substring(1, line.indexOf(' ')));
                    confirm(staff, "Apply rollback #" + id + "?", () -> {
                        rollbacks.queueOrApply(staff, target.uuid(), target.name(), id);
                        openMain(staff);
                    }, () -> openRollbacks(staff, target));
                }
            });
            for (int i = 0; i < Math.min(45, lines.size()); i++) {
                inv.setItem(i, gui.plain(Material.CHEST, "<yellow>Snapshot", List.of("<gray>" + lines.get(i), "<red>Click opens confirmation.")));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        })));
    }

    public void openGrace(Player staff) {
        require(staff, Permissions.GRACE, () -> {
            Inventory inv = gui.create(staff, "grace", "<dark_gray>Grace Period", 27, click -> {
                if (click.slot() == 10) {
                    promptDuration(staff, "grace.prompt-duration", duration -> {
                        grace.start(staff, duration, true);
                        openGrace(staff);
                    }, () -> openGrace(staff));
                } else if (click.slot() == 12) {
                    grace.stop(staff);
                    openGrace(staff);
                } else if (click.slot() == 18) {
                    openMain(staff);
                }
            });
            inv.setItem(4, gui.plain(Material.CLOCK, "<aqua>Status", List.of(grace.active() ? "<green>Active: " + DurationParser.human(grace.remaining()) : "<red>Inactive")));
            inv.setItem(10, gui.plain(Material.LIME_DYE, "<green>Start", List.of("<gray>Enter a duration in chat.")));
            inv.setItem(12, gui.plain(Material.RED_DYE, "<red>Stop", List.of("<gray>Cancel current grace period.")));
            inv.setItem(18, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        });
    }

    public void openChatControls(Player staff) {
        Inventory inv = gui.create(staff, "chat", "<dark_gray>Chat Controls", 36, click -> {
            Player viewer = (Player) click.event().getWhoClicked();
            switch (click.slot()) {
                case 10 -> require(viewer, Permissions.CHAT_CLEAR, () -> confirm(viewer, "Clear global chat?", () -> {
                    chat.clear(viewer);
                    openChatControls(viewer);
                }, () -> openChatControls(viewer)));
                case 11 -> require(viewer, Permissions.CHAT_LOCK, () -> {
                    chat.setLocked(viewer, !chat.locked());
                    openChatControls(viewer);
                });
                case 12 -> require(viewer, Permissions.CHAT_SLOW, () -> promptDuration(viewer, "chat.prompt-slow", duration -> {
                    chat.setSlow(viewer, duration);
                    openChatControls(viewer);
                }, () -> openChatControls(viewer)));
                case 13 -> require(viewer, Permissions.STAFFCHAT, () -> {
                    chat.toggleStaffChat(viewer);
                    openChatControls(viewer);
                });
                case 27 -> openMain(viewer);
                default -> {
                }
            }
        });
        set(inv, 10, Material.WATER_BUCKET, "Clear Chat", Permissions.CHAT_CLEAR, "Push blank lines to clients.");
        set(inv, 11, Material.IRON_DOOR, chat.locked() ? "Unlock Chat" : "Lock Chat", Permissions.CHAT_LOCK, chat.locked() ? "Currently locked." : "Currently unlocked.");
        set(inv, 12, Material.CLOCK, "Slow Mode", Permissions.CHAT_SLOW, "Delay: " + DurationParser.human(chat.slowDelay()));
        set(inv, 13, Material.WRITABLE_BOOK, "Staff Chat Toggle", Permissions.STAFFCHAT, "Toggle personal staff-chat mode.");
        inv.setItem(27, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
        staff.openInventory(inv);
    }

    public void openRestart(Player staff) {
        require(staff, Permissions.RESTART, () -> {
            Inventory inv = gui.create(staff, "restart", "<dark_gray>Restart Manager", 36, click -> {
                Player viewer = (Player) click.event().getWhoClicked();
                switch (click.slot()) {
                    case 10 -> promptDuration(viewer, "restart.prompt-duration", duration -> {
                        restart.schedule(viewer, duration, true);
                        openRestart(viewer);
                    }, () -> openRestart(viewer));
                    case 11 -> {
                        restart.cancel(viewer);
                        openRestart(viewer);
                    }
                    case 12 -> {
                        restart.schedule(viewer, Duration.ofMinutes(5), true);
                        openRestart(viewer);
                    }
                    case 27 -> openMain(viewer);
                    default -> {
                    }
                }
            });
            inv.setItem(4, gui.plain(Material.CLOCK, "<aqua>Status", List.of(restart.active() ? "<green>Remaining: " + DurationParser.human(restart.remaining()) : "<red>No countdown", "<gray>Scheduled by: " + restart.scheduler())));
            inv.setItem(10, gui.plain(Material.LIME_DYE, "<green>Custom Countdown", List.of("<gray>Enter a duration.")));
            inv.setItem(11, gui.plain(Material.RED_DYE, "<red>Cancel Countdown", List.of()));
            inv.setItem(12, gui.plain(Material.CLOCK, "<yellow>5 Minute Preset", List.of()));
            inv.setItem(27, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        });
    }

    public void openLockdown(Player staff) {
        require(staff, Permissions.LOCKDOWN, () -> {
            Inventory inv = gui.create(staff, "lockdown", "<dark_gray>Lockdown", 27, click -> {
                if (click.slot() == 10) {
                    input.prompt(staff, config.message("lockdown.prompt-reason"), response -> confirm(staff, "Enable lockdown and kick affected players?", () -> {
                        lockdown.set(staff, true, response.input(), true);
                        openLockdown(staff);
                    }, () -> openLockdown(staff)), () -> openLockdown(staff));
                } else if (click.slot() == 12) {
                    lockdown.set(staff, false, "", false);
                    openLockdown(staff);
                } else if (click.slot() == 18) {
                    openMain(staff);
                }
            });
            inv.setItem(4, gui.plain(Material.BARRIER, "<aqua>Status", List.of(lockdown.locked() ? "<red>Locked: " + lockdown.reason() : "<green>Unlocked", "<gray>Affected online: " + affectedLockdownPlayers())));
            inv.setItem(10, gui.plain(Material.RED_DYE, "<red>Enable", List.of("<gray>Requires confirmation.")));
            inv.setItem(12, gui.plain(Material.LIME_DYE, "<green>Disable", List.of()));
            inv.setItem(18, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            staff.openInventory(inv);
        });
    }

    public void openPerformance(Player staff) {
        require(staff, Permissions.PERFORMANCE, () -> {
            Inventory inv = gui.create(staff, "performance", "<dark_gray>Performance", 54, click -> {
                if (click.slot() == 45) {
                    openMain(staff);
                } else if (click.slot() == 53) {
                    require(staff, Permissions.PERFORMANCE_CLEANUP, () -> confirm(staff, "Remove " + performance.previewCleanup() + " disposable entities?", () -> {
                        performance.cleanup(staff);
                        openPerformance(staff);
                    }, () -> openPerformance(staff)));
                }
            });
            List<String> lines = performance.metrics().lines();
            for (int i = 0; i < Math.min(45, lines.size()); i++) {
                inv.setItem(i, gui.plain(Material.PAPER, "<yellow>Metric", List.of("<gray>" + lines.get(i))));
            }
            inv.setItem(45, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
            set(inv, 53, Material.TNT, "Preview Cleanup", Permissions.PERFORMANCE_CLEANUP, "Disposable entities: " + performance.previewCleanup());
            staff.openInventory(inv);
        });
    }

    public void openKeyAll(Player staff) {
        require(staff, Permissions.KEYALL, () -> {
            AtomicBoolean completed = new AtomicBoolean();
            List<Integer> depositSlots = List.of(10, 11, 12, 19, 20, 21);
            Inventory[] holder = new Inventory[1];
            Inventory inv = gui.create(staff, "keyall", "<dark_gray>Key All", 45, click -> {
                if (depositSlots.contains(click.slot())) {
                    click.event().setCancelled(false);
                    return;
                }
                if (click.slot() == 24 && completed.compareAndSet(false, true)) {
                    Inventory keyInventory = holder[0];
                    List<ItemStack> items = depositSlots.stream().map(keyInventory::getItem).filter(item -> item != null && !item.getType().isAir()).map(ItemStack::clone).toList();
                    if (items.isEmpty()) {
                        completed.set(false);
                        Text.send(staff, "<red>Deposit at least one item.");
                        return;
                    }
                    int recipients = 0;
                    for (Player recipient : Bukkit.getOnlinePlayers()) {
                        recipients++;
                        for (ItemStack item : items) {
                            Map<Integer, ItemStack> overflow = recipient.getInventory().addItem(item.clone());
                            overflow.values().forEach(leftover -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover));
                            if (!overflow.isEmpty()) {
                                Text.send(recipient, config.message("keyall.dropped"));
                            }
                        }
                    }
                    depositSlots.forEach(slot -> keyInventory.setItem(slot, null));
                    Text.send(staff, config.message("keyall.sent").replace("<count>", Integer.toString(recipients)));
                    audit.record(staff, "KEYALL", null, "", "items " + items.size(), "recipients " + recipients, "");
                    int finalRecipients = recipients;
                    database.execute(conn -> {
                        try (var ps = conn.prepareStatement("INSERT INTO keyall_transactions(staff_uuid,staff_name,items,recipients,created_at,completed) VALUES(?,?,?,?,?,1)")) {
                            ps.setString(1, staff.getUniqueId().toString());
                            ps.setString(2, staff.getName());
                            ps.setString(3, Items.serialize(items.toArray(ItemStack[]::new)));
                            ps.setString(4, Integer.toString(finalRecipients));
                            ps.setLong(5, System.currentTimeMillis());
                            ps.executeUpdate();
                        }
                    });
                    staff.closeInventory();
                } else if (click.slot() == 26) {
                    completed.set(true);
                    returnDeposits(staff, holder[0], depositSlots);
                    staff.closeInventory();
                }
            }, close -> {
                if (!completed.get()) {
                    returnDeposits((Player) close.event().getPlayer(), holder[0], depositSlots);
                    completed.set(true);
                }
            });
            holder[0] = inv;
            depositSlots.forEach(slot -> inv.setItem(slot, null));
            inv.setItem(24, gui.plain(Material.LIME_WOOL, "<green>Confirm", List.of("<gray>Clones deposited items to all online players.")));
            inv.setItem(26, gui.plain(Material.RED_WOOL, "<red>Cancel", List.of("<gray>Return deposited items.")));
            staff.openInventory(inv);
        });
    }

    private void openInventoryEditor(Player staff, Player target, boolean edit) {
        if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) return;
        boolean canEdit = staff.hasPermission(Permissions.INV_EDIT);
        ItemStack[] before = Items.cloneContents(target.getInventory().getContents());
        Inventory[] holder = new Inventory[1];
        Inventory inv = gui.create(staff, canEdit ? "inventory-edit" : "inventory-view", (canEdit ? "<dark_gray>Edit " : "<dark_gray>View ") + target.getName(), 54, click -> {
            if (canEdit && click.slot() < 41) {
                click.event().setCancelled(false);
            }
        }, close -> {
            if (!canEdit || !dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) {
                return;
            }
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online == null) {
                Text.send((Player) close.event().getPlayer(), "<red>The target disconnected before changes could be saved.");
                return;
            }
            rollbacks.saveSnapshot(online, "inventory-edit-before", "Staff inventory editor", false);
            ItemStack[] contents = Arrays.copyOf(holder[0].getContents(), online.getInventory().getContents().length);
            online.getInventory().setContents(contents);
            audit.record((Player) close.event().getPlayer(), "INVENTORY_EDIT_SAVE", online.getUniqueId(), online.getName(), summarizeItems(before) + " -> " + summarizeItems(contents), "saved", "");
        });
        holder[0] = inv;
        inv.setContents(Arrays.copyOf(target.getInventory().getContents(), 54));
        staff.openInventory(inv);
        audit.record(staff, canEdit ? "INVENTORY_EDIT_OPEN" : "INVENTORY_VIEW", target.getUniqueId(), target.getName(), "", "opened", "");
    }

    private void openEnderEditor(Player staff, Player target, boolean edit) {
        if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) return;
        boolean canEdit = staff.hasPermission(Permissions.EC_EDIT);
        ItemStack[] before = Items.cloneContents(target.getEnderChest().getContents());
        Inventory[] holder = new Inventory[1];
        Inventory inv = gui.create(staff, canEdit ? "ender-edit" : "ender-view", (canEdit ? "<dark_gray>Edit Ender " : "<dark_gray>View Ender ") + target.getName(), 27, click -> {
            if (canEdit) {
                click.event().setCancelled(false);
            }
        }, close -> {
            if (!canEdit || !dev.turtleroles.service.StaffAccess.check(plugin, staff, target.getUniqueId(), true)) {
                return;
            }
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online == null) {
                Text.send((Player) close.event().getPlayer(), "<red>The target disconnected before changes could be saved.");
                return;
            }
            rollbacks.saveSnapshot(online, "ender-edit-before", "Staff ender chest editor", false);
            online.getEnderChest().setContents(holder[0].getContents());
            audit.record((Player) close.event().getPlayer(), "ENDERCHEST_EDIT_SAVE", online.getUniqueId(), online.getName(), summarizeItems(before) + " -> " + summarizeItems(holder[0].getContents()), "saved", "");
        });
        holder[0] = inv;
        inv.setContents(target.getEnderChest().getContents());
        staff.openInventory(inv);
        audit.record(staff, canEdit ? "ENDERCHEST_EDIT_OPEN" : "ENDERCHEST_VIEW", target.getUniqueId(), target.getName(), "", "opened", "");
    }

    private void promptPlayer(Player staff, java.util.function.BiConsumer<Player, Database.PlayerIdentity> next, String permission, String promptPath) {
        require(staff, permission, () -> input.prompt(staff, config.message(promptPath), response -> identities.resolve(response.input()).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (found.isEmpty()) {
                Text.send(staff, config.message("player-not-found"));
                openMain(staff);
            } else {
                next.accept(staff, found.get());
            }
        })), () -> openMain(staff)));
    }

    private void durationReasonPunish(Player staff, Database.PlayerIdentity target, PunishmentType type, String permission) {
        require(staff, permission, () -> promptDuration(staff, "punishments.prompt-duration", duration ->
                input.prompt(staff, config.message("punishments.prompt-reason"), response -> punishments.punish(staff, target.uuid(), target.name(), type, duration, response.input(), null), () -> openPunishments(staff, target)), () -> openPunishments(staff, target)));
    }

    private void reasonThenPunish(Player staff, Database.PlayerIdentity target, PunishmentType type, Duration duration, String permission) {
        require(staff, permission, () -> input.prompt(staff, config.message("punishments.prompt-reason"), response -> punishments.punish(staff, target.uuid(), target.name(), type, duration, response.input(), null), () -> openPunishments(staff, target)));
    }

    private void confirmPunish(Player staff, Database.PlayerIdentity target, PunishmentType type, Duration duration, String permission) {
        require(staff, permission, () -> input.prompt(staff, config.message("punishments.prompt-reason"), response -> confirm(staff, "Confirm " + type + " for " + target.name() + "?", () -> {
            punishments.punish(staff, target.uuid(), target.name(), type, duration, response.input(), null);
            openPunishments(staff, target);
        }, () -> openPunishments(staff, target)), () -> openPunishments(staff, target)));
    }

    private void promptAnnouncement(Player staff) {
        input.prompt(staff, config.message("announcement.prompt"), response -> {
            announcements.announce(staff, response.input());
            openMain(staff);
        }, () -> openMain(staff));
    }

    private void promptNote(Player staff, Player target) {
        input.prompt(staff, config.message("notes.prompt"), response -> {
            reports.addNote(staff, target.getUniqueId(), target.getName(), response.input());
            openManage(staff, target);
        }, () -> openManage(staff, target));
    }

    private void promptDuration(Player staff, String promptPath, java.util.function.Consumer<Duration> next, Runnable back) {
        input.prompt(staff, config.message(promptPath), response -> {
            try {
                next.accept(DurationParser.parse(response.input()));
            } catch (IllegalArgumentException ex) {
                Text.send(staff, "<red>" + ex.getMessage());
                back.run();
            }
        }, back);
    }

    private void confirm(Player staff, String title, Runnable yes, Runnable no) {
        Inventory inv = gui.create(staff, "confirm", "<dark_red>Confirm", 27, click -> {
            if (click.slot() == 11) {
                yes.run();
            } else if (click.slot() == 15) {
                no.run();
            }
        });
        inv.setItem(4, gui.plain(Material.PAPER, "<yellow>" + title, List.of()));
        inv.setItem(11, gui.plain(Material.LIME_WOOL, "<green>Confirm", List.of()));
        inv.setItem(15, gui.plain(Material.RED_WOOL, "<red>Cancel", List.of()));
        staff.openInventory(inv);
    }

    private void require(Player player, String permission, Runnable action) {
        if (!player.hasPermission(permission)) {
            Text.send(player, config.message("no-permission"));
            return;
        }
        action.run();
    }

    private void set(Inventory inv, int slot, Material material, String name, String permission, String lore) {
        boolean allowed = true;
        if (inv.getHolder() instanceof GuiHolder holder) {
            Player viewer = Bukkit.getPlayer(holder.viewer());
            allowed = viewer == null || viewer.hasPermission(permission);
        }
        inv.setItem(slot, gui.button(material, name, List.of("<gray>" + lore, "<dark_gray>Permission: " + permission), allowed));
    }

    private List<String> playerInfo(Player viewer, Player target) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>UUID: " + target.getUniqueId());
        lore.add("<gray>Health: " + (int) target.getHealth() + "/" + (int) target.getMaxHealth());
        lore.add("<gray>Food: " + target.getFoodLevel());
        lore.add("<gray>Gamemode: " + target.getGameMode());
        lore.add("<gray>Ping: " + target.getPing());
        lore.add("<gray>World: " + target.getWorld().getName());
        lore.add("<gray>Playtime ticks: " + target.getStatistic(Statistic.PLAY_ONE_MINUTE));
        lore.add("<gray>First join: " + Instant.ofEpochMilli(target.getFirstPlayed()));
        lore.add("<gray>Last join: " + Instant.ofEpochMilli(target.getLastSeen()));
        lore.add(freeze.isFrozen(target.getUniqueId()) ? "<red>Frozen" : "<green>Not frozen");
        lore.add(vanish.isVanished(target.getUniqueId()) ? "<yellow>Vanished" : "<green>Visible");
        if (viewer.hasPermission(Permissions.SENSITIVE) && target.getAddress() != null) {
            lore.add("<red>IP: " + target.getAddress().getAddress().getHostAddress());
        }
        return lore;
    }

    private GameMode nextGameMode(GameMode current) {
        return switch (current) {
            case SURVIVAL -> GameMode.CREATIVE;
            case CREATIVE -> GameMode.ADVENTURE;
            case ADVENTURE -> GameMode.SPECTATOR;
            case SPECTATOR -> GameMode.SURVIVAL;
        };
    }

    private ReportStatus nextStatus(ReportStatus status) {
        return switch (status) {
            case OPEN -> ReportStatus.ASSIGNED;
            case ASSIGNED -> ReportStatus.RESOLVED;
            case RESOLVED -> ReportStatus.REJECTED;
            case REJECTED -> ReportStatus.OPEN;
        };
    }

    private ReportStatus previousStatus(ReportStatus status) {
        return switch (status) {
            case OPEN -> ReportStatus.REJECTED;
            case ASSIGNED -> ReportStatus.OPEN;
            case RESOLVED -> ReportStatus.ASSIGNED;
            case REJECTED -> ReportStatus.RESOLVED;
        };
    }

    private void openReportDetail(Player staff, ReportRecord report) {
        Inventory inv = gui.create(staff, "report-detail", "<dark_gray>Report #" + report.id(), 36, click -> {
            switch (click.slot()) {
                case 10 -> reports.transition(staff, report.id(), ReportStatus.ASSIGNED, "assigned");
                case 11 -> reports.transition(staff, report.id(), ReportStatus.RESOLVED, "resolved");
                case 12 -> reports.transition(staff, report.id(), ReportStatus.REJECTED, "rejected");
                case 13 -> openHistory(staff, report.reportedUuid(), report.reportedName(), 0);
                case 27 -> openReports(staff, report.status(), 0);
                default -> {
                }
            }
        });
        inv.setItem(4, gui.plain(Material.WRITABLE_BOOK, "<yellow>Report #" + report.id(), List.of("<gray>" + report.reason(), "<gray>Status: " + report.status())));
        inv.setItem(10, gui.plain(Material.NAME_TAG, "<green>Assign to Me", List.of()));
        inv.setItem(11, gui.plain(Material.LIME_DYE, "<green>Resolve", List.of()));
        inv.setItem(12, gui.plain(Material.RED_DYE, "<red>Reject", List.of()));
        inv.setItem(13, gui.plain(Material.BOOK, "<yellow>Player History", List.of()));
        inv.setItem(27, gui.plain(Material.ARROW, "<yellow>Back", List.of()));
        staff.openInventory(inv);
    }

    private void openReportsFor(Player staff, Player target) {
        openReports(staff, ReportStatus.OPEN, 0);
    }

    private int affectedLockdownPlayers() {
        return (int) Bukkit.getOnlinePlayers().stream().filter(player -> !player.isOp() && !player.hasPermission(Permissions.LOCKDOWN_BYPASS)).count();
    }

    private void returnDeposits(Player staff, Inventory inv, List<Integer> slots) {
        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                Map<Integer, ItemStack> overflow = staff.getInventory().addItem(item);
                overflow.values().forEach(leftover -> staff.getWorld().dropItemNaturally(staff.getLocation(), leftover));
                inv.setItem(slot, null);
            }
        }
    }

    private void exportAudit(Player staff, List<String> lines) {
        if (!staff.hasPermission(Permissions.AUDIT_EXPORT)) {
            Text.send(staff, config.message("no-permission"));
            return;
        }
        File file = new File(config.dataFolder(), "audit-export.csv");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("id,timestamp,staff,action,target,reason,result\n");
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
            Text.send(staff, "<green>Exported audit logs to " + file.getName() + ".");
        } catch (Exception ex) {
            Text.send(staff, "<red>Audit export failed: " + ex.getMessage());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(",", ";").replace("\n", " ");
    }

    private String summarizeItems(ItemStack[] items) {
        Map<Material, Integer> counts = new TreeMap<>(Comparator.comparing(Enum::name));
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                counts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        return counts.toString();
    }
}

package dev.turtleroles.command;

import dev.turtleroles.config.TurtleConfig;
import dev.turtleroles.policy.Actor;
import dev.turtleroles.punishment.PunishmentCase;
import dev.turtleroles.punishment.PunishmentType;
import dev.turtleroles.service.PunishmentService;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.util.CommandUtil;
import dev.turtleroles.util.DurationParser;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

public final class ModerationCommand implements CommandExecutor {
    private final String kind;
    private final RoleService roles;
    private final PlayerRepository players;
    private final PunishmentService punishments;
    private final TurtleConfig config;

    public ModerationCommand(String kind, RoleService roles, PlayerRepository players, PunishmentService punishments, TurtleConfig config) {
        this.kind = kind;
        this.roles = roles;
        this.players = players;
        this.punishments = punishments;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            switch (kind) {
                case "warn" -> issue(sender, args, PunishmentType.WARNING, false, false);
                case "tempmute" -> issue(sender, args, PunishmentType.TEMP_MUTE, true, false);
                case "mute" -> issue(sender, args, PunishmentType.PERM_MUTE, false, false);
                case "tempban" -> issue(sender, args, PunishmentType.TEMP_BAN, true, true);
                case "ban" -> issue(sender, args, PunishmentType.PERM_BAN, false, true);
                case "kick" -> issue(sender, args, PunishmentType.KICK, false, true);
                case "unwarn", "unmute", "unban" -> revoke(sender, args);
                case "warnings", "history" -> history(sender, args);
                case "case" -> showCase(sender, args);
                default -> CommandUtil.error(sender, "Unknown moderation command.");
            }
        } catch (Exception e) {
            CommandUtil.error(sender, e.getMessage());
        }
        return true;
    }

    private void issue(CommandSender sender, String[] args, PunishmentType type, boolean durationRequired, boolean applyKick) throws SQLException {
        int minArgs = durationRequired ? 3 : 2;
        if (args.length < minArgs) {
            throw new IllegalArgumentException("Usage: /" + kind + " <player> " + (durationRequired ? "<duration> " : "") + "<reason>");
        }
        PlayerRecord target = players.resolveKnown(args[0]).orElseThrow(() -> new IllegalArgumentException("Unknown known player: " + args[0]));
        Duration duration = durationRequired ? DurationParser.parseStrict(args[1]) : null;
        String reason = CommandUtil.reason(args, durationRequired ? 2 : 1, config.reasonMaxLength());
        Actor actor = roles.actor(sender);
        PunishmentCase created = punishments.issue(actor, target, type, duration, reason);
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null && (type.ban() || type == PunishmentType.KICK)) {
            online.kick(net.kyori.adventure.text.Component.text(type == PunishmentType.KICK ? "Kicked: " + reason : "Banned: " + reason));
        }
        if (type.ban()) {
            Date expiry = created.expiresAt() == null ? null : Date.from(created.expiresAt());
            Bukkit.getBanList(BanList.Type.NAME).addBan(target.lastName(), reason, expiry, actor.name());
        }
        CommandUtil.ok(sender, "Created case #" + created.id() + " (" + type.name().toLowerCase(Locale.ROOT) + ") for " + target.lastName() + ".");
    }

    private void revoke(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /" + kind + " <case-id-or-player> <reason>");
        }
        long id = resolveReversalCaseId(args[0]);
        String reason = CommandUtil.reason(args, 1, config.reasonMaxLength());
        punishments.revoke(roles.actor(sender), id, reason);
        if ("unban".equals(kind)) {
            PunishmentCase punishment = punishments.get(id).orElseThrow();
            Bukkit.getBanList(BanList.Type.NAME).pardon(punishment.targetName());
        }
        CommandUtil.ok(sender, "Revoked case #" + id + ".");
    }

    private long resolveReversalCaseId(String input) throws SQLException {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
            if (!"unmute".equals(kind) && !"unban".equals(kind)) {
                throw new IllegalArgumentException("That command requires a case ID.");
            }
            PlayerRecord target = players.resolveKnown(input).orElseThrow(() -> new IllegalArgumentException("Unknown known player: " + input));
            PunishmentCase active = ("unmute".equals(kind) ? punishments.activeMute(target) : punishments.activeBan(target))
                .orElseThrow(() -> new IllegalArgumentException("That player has no active " + ("unmute".equals(kind) ? "mute." : "ban.")));
            return active.id();
        }
    }

    private void history(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: /" + kind + " <player> [page]");
        }
        PlayerRecord target = players.resolveKnown(args[0]).orElseThrow(() -> new IllegalArgumentException("Unknown known player: " + args[0]));
        int page = CommandUtil.page(args, 1);
        sender.sendMessage("History for " + target.lastName() + " page " + page + ":");
        for (PunishmentCase punishment : punishments.history(target, config.historyPageSize(), page)) {
            sender.sendMessage("#" + punishment.id() + " " + punishment.type() + " by " + punishment.issuerName() + " reason=" + punishment.reason() + (punishment.revoked() ? " [REVOKED]" : ""));
        }
    }

    private void showCase(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: /case <id>");
        }
        PunishmentCase punishment = punishments.get(Long.parseLong(args[0])).orElseThrow(() -> new IllegalArgumentException("Unknown case."));
        sender.sendMessage("#" + punishment.id() + " " + punishment.type() + " target=" + punishment.targetName() + " issuer=" + punishment.issuerName() + " reason=" + punishment.reason());
        if (punishment.expiresAt() != null) {
            sender.sendMessage("Expires: " + punishment.expiresAt().atZone(ZoneId.systemDefault()));
        }
    }
}

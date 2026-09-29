package dev.turtleroles.command;

import dev.turtleroles.config.TurtleConfig;
import dev.turtleroles.gui.RoleMenuService;
import dev.turtleroles.policy.Actor;
import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.PresentationService;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RoleCommand implements CommandExecutor, TabCompleter {
    private final RoleService roles;
    private final PlayerRepository players;
    private final PolicyService policy;
    private final RoleMenuService menus;
    private final PresentationService presentation;
    private final TurtleConfig config;

    public RoleCommand(RoleService roles, PlayerRepository players, PolicyService policy, RoleMenuService menus, PresentationService presentation, TurtleConfig config) {
        this.roles = roles;
        this.players = players;
        this.policy = policy;
        this.menus = menus;
        this.presentation = presentation;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    PlayerRecord target = players.findByUuid(player.getUniqueId()).orElseThrow();
                    menus.open(player, target);
                } else {
                    CommandUtil.warn(sender, "Console usage: role set|info|list|bootstrap");
                }
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> help(sender);
                case "list" -> list(sender);
                case "info" -> info(sender, args.length >= 2 ? args[1] : sender.getName());
                case "set" -> set(sender, args);
                case "booster" -> {
                    if(!roles.actor(sender).ownerOverride())throw new IllegalArgumentException("Only Owner or console can change booster benefits.");
                    if(args.length!=3)throw new IllegalArgumentException("/role booster <player> <0|1|2>");
                    var target=players.resolveKnown(args[1]).orElseThrow(()->new IllegalArgumentException("Unknown player"));
                    int tier=Integer.parseInt(args[2]);if(tier<0||tier>2)throw new IllegalArgumentException("Use 0, 1 or 2.");
                    roles.setBooster(target.uuid(),tier);sender.sendMessage("Booster benefits set to "+tier+" for "+target.lastName()+". Primary role unchanged.");
                }
                case "bootstrap" -> bootstrap(sender, args);
                default -> {
                    if (args.length == 1) {
                        info(sender, args[0]);
                    } else {
                        CommandUtil.error(sender, "Unknown role command. Use /role help.");
                    }
                }
            }
        } catch (Exception e) {
            CommandUtil.error(sender, e.getMessage());
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("TurtleRoles: /role, /role info <player>, /role set <player> <role> [reason], /role list. Only Owner or console can change operator ranks. /role booster <player> <0|1|2> changes additional booster benefits.");
    }

    private void list(CommandSender sender) throws SQLException {
        for (PlayerRecord record : players.listPlayers()) {
            sender.sendMessage(record.lastName() + ": " + roleDescription(record));
        }
    }

    private void info(CommandSender sender, String targetName) throws SQLException {
        PlayerRecord target = players.resolveKnown(targetName).orElseThrow(() -> new IllegalArgumentException("Unknown known player: " + targetName));
        sender.sendMessage(target.lastName() + " is " + roleDescription(target) + " (revision " + target.revision() + ")");
    }

    private String roleDescription(PlayerRecord record) {
        Role effective = roles.effectiveRoleOf(record.uuid());
        return effective == Role.OWNER && record.role() != Role.OWNER
            ? "OP (owner-level; stored role " + record.role().label() + ")"
            : record.role().label();
    }

    private void set(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: /role set <player> <role> [reason]");
        }
        PlayerRecord target = players.resolveKnown(args[1]).orElseThrow(() -> new IllegalArgumentException("Unknown known player: " + args[1]));
        Role requested = Role.parse(args[2]).orElseThrow(() -> new IllegalArgumentException("Unknown role: " + args[2]));
        Actor actor = roles.actor(sender);
        PolicyDecision decision = policy.canGrantRole(actor, target.uuid(), roles.effectiveRoleOf(target.uuid()), requested);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        String reason = args.length >= 4 ? CommandUtil.reason(args, 3, config.reasonMaxLength()) : "Role command";
        roles.setRole(target, requested, actor, reason);
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            presentation.refreshPlayer(online);
        }
        presentation.refreshAll();
        CommandUtil.ok(sender, "Set " + target.lastName() + " to " + requested.label() + ".");
    }

    private void bootstrap(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /role bootstrap <known-player-or-uuid>");
        }
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            throw new IllegalArgumentException("Owner bootstrap must be run from local console.");
        }
        PlayerRecord target = players.resolveKnown(args[1]).orElseGet(() -> {
            try {
                UUID uuid = UUID.fromString(args[1]);
                String cachedName = Bukkit.getOfflinePlayer(uuid).getName();
                return players.upsertKnownPlayer(uuid, cachedName == null ? uuid.toString() : cachedName);
            } catch (IllegalArgumentException | SQLException exception) {
                throw new IllegalArgumentException("Unknown player or invalid UUID: " + args[1]);
            }
        });
        players.bootstrapOwner(target, Actor.systemConsole());
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            roles.reconcileOp(online, Role.OWNER);
            presentation.refreshPlayer(online);
        }
        CommandUtil.ok(sender, "Bootstrapped Owner: " + target.lastName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("help", "info", "list", "set", "booster", "bootstrap");
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            return java.util.Arrays.stream(Role.values()).map(Role::id).toList();
        }
        return List.of();
    }

}

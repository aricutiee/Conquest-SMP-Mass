package dev.turtleroles.command;

import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.policy.StaffAction;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Locale;

public final class GameModeCommand implements CommandExecutor {
    private final RoleService roles;
    private final PlayerRepository players;
    private final PolicyService policy;

    public GameModeCommand(RoleService roles, PlayerRepository players, PolicyService policy) {
        this.roles = roles;
        this.players = players;
        this.policy = policy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length < 1) {
                throw new IllegalArgumentException("Usage: /gamemode <mode> [player]");
            }
            GameMode mode = parse(args[0]);
            Player target;
            PlayerRecord targetRecord;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    throw new IllegalArgumentException("Target must be online for gamemode changes.");
                }
                targetRecord = players.findByUuid(target.getUniqueId()).orElseThrow();
            } else if (sender instanceof Player player) {
                target = player;
                targetRecord = players.findByUuid(player.getUniqueId()).orElseThrow();
            } else {
                throw new IllegalArgumentException("Console must specify a player.");
            }
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            PolicyDecision decision = policy.canUseOnTarget(roles.actor(sender), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), StaffAction.GAMEMODE, self);
            if (!decision.allowed()) {
                throw new IllegalArgumentException(decision.message());
            }
            target.setGameMode(mode);
            CommandUtil.ok(sender, "Set " + target.getName() + " to " + mode.name().toLowerCase(Locale.ROOT) + ".");
        } catch (Exception e) {
            CommandUtil.error(sender, e.getMessage());
        }
        return true;
    }

    private GameMode parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> throw new IllegalArgumentException("Unknown gamemode: " + raw);
        };
    }
}

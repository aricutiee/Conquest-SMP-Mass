package dev.turtleroles.command;

import dev.turtleroles.config.TurtleConfig;
import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.policy.StaffAction;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.InventoryInspectService;
import dev.turtleroles.service.PresentationService;
import dev.turtleroles.service.ResourcePackService;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Locale;

public final class TurtleCommand implements CommandExecutor {
    private final Plugin plugin;
    private final RoleService roles;
    private final PlayerRepository players;
    private final PolicyService policy;
    private final InventoryInspectService invsee;
    private final PresentationService presentation;
    private final ResourcePackService packs;
    private TurtleConfig config;

    public TurtleCommand(Plugin plugin, RoleService roles, PlayerRepository players, PolicyService policy, InventoryInspectService invsee, PresentationService presentation, ResourcePackService packs, TurtleConfig config) {
        this.plugin = plugin;
        this.roles = roles;
        this.players = players;
        this.policy = policy;
        this.invsee = invsee;
        this.presentation = presentation;
        this.packs = packs;
        this.config = config;
    }

    public void updateConfig(TurtleConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
                help(sender);
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> reload(sender);
                case "doctor" -> doctor(sender);
                case "invsee" -> invsee(sender, args);
                case "heal" -> simplePlayerAction(sender, args, StaffAction.HEAL);
                case "feed" -> simplePlayerAction(sender, args, StaffAction.FEED);
                case "fly" -> fly(sender, args);
                case "tp" -> teleport(sender, args, false);
                case "tphere" -> teleport(sender, args, true);
                case "clear" -> clear(sender, args);
                case "give" -> give(sender, args);
                case "time" -> time(sender, args);
                case "weather" -> weather(sender, args);
                case "pack" -> pack(sender, args);
                default -> CommandUtil.error(sender, "Unknown /tr subcommand. Use /tr help.");
            }
        } catch (Exception e) {
            CommandUtil.error(sender, e.getMessage());
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("/tr help, reload, doctor, invsee <player>, heal [player], feed [player], fly [player], tp <player>, tphere <player>, clear <player>, give <player> <material> [amount], time <day|night>, weather <clear|rain|thunder>, pack resend");
        sender.sendMessage("Role routes: /role set/info/list. Moderation: /warn /tempmute /mute /tempban /ban /kick /history /case.");
    }

    private void reload(CommandSender sender) {
        PolicyDecision decision = policy.canUseUntargeted(roles.actor(sender), StaffAction.WORLD_CONTROL);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        plugin.reloadConfig();
        config = TurtleConfig.from(plugin.getConfig());
        packs.updateSettings(config.resourcePack());
        CommandUtil.ok(sender, "TurtleRoles configuration reloaded.");
    }

    private void doctor(CommandSender sender) throws SQLException {
        sender.sendMessage("TurtleRoles doctor:");
        sender.sendMessage("- Owners in database: " + players.ownerCount());
        for (Player player : Bukkit.getOnlinePlayers()) {
            Role role = roles.roleOf(player.getUniqueId());
            if (role == Role.OWNER && !player.isOp()) {
                sender.sendMessage("- Stored Owner is missing OP and will be reconciled: " + player.getName());
            } else if (dev.turtleroles.service.StaffAccess.managed(role) && player.isOp()) {
                sender.sendMessage("- Role-managed OP: " + player.getName() + " role=" + role.label() + "; hierarchy restrictions active");
            } else if (role != Role.OWNER && player.isOp()) {
                sender.sendMessage("- OP override active: " + player.getName() + " role=" + role.label() + " treated as owner-level");
            }
        }
        if (config.resourcePack().url().isBlank() || config.resourcePack().sha1().isBlank()) {
            sender.sendMessage("- Resource pack URL/SHA-1 missing: automatic picture badges will use fallback text until configured.");
        } else {
            sender.sendMessage("- Resource pack configured: " + config.resourcePack().url());
        }
        sender.sendMessage("- Storage ready: yes");
    }

    private void invsee(CommandSender sender, String[] args) throws SQLException {
        if (!(sender instanceof Player viewer)) {
            throw new IllegalArgumentException("Only players can open inventory views.");
        }
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /tr invsee <player>");
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new IllegalArgumentException("Target must be online.");
        }
        PlayerRecord targetRecord = players.findByUuid(target.getUniqueId()).orElseThrow();
        invsee.open(viewer, target, targetRecord);
    }

    private void simplePlayerAction(CommandSender sender, String[] args, StaffAction action) throws SQLException {
        Player target = targetOrSelf(sender, args, 1);
        PlayerRecord record = players.findByUuid(target.getUniqueId()).orElseThrow();
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        PolicyDecision decision = policy.canUseOnTarget(roles.actor(sender), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), action, self);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        if (action == StaffAction.HEAL) {
            target.setHealth(target.getMaxHealth());
        } else if (action == StaffAction.FEED) {
            target.setFoodLevel(20);
            target.setSaturation(20);
        }
        CommandUtil.ok(sender, action.name().toLowerCase(Locale.ROOT) + " applied to " + target.getName() + ".");
    }

    private void fly(CommandSender sender, String[] args) throws SQLException {
        Player target = targetOrSelf(sender, args, 1);
        PlayerRecord record = players.findByUuid(target.getUniqueId()).orElseThrow();
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        PolicyDecision decision = policy.canUseOnTarget(roles.actor(sender), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), StaffAction.FLY, self);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        target.setAllowFlight(!target.getAllowFlight());
        CommandUtil.ok(sender, "Flight for " + target.getName() + ": " + target.getAllowFlight());
    }

    private void teleport(CommandSender sender, String[] args, boolean here) throws SQLException {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Teleport utility requires a player sender.");
        }
        if (args.length < 2) {
            throw new IllegalArgumentException(here ? "Usage: /tr tphere <player>" : "Usage: /tr tp <player>");
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new IllegalArgumentException("Target must be online.");
        }
        PlayerRecord targetRecord = players.findByUuid(target.getUniqueId()).orElseThrow();
        PolicyDecision decision = policy.canUseOnTarget(roles.actor(sender), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), StaffAction.TELEPORT, false);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        if (here) {
            target.teleport(player.getLocation());
        } else {
            player.teleport(target.getLocation());
        }
        CommandUtil.ok(sender, "Teleported.");
    }

    private void clear(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /tr clear <player>");
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new IllegalArgumentException("Target must be online.");
        }
        PlayerRecord targetRecord = players.findByUuid(target.getUniqueId()).orElseThrow();
        PolicyDecision decision = policy.canUseOnTarget(roles.actor(sender), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), StaffAction.CLEAR_INVENTORY, false);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        target.getInventory().clear();
        CommandUtil.ok(sender, "Cleared " + target.getName() + "'s inventory.");
    }

    private void give(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: /tr give <player> <material> [amount]");
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new IllegalArgumentException("Target must be online.");
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null || dangerous(material)) {
            throw new IllegalArgumentException("That item is not allowed through protected give.");
        }
        int amount = args.length >= 4 ? Math.min(64, Math.max(1, Integer.parseInt(args[3]))) : 1;
        PlayerRecord targetRecord = players.findByUuid(target.getUniqueId()).orElseThrow();
        PolicyDecision decision = policy.canUseOnTarget(roles.actor(sender), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), StaffAction.GIVE_ITEM, false);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        target.getInventory().addItem(new ItemStack(material, amount));
        CommandUtil.ok(sender, "Gave " + amount + " " + material.name().toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
    }

    private void time(CommandSender sender, String[] args) {
        PolicyDecision decision = policy.canUseUntargeted(roles.actor(sender), StaffAction.WORLD_CONTROL);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Use this in a world as a player.");
        }
        World world = player.getWorld();
        world.setTime(args.length >= 2 && "night".equalsIgnoreCase(args[1]) ? 13000 : 1000);
        CommandUtil.ok(sender, "Updated world time.");
    }

    private void weather(CommandSender sender, String[] args) {
        PolicyDecision decision = policy.canUseUntargeted(roles.actor(sender), StaffAction.WORLD_CONTROL);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Use this in a world as a player.");
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "clear";
        World world = player.getWorld();
        world.setStorm("rain".equals(mode) || "thunder".equals(mode));
        world.setThundering("thunder".equals(mode));
        CommandUtil.ok(sender, "Updated world weather.");
    }

    private void pack(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Only players can request a pack resend.");
        }
        packs.send(player);
        presentation.refreshPlayer(player);
    }

    private Player targetOrSelf(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                throw new IllegalArgumentException("Target must be online.");
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("Console must specify a target.");
    }

    private boolean dangerous(Material material) {
        return switch (material) {
            case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, COMMAND_BLOCK_MINECART, STRUCTURE_BLOCK, JIGSAW, STRUCTURE_VOID, WRITTEN_BOOK, WRITABLE_BOOK -> true;
            default -> false;
        };
    }
}

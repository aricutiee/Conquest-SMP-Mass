package dev.turtleroles.gui;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.PresentationService;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.util.CommandUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class RoleMenuService implements Listener {
    private final Plugin plugin;
    private final RoleService roles;
    private final PlayerRepository players;
    private final PolicyService policy;
    private final PresentationService presentation;

    public RoleMenuService(Plugin plugin, RoleService roles, PlayerRepository players, PolicyService policy, PresentationService presentation) {
        this.plugin = plugin;
        this.roles = roles;
        this.players = players;
        this.policy = policy;
        this.presentation = presentation;
    }

    public void open(Player actorPlayer, PlayerRecord target) {
        Actor actor = roles.actor(actorPlayer);
        Inventory inventory = Bukkit.createInventory(
            new RoleMenuHolder(actorPlayer.getUniqueId(), target.uuid(), target.revision(), null, RoleMenuHolder.Stage.LIST),
            27,
            Component.text("TurtleRoles: " + target.lastName())
        );
        String current = roles.effectiveRoleOf(target.uuid()) == Role.OWNER && target.role() != Role.OWNER
            ? "OP (stored " + target.role().label() + ")"
            : target.role().label();
        inventory.setItem(4, item(Material.PLAYER_HEAD, "Current: " + current, List.of("Player: " + target.lastName())));
        int slot = 10;
        for (Role role : Role.values()) {
            PolicyDecision decision = policy.canGrantRole(actor, target.uuid(), roles.effectiveRoleOf(target.uuid()), role);
            Material material = decision.allowed() ? materialFor(role) : Material.BARRIER;
            inventory.setItem(slot++, item(material, role.label(), List.of(decision.allowed() ? "Click to assign" : decision.message())));
        }
        actorPlayer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RoleMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND || event.isShiftClick()) {
            return;
        }
        if (!player.getUniqueId().equals(holder.actorUuid())) {
            player.closeInventory();
            return;
        }
        try {
            PlayerRecord target = players.findByUuid(holder.targetUuid()).orElse(null);
            if (target == null) {
                player.closeInventory();
                CommandUtil.error(player, "That known player record no longer exists.");
                return;
            }
            if (holder.stage() == RoleMenuHolder.Stage.LIST) {
                int index = event.getRawSlot() - 10;
                if (index < 0 || index >= Role.values().length) {
                    return;
                }
                Role requested = Role.values()[index];
                openConfirm(player, target, requested);
            } else if (holder.stage() == RoleMenuHolder.Stage.CONFIRM && event.getRawSlot() == 13) {
                if (target.revision() != holder.targetRevision()) {
                    player.closeInventory();
                    CommandUtil.error(player, "That role view is stale. Reopen /role and try again.");
                    return;
                }
                Actor actor = roles.actor(player);
                PolicyDecision decision = policy.canGrantRole(actor, target.uuid(), roles.effectiveRoleOf(target.uuid()), holder.requestedRole());
                if (!decision.allowed()) {
                    player.closeInventory();
                    CommandUtil.error(player, decision.message());
                    return;
                }
                roles.setRole(target, holder.requestedRole(), actor, "Changed through role GUI");
                Player online = Bukkit.getPlayer(target.uuid());
                if (online != null) {
                    presentation.refreshPlayer(online);
                }
                presentation.refreshAll();
                player.closeInventory();
                CommandUtil.ok(player, "Set " + target.lastName() + " to " + holder.requestedRole().label() + ".");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Role menu error: " + e.getMessage());
            CommandUtil.error(player, "Storage is unavailable.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RoleMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void openConfirm(Player actor, PlayerRecord target, Role requested) {
        Inventory inventory = Bukkit.createInventory(
            new RoleMenuHolder(actor.getUniqueId(), target.uuid(), target.revision(), requested, RoleMenuHolder.Stage.CONFIRM),
            27,
            Component.text("Confirm role change")
        );
        inventory.setItem(13, item(materialFor(requested), "Confirm " + requested.label(), List.of("Target: " + target.lastName(), "Current: " + target.role().label())));
        actor.openInventory(inventory);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY));
        }
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private Material materialFor(Role role) {
        return switch (role) {
            case OWNER, SSER -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case CO_OWNER -> Material.GREEN_STAINED_GLASS_PANE;
            case SR_ADMIN -> Material.BLACK_STAINED_GLASS_PANE;
            case ADMIN -> Material.RED_STAINED_GLASS_PANE;
            case MODERATOR -> Material.LIME_STAINED_GLASS_PANE;
            case HELPER -> Material.YELLOW_STAINED_GLASS_PANE;
            case MEDIA -> Material.PINK_STAINED_GLASS_PANE;
            case BOOSTER, BOOSTER_X2 -> Material.PURPLE_STAINED_GLASS_PANE;
            case MEMBER -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }
}

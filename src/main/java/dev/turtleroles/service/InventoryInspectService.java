package dev.turtleroles.service;

import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.policy.StaffAction;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.util.CommandUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryInspectService implements Listener {
    private final Plugin plugin;
    private final RoleService roles;
    private final PolicyService policy;
    private final int refreshTicks;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private BukkitTask task;

    public InventoryInspectService(Plugin plugin, RoleService roles, PolicyService policy, int refreshTicks) {
        this.plugin = plugin;
        this.roles = roles;
        this.policy = policy;
        this.refreshTicks = refreshTicks;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, refreshTicks, refreshTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        HandlerList.unregisterAll(this);
    }

    public void open(Player viewer, Player target, PlayerRecord targetRecord) {
        PolicyDecision decision = policy.canUseOnTarget(roles.actor(viewer), target.getUniqueId(), roles.effectiveRoleOf(target.getUniqueId()), StaffAction.INVSEE, false);
        if (!decision.allowed()) {
            CommandUtil.error(viewer, decision.message());
            return;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(viewer.getUniqueId(), target.getUniqueId()), 54, Component.text("Read-only invsee: " + target.getName()));
        Session session = new Session(viewer.getUniqueId(), target.getUniqueId(), inventory);
        sessions.put(viewer.getUniqueId(), session);
        fill(session);
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID quitting = event.getPlayer().getUniqueId();
        sessions.values().removeIf(session -> session.viewer.equals(quitting) || session.target.equals(quitting));
    }

    private void refresh() {
        sessions.values().removeIf(session -> {
            Player viewer = Bukkit.getPlayer(session.viewer);
            Player target = Bukkit.getPlayer(session.target);
            if (viewer == null || target == null || viewer.getOpenInventory().getType() == InventoryType.CRAFTING) {
                return true;
            }
            fill(session);
            return false;
        });
    }

    private void fill(Session session) {
        Player target = Bukkit.getPlayer(session.target);
        if (target == null) {
            return;
        }
        session.inventory.clear();
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 41); i++) {
            session.inventory.setItem(i, contents[i] == null ? null : contents[i].clone());
        }
        session.inventory.setItem(45, target.getInventory().getHelmet());
        session.inventory.setItem(46, target.getInventory().getChestplate());
        session.inventory.setItem(47, target.getInventory().getLeggings());
        session.inventory.setItem(48, target.getInventory().getBoots());
        session.inventory.setItem(49, target.getInventory().getItemInOffHand());
    }

    private record Session(UUID viewer, UUID target, Inventory inventory) {
    }

    private record Holder(UUID viewer, UUID target) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}

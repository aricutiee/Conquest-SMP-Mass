package me.jade.ariServerUtil.staffmode;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.vanish.VanishService;
import org.bukkit.Server;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffModeToolsTest {
    StaffModeService service;
    Player player;
    final List<Runnable> pending = new ArrayList<>();
    final List<StaffTool> opened = new ArrayList<>();
    Set<UUID> active;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getName()).thenReturn("TurtleRoles");
        when(plugin.namespace()).thenReturn("turtleroles");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(call -> { pending.add(call.getArgument(1)); return null; }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        ServerUtilConfig config = mock(ServerUtilConfig.class);
        when(config.message(anyString())).thenReturn("<red>No permission");
        service = new StaffModeService(plugin, config, mock(Database.class), mock(AuditService.class), mock(VanishService.class));
        // Simulate a previously enabled staff session without modifying a real inventory/database.
        var field = StaffModeService.class.getDeclaredField("active");
        field.setAccessible(true);
        active = (Set<UUID>) field.get(service);
        active.add(player.getUniqueId());
        service.setToolHandler((viewer, tool) -> opened.add(tool));
    }

    private PlayerInteractEvent use(StaffTool tool, EquipmentSlot hand) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(tool.name());
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(hand);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(item);
        when(event.isCancelled()).thenReturn(true); // Bukkit commonly pre-cancels air use.
        return event;
    }

    @Test void everyTaggedToolOpensItsOwnMenuOnTheNextTick() {
        for (StaffTool tool : StaffTool.values()) {
            var event = use(tool, EquipmentSlot.HAND);
            service.onUse(event);
            verify(event).setCancelled(true);
        }
        assertTrue(opened.isEmpty());
        pending.forEach(Runnable::run);
        assertEquals(List.of(StaffTool.values()), opened);
    }

    @Test void offhandAndInactivePlayersCannotUseTools() {
        service.onUse(use(StaffTool.FREEZE, EquipmentSlot.OFF_HAND));
        active.clear();
        service.onUse(use(StaffTool.FREEZE, EquipmentSlot.HAND));
        assertTrue(pending.isEmpty());
    }

    @Test void revokedPermissionCannotOpenQueuedToolButExitStillWorks() {
        service.onUse(use(StaffTool.PLAYERS, EquipmentSlot.HAND));
        service.onUse(use(StaffTool.EXIT, EquipmentSlot.HAND));
        when(player.hasPermission(anyString())).thenReturn(false);
        pending.forEach(Runnable::run);
        assertEquals(List.of(StaffTool.EXIT), opened);
    }
}

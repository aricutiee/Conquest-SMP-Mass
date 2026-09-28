package me.jade.ariServerUtil.gui;

import me.jade.ariServerUtil.freeze.FreezeService;
import me.jade.ariServerUtil.staffmode.*;
import me.jade.ariServerUtil.reports.ReportService;
import me.jade.ariServerUtil.model.ReportStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffToolMenusTest {
    @Test void freezeAndInspectorPickAPlayerThenOpenTheMatchingScreen() throws Exception {
        try (Fixture f = new Fixture()) {
            f.controller.openStaffTool(f.staff, StaffTool.FREEZE);
            assertEquals("staff-targets", f.lastKey);
            f.click.accept(new GuiClick(null, 0));
            assertEquals("staff-freeze", f.lastKey);
            verify(f.freeze, never()).toggle(any(), any(), anyString());
            f.click.accept(new GuiClick(null, 13));
            verify(f.freeze).toggle(f.staff, f.target, "Staff Mode freeze tool");
            f.controller.openStaffTool(f.staff, StaffTool.INVENTORY);
            f.click.accept(new GuiClick(null, 0));
            assertEquals("staff-inspect", f.lastKey);
            f.click.accept(new GuiClick(null, 0));
            verify(f.targetInventory, never()).setContents(any());
        }
    }

    @Test void exitNeedsConfirmationAndReportsUseReportBrowser() throws Exception {
        try (Fixture f = new Fixture()) {
            f.controller.openStaffTool(f.staff, StaffTool.EXIT);
            assertEquals("confirm", f.lastKey);
            verify(f.mode, never()).disable(any());
            f.click.accept(new GuiClick(null, 11));
            verify(f.mode).disable(f.staff);
            f.controller.openStaffTool(f.staff, StaffTool.REPORTS);
            assertEquals("reports", f.lastKey);
            verify(f.reports).reports(ReportStatus.OPEN);
        }
    }

    private static class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final Player staff = mock(Player.class), target = mock(Player.class);
        final PlayerInventory targetInventory = mock(PlayerInventory.class);
        final GuiManager gui = mock(GuiManager.class);
        final FreezeService freeze = mock(FreezeService.class);
        final StaffModeService mode = mock(StaffModeService.class);
        final ReportService reports = mock(ReportService.class);
        final GuiController controller;
        String lastKey;
        Consumer<GuiClick> click;

        Fixture() throws Exception {
            when(staff.hasPermission(anyString())).thenReturn(true);
            when(staff.canSee(target)).thenReturn(true);
            when(target.getUniqueId()).thenReturn(UUID.randomUUID());
            when(target.getName()).thenReturn("Target");
            when(target.isOnline()).thenReturn(true);
            when(target.getInventory()).thenReturn(targetInventory);
            when(targetInventory.getContents()).thenReturn(new ItemStack[41]);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(target));
            bukkit.when(() -> Bukkit.getPlayer(target.getUniqueId())).thenReturn(target);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; }).when(scheduler).runTask(any(), any(Runnable.class));
            when(reports.reports(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
            when(gui.create(eq(staff), anyString(), anyString(), anyInt(), any())).thenAnswer(call -> {
                lastKey = call.getArgument(1); click = call.getArgument(4); return mock(Inventory.class);
            });
            var constructor = GuiController.class.getConstructors()[0];
            Map<Class<?>, Object> dependencies = Map.of(GuiManager.class, gui, FreezeService.class, freeze, StaffModeService.class, mode, ReportService.class, reports);
            Object[] args = Arrays.stream(constructor.getParameterTypes()).map(type -> dependencies.containsKey(type) ? dependencies.get(type) : mock(type)).toArray();
            controller = (GuiController) constructor.newInstance(args);
        }
        public void close() { bukkit.close(); }
    }
}

package dev.turtleroles.service;

import dev.turtleroles.config.TurtleConfig.ResourcePackSettings;
import dev.turtleroles.pack.PackStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourcePackDeliveryTest {
    @Test void bedrockUsesTextFallbackWithoutJavaPackOrTimeoutKick() {
        Plugin plugin=mock(Plugin.class);PresentationService presentation=mock(PresentationService.class);Player player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        try(var compatibility=mockStatic(ClientCompatibility.class)) {
            compatibility.when(()->ClientCompatibility.bedrock(player)).thenReturn(true);
            ResourcePackService packs=new ResourcePackService(plugin,presentation,settings(true));packs.sendOnJoin(player);
            assertEquals(PackStatus.NOT_SENT,packs.state(player).status());
            verify(player,never()).addResourcePack(any(),anyString(),any(),anyString(),anyBoolean());
            verify(player,never()).kick(any(net.kyori.adventure.text.Component.class));
        }
    }
    private final UUID packId = UUID.randomUUID();
    private ResourcePackSettings settings(boolean required) {
        return new ResourcePackSettings(true, true, required, "https://example.org/pack.zip",
            "0123456789abcdef0123456789abcdef01234567", packId, 45, 1, "Conquest SMP", false, 9076);
    }

    @Test void joinStacksPackAndIgnoresOtherPackStatusesUntilOurLoadSucceeds() {
        Plugin plugin = mock(Plugin.class);
        PresentationService presentation = mock(PresentationService.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask timeout = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(timeout);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            ResourcePackService packs = new ResourcePackService(plugin, presentation, settings(false));
            packs.sendOnJoin(player);
            verify(player).addResourcePack(eq(packId), eq(settings(false).url()), any(byte[].class), anyString(), eq(false));
            verify(player, never()).setResourcePack(any(UUID.class), anyString(), any(byte[].class), anyString(), anyBoolean());
            assertEquals(PackStatus.PENDING, packs.state(player).status());
            packs.handleStatus(new PlayerResourcePackStatusEvent(player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
            assertEquals(PackStatus.PENDING, packs.state(player).status());
            packs.handleStatus(new PlayerResourcePackStatusEvent(player, packId, PlayerResourcePackStatusEvent.Status.DOWNLOADED));
            assertEquals(PackStatus.PENDING, packs.state(player).status());
            packs.handleStatus(new PlayerResourcePackStatusEvent(player, packId, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
            assertEquals(PackStatus.LOADED, packs.state(player).status());
            verify(timeout).cancel();
            verify(presentation).setPackStatus(player.getUniqueId(), PackStatus.LOADED);
            packs.close();
            verify(player).removeResourcePack(packId);
            assertNull(packs.state(player));
        }
    }

    @Test void reconnectCannotBeTimedOutByOldConnectionAndQuitCleansState() {
        Plugin plugin = mock(Plugin.class);
        PresentationService presentation = mock(PresentationService.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            ResourcePackService packs = new ResourcePackService(plugin, presentation, settings(true));
            packs.sendOnJoin(player);
            UUID previous = packs.state(player).connectionId();
            packs.forgetPlayer(player.getUniqueId());
            packs.sendOnJoin(player);
            assertNotEquals(previous, packs.state(player).connectionId());
            var actions = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(2)).runTaskLater(eq(plugin), actions.capture(), anyLong());
            actions.getAllValues().getFirst().run();
            assertEquals(PackStatus.PENDING, packs.state(player).status());
            verify(player, never()).kick(any(net.kyori.adventure.text.Component.class));
            actions.getAllValues().getLast().run();
            assertEquals(PackStatus.TIMED_OUT, packs.state(player).status());
            verify(player).kick(any(net.kyori.adventure.text.Component.class));
            packs.forgetPlayer(player.getUniqueId());
            assertNull(packs.state(player));
        }
    }

    @Test void optionalDeclinedPackRetainsFallbackWithoutKicking() {
        Plugin plugin = mock(Plugin.class);
        PresentationService presentation = mock(PresentationService.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            ResourcePackService packs = new ResourcePackService(plugin, presentation, settings(false));
            packs.sendOnJoin(player);
            packs.handleStatus(new PlayerResourcePackStatusEvent(player, packId, PlayerResourcePackStatusEvent.Status.DECLINED));
            assertEquals(PackStatus.DECLINED, packs.state(player).status());
            verify(player, never()).kick(any(net.kyori.adventure.text.Component.class));
        }
    }
}

package me.jade.ariServerUtil.vanish;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;

class VanishVisibilityTest {
    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    @Test void nonOpWithSeePermissionIsStillHiddenAndUnlisted() {
        Player viewer = player(), admin = player();
        Plugin plugin = mock(Plugin.class);
        when(viewer.hasPermission("serverutil.vanish.see")).thenReturn(true);
        VanishVisibility.apply(plugin, viewer, admin, true);
        verify(viewer).hidePlayer(plugin, admin);
        verify(viewer).unlistPlayer(admin);
        verify(viewer, never()).listPlayer(admin);
    }

    @Test void losingOpHidesAdminOnNextRefreshAndUnvanishRestoresThem() {
        Player viewer = player(), admin = player();
        Plugin plugin = mock(Plugin.class);
        when(viewer.isOp()).thenReturn(true);
        when(viewer.canSee(admin)).thenReturn(true);
        VanishVisibility.apply(plugin, viewer, admin, true);
        verify(viewer).listPlayer(admin);
        when(viewer.isOp()).thenReturn(false);
        VanishVisibility.apply(plugin, viewer, admin, true);
        verify(viewer).unlistPlayer(admin);
        verify(viewer).hidePlayer(plugin, admin);
        VanishVisibility.apply(plugin, viewer, admin, false);
        verify(viewer, times(2)).listPlayer(admin);
    }

    @Test void anotherPluginsHideIsNotOverriddenAndSelfIsLeftAlone() {
        Player viewer = player(), admin = player();
        Plugin plugin = mock(Plugin.class);
        when(viewer.isOp()).thenReturn(true);
        when(viewer.canSee(admin)).thenReturn(false);
        VanishVisibility.apply(plugin, viewer, admin, true);
        verify(viewer, never()).listPlayer(admin);
        VanishVisibility.apply(plugin, admin, admin, true);
        verify(admin, never()).hidePlayer(any(), any());
    }
}

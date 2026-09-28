package dev.turtleroles;

import dev.turtleroles.role.Role;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.service.StaffAccess;
import dev.turtleroles.storage.PlayerRepository;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;

class StaffPermissionLifecycleTest {
    @Test void managedStaffGetOperatorAccessRevokedOnLogout() {
        for (Role role : new Role[]{Role.SSER, Role.ADMIN, Role.SR_ADMIN, Role.CO_OWNER}) {
            Plugin plugin = mock(Plugin.class);
            Player player = mock(Player.class);
            PermissionAttachment attachment = mock(PermissionAttachment.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOp()).thenReturn(false);
            when(player.addAttachment(plugin)).thenReturn(attachment);
            RoleService roles = new RoleService(plugin, mock(PlayerRepository.class));
            roles.reconcileOp(player, role);
            verify(player).setOp(true);
            for (String node : StaffAccess.permissions(role)) verify(attachment).setPermission(node, true);
            roles.removePermissions(player);
            roles.removePermissions(player);
            verify(player, times(1)).removeAttachment(attachment);
        }
    }

    @Test void demotionRemovesPermissionsAndClosesStaleMenu() {
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class);
        PermissionAttachment attachment = mock(PermissionAttachment.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.addAttachment(plugin)).thenReturn(attachment);
        RoleService roles = new RoleService(plugin, mock(PlayerRepository.class));
        roles.reconcileOp(player, Role.ADMIN);
        roles.reconcileOp(player, Role.MEMBER);
        verify(player).removeAttachment(attachment);
        verify(player).closeInventory();
        verify(player, times(1)).addAttachment(plugin);
        verify(player).setOp(true);
        verify(player).setOp(false);
    }
}

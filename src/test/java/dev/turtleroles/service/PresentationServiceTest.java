package dev.turtleroles.service;

import dev.turtleroles.pack.PackStatus;
import dev.turtleroles.role.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresentationServiceTest {
    @Test void tabSortsAllRolesAndNamesAndUpdatesAfterRoleChanges() {
        RoleService roles = mock(RoleService.class);
        Player owner = player("ZOwner", Role.OWNER, roles);
        Player coOwner = player("CoOwner", Role.CO_OWNER, roles);
        Player senior = player("Senior", Role.SR_ADMIN, roles);
        Player admin = player("Admin", Role.ADMIN, roles);
        Player moderator = player("Moderator", Role.MODERATOR, roles);
        Player helper = player("Helper", Role.HELPER, roles);
        Player memberA = player("alpha", Role.MEMBER, roles);
        Player memberZ = player("Zebra", Role.MEMBER, roles);
        List<Player> expected = List.of(owner, coOwner, senior, admin, moderator, helper, memberA, memberZ);
        List<Player> shuffled = List.of(memberZ, helper, admin, memberA, owner, senior, moderator, coOwner);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(shuffled);
            PresentationService service = new PresentationService(roles);
            service.refreshAll();
            for (int i = 0; i < expected.size(); i++) {
                verify(expected.get(i)).setPlayerListOrder(expected.size() - i);
                when(expected.get(i).getPlayerListOrder()).thenReturn(expected.size() - i);
            }
            expected.forEach(org.mockito.Mockito::clearInvocations);
            service.refreshAll();
            for (Player player : expected) verify(player, never()).setPlayerListOrder(anyInt());

            // Promotion or operator override takes effect on the next refresh.
            when(roles.effectiveRoleOf(memberA.getUniqueId())).thenReturn(Role.OWNER);
            service.refreshAll();
            verify(memberA).setPlayerListOrder(8);
            verify(owner).setPlayerListOrder(7);
            verify(memberA, never()).setScoreboard(any());
            verify(owner, never()).setGlowing(anyBoolean());
        }
    }

    private static Player player(String name, Role role, RoleService roles) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(roles.effectiveRoleOf(id)).thenReturn(role);
        return player;
    }

    @Test void joiningOrFailedClientsNeverDowngradeSharedTabBadges() {
        RoleService roles = mock(RoleService.class);
        Player existing = mock(Player.class);
        Player joining = mock(Player.class);
        UUID existingId = UUID.randomUUID();
        UUID joiningId = UUID.randomUUID();
        when(existing.getUniqueId()).thenReturn(existingId);
        when(joining.getUniqueId()).thenReturn(joiningId);
        when(existing.getName()).thenReturn("Existing");
        when(joining.getName()).thenReturn("Joining");
        when(roles.effectiveRoleOf(any())).thenReturn(Role.ADMIN);
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(existing, joining));
            PresentationService service = new PresentationService(roles);
            service.setPackStatus(existingId, PackStatus.LOADED);
            Component expected = service.displayNameFor(existing, true);
            Component joiningExpected = service.displayNameFor(joining, true);
            for (PackStatus status : PackStatus.values()) {
                service.setPackStatus(joiningId, status);
                service.refreshAll();
                assertTrue(service.canUseGlyphFor(existing));
                assertEquals(status == PackStatus.LOADED, service.canUseGlyphFor(joining));
            }
            verify(existing, times(PackStatus.values().length)).playerListName(expected);
            verify(joining, times(PackStatus.values().length)).playerListName(joiningExpected);
            assertNotEquals(service.displayNameFor(existing, false), expected);
        }
    }
}

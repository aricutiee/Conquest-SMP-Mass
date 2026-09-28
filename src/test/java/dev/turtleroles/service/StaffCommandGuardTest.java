package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffCommandGuardTest {
    ServerMock server;
    @BeforeEach void start(){server=MockBukkit.mock();}
    @AfterEach void stop(){MockBukkit.unmock();}
    @Test void directNamespacedCommandsCannotAffectHigherStaffButLowerTargetsWork() {
        var plugin=mock(TurtleRolesPlugin.class);when(plugin.getServer()).thenReturn(server);
        var roles=mock(RoleService.class);var admin=server.addPlayer("AdminOne");var owner=server.addPlayer("OwnerOne");var member=server.addPlayer("MemberOne");
        when(roles.roleOf(admin.getUniqueId())).thenReturn(Role.ADMIN);
        when(roles.effectiveRoleOf(owner.getUniqueId())).thenReturn(Role.OWNER);
        when(roles.effectiveRoleOf(member.getUniqueId())).thenReturn(Role.MEMBER);
        var guard=new StaffCommandGuard(plugin,roles);
        for(String command:new String[]{"/kill OwnerOne","/minecraft:kill OwnerOne","/ss freeze OwnerOne","/tp OwnerOne AdminOne","/op AdminOne","/kill @a"}) {
            var event=new PlayerCommandPreprocessEvent(admin,command);guard.command(event);assertTrue(event.isCancelled(),command);
        }
        for(String command:new String[]{"/kill MemberOne","/ss freeze MemberOne","/gamemode creative AdminOne"}) {
            var event=new PlayerCommandPreprocessEvent(admin,command);guard.command(event);assertFalse(event.isCancelled(),command);
        }
        when(roles.roleOf(admin.getUniqueId())).thenReturn(Role.OWNER);
        var event=new PlayerCommandPreprocessEvent(admin,"/op MemberOne");guard.command(event);assertFalse(event.isCancelled());
    }
}

package dev.turtleroles.service;
import dev.turtleroles.role.Role;
import dev.turtleroles.TurtleRolesPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerTrollModeTest {
    @Test void operatorStatusCannotReplaceStoredOwnerRank(){
        var server=MockBukkit.mock();
        try{
            var plugin=mock(TurtleRolesPlugin.class);var roles=mock(RoleService.class);
            when(plugin.roleService()).thenReturn(roles);
            var player=server.addPlayer("Staff");player.setOp(true);
            var mode=new OwnerTrollMode(plugin);
            for(Role role:Role.values()){
                assertEquals(role==Role.OWNER,OwnerTrollMode.eligible(role));
                when(roles.roleOf(player.getUniqueId())).thenReturn(role);
                if(role!=Role.OWNER){
                    mode.onCommand(player,null,"troll",new String[]{"on"});
                    assertEquals("Only the Owner rank can use /troll for themselves.",player.nextMessage());
                    assertTrue(mode.onTabComplete(player,null,"troll",new String[]{""}).isEmpty());
                }
            }
            when(roles.roleOf(player.getUniqueId())).thenReturn(Role.OWNER);
            mode.onCommand(player,null,"troll",new String[]{"on","SomeoneElse"});
            assertTrue(player.nextMessage().startsWith("Usage:"));
            mode.onCommand(player,null,"troll",new String[]{"on"});
            assertEquals("GrimAC is unavailable; troll mode was not enabled.",player.nextMessage());
        }finally{MockBukkit.unmock();}
    }
}

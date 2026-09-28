package dev.turtleroles.survival;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.combat.ConquestCombat;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.nio.file.Path;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalTest {
    @TempDir Path temp;
    ServerMock server; TurtleRolesPlugin plugin; SurvivalModule module; PlayerMock player; RoleService roles;
    @BeforeEach void setup(){
        server=MockBukkit.mock();server.addSimpleWorld("world_terralith");player=server.addPlayer();
        plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        roles=mock(RoleService.class);when(plugin.roleService()).thenReturn(roles);
        when(roles.effectiveRoleOf(player.getUniqueId())).thenReturn(Role.MEMBER);
        when(plugin.combat()).thenReturn(mock(ConquestCombat.class));module=new SurvivalModule(plugin);
    }
    @AfterEach void close(){MockBukkit.unmock();}
    void command(String name,String...args){Command cmd=mock(Command.class);when(cmd.getName()).thenReturn(name);module.onCommand(player,cmd,name,args);}
    @Test void ranksReceiveFiveSixAndTenWithoutMediaStaff(){
        assertEquals(5,SurvivalModule.homeLimit(Role.MEMBER));assertEquals(6,SurvivalModule.homeLimit(Role.MEDIA));
        assertEquals(6,SurvivalModule.homeLimit(Role.HELPER));assertEquals(10,SurvivalModule.homeLimit(Role.ADMIN));
        assertEquals(10,SurvivalModule.homeLimit(Role.OWNER));assertFalse(Role.MEDIA.isStaff());
        assertTrue(Role.MEDIA.outranks(Role.MEMBER));assertTrue(Role.HELPER.outranks(Role.MEDIA));
    }
    @Test void homesPersistAndLimitDoesNotPreventUpdatingAnExistingHome(){
        for(int i=0;i<6;i++)command("sethome","h"+i);
        var section=module.data.getConfigurationSection("homes."+player.getUniqueId());assertEquals(5,section.getKeys(false).size());
        command("sethome","h0");assertEquals(5,section.getKeys(false).size());
        SurvivalModule reload=new SurvivalModule(plugin);assertNotNull(reload.data.getLocation("homes."+player.getUniqueId()+".h0"));
        command("delhome","h1");command("sethome","h5");assertNotNull(module.data.getLocation("homes."+player.getUniqueId()+".h5"));
    }
    @Test void invalidHomeNamesCannotCreateNestedSettings(){command("sethome","../../spawn");assertNull(module.data.getConfigurationSection("homes"));}
    @Test void combatPreventsHomeMutation(){when(plugin.combat().tagged(player)).thenReturn(true);command("sethome","test");assertNull(module.data.getConfigurationSection("homes"));}
    @Test void spawnPointMovesOnlyForAdminAndDoesNotCreateProtection(){
        Location original=module.spawn().clone();command("setworldspawn");assertEquals(original,module.spawn());
        when(roles.effectiveRoleOf(player.getUniqueId())).thenReturn(Role.ADMIN);player.teleport(new Location(original.getWorld(),200,80,100));
        command("setworldspawn");assertEquals(200,module.spawn().getBlockX());
        SpawnProtection protection=new SpawnProtection(module);
        var event=new BlockBreakEvent(original.getWorld().getBlockAt(200,80,100),player);protection.breakBlock(event);assertFalse(event.isCancelled());
        assertNull(module.spawnArea().region());
    }
    @Test void validBedsKeepPriorityWhileOrdinaryRespawnUsesCustomSpawn(){
        Location bed=new Location(player.getWorld(),150,70,150);
        PlayerRespawnEvent fromBed=mock(PlayerRespawnEvent.class);when(fromBed.getRespawnLocation()).thenReturn(bed);when(fromBed.isBedSpawn()).thenReturn(true);
        module.respawn(fromBed);verify(fromBed,never()).setRespawnLocation(any());
        PlayerRespawnEvent ordinary=mock(PlayerRespawnEvent.class);when(ordinary.getRespawnLocation()).thenReturn(bed);module.respawn(ordinary);verify(ordinary).setRespawnLocation(module.spawn());
    }
    @Test void disabledDimensionsRejectAccessAndRespawns(){
        World nether=mock(World.class);when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        assertTrue(module.allowed(nether));module.data.set("dimensions.nether",false);assertFalse(module.allowed(nether));
        PlayerRespawnEvent event=mock(PlayerRespawnEvent.class);when(event.getRespawnLocation()).thenReturn(new Location(nether,0,70,0));when(event.isAnchorSpawn()).thenReturn(true);
        module.respawn(event);verify(event).setRespawnLocation(module.spawn());
    }
    @Test void playtimeChangesUnitsWithoutYears(){
        assertEquals("59s",SurvivalSidebar.duration(59));assertEquals("1m 0s",SurvivalSidebar.duration(60));
        assertEquals("1h 0m",SurvivalSidebar.duration(3600));assertEquals("1d 0h",SurvivalSidebar.duration(86400));
        assertEquals("400d 0h",SurvivalSidebar.duration(400L*86400));
    }
}

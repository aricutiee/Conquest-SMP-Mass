package dev.turtleroles.survival;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.combat.ConquestCombat;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
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
    @Test void newPlayersUseExactSpawnWithLevelViewAndReturningPlayersKeepLocation(){
        Location spawn=new Location(player.getWorld(),125.25,80,201.75,73,85);module.data.set("spawn",spawn);
        Player newcomer=mock(Player.class);
        var event=new org.spigotmc.event.player.PlayerSpawnLocationEvent(newcomer,player.getLocation());
        module.firstSpawn(event);assertEquals(125.25,event.getSpawnLocation().getX());assertEquals(73,event.getSpawnLocation().getYaw());assertEquals(0,event.getSpawnLocation().getPitch());
        assertEquals(85,module.data.getLocation("spawn").getPitch());
        when(newcomer.hasPlayedBefore()).thenReturn(true);Location saved=player.getLocation();
        event=new org.spigotmc.event.player.PlayerSpawnLocationEvent(newcomer,saved);module.firstSpawn(event);assertEquals(saved,event.getSpawnLocation());
    }
    @Test void liveRankChangesAndExtraBoosterAdjustHomeCommandsWithoutDeletingHomes(){
        command("sethome","one");command("sethome","two");command("sethome","three");
        String path="homes."+player.getUniqueId();assertEquals(2,module.data.getConfigurationSection(path).getKeys(false).size());
        when(roles.effectiveRoleOf(player.getUniqueId())).thenReturn(Role.IRON);
        for(int i=0;i<8;i++)command("sethome","iron"+i);
        assertEquals(6,module.data.getConfigurationSection(path).getKeys(false).size());
        when(roles.boosterTier(player.getUniqueId())).thenReturn(2);command("sethome","extra");
        assertEquals(7,module.data.getConfigurationSection(path).getKeys(false).size());
        when(roles.effectiveRoleOf(player.getUniqueId())).thenReturn(Role.MEMBER);when(roles.boosterTier(player.getUniqueId())).thenReturn(0);
        command("sethome","blocked");assertEquals(7,module.data.getConfigurationSection(path).getKeys(false).size());
        assertNull(module.data.getLocation(path+".blocked"));command("sethome","one");assertNotNull(module.data.getLocation(path+".one"));
    }
    @Test void teleportRankBenefitsMatchAfkAndStaffBypass(){
        Role[] ranks={Role.MEMBER,Role.COAL,Role.IRON,Role.REDSTONE,Role.DIAMOND,Role.NETHERITE,Role.BOOSTER,Role.BOOSTER_X2};
        long[] waits={30000,25000,20000,15000,10000,5000,20000,10000};
        for(int i=0;i<ranks.length;i++)assertEquals(waits[i],SurvivalModule.teleportWait(ranks[i],0));
        assertEquals(10000,SurvivalModule.teleportWait(Role.IRON,2));assertEquals(5000,SurvivalModule.teleportWait(Role.NETHERITE,2));
        assertEquals(0,SurvivalModule.teleportWait(Role.ADMIN,0));
    }
    @Test void allThreeCommandsUseRankWaitAndShareOnePendingTeleport(){
        command("sethome","test");while(player.nextMessage()!=null){}
        command("home","test");assertTrue(player.nextMessage().contains("30 seconds"));
        command("spawn");assertTrue(player.nextMessage().contains("already pending"));
        command("rtp");assertTrue(player.nextMessage().contains("already pending"));
        module.close();module=new SurvivalModule(plugin);
        when(roles.effectiveRoleOf(player.getUniqueId())).thenReturn(Role.COAL);
        command("spawn");assertTrue(player.nextMessage().contains("25 seconds"));
        module.close();module=new SurvivalModule(plugin);
        when(roles.boosterTier(player.getUniqueId())).thenReturn(2);
        command("rtp");assertTrue(player.nextMessage().contains("10 seconds"));
    }
    @Test void ranksReceiveFiveSixAndTenWithoutMediaStaff(){
        assertEquals(2,SurvivalModule.homeLimit(Role.MEMBER));assertEquals(6,SurvivalModule.homeLimit(Role.MEDIA));
        assertEquals(6,SurvivalModule.homeLimit(Role.HELPER));assertEquals(10,SurvivalModule.homeLimit(Role.ADMIN));
        assertEquals(10,SurvivalModule.homeLimit(Role.OWNER));assertFalse(Role.MEDIA.isStaff());
        assertTrue(Role.MEDIA.outranks(Role.MEMBER));assertTrue(Role.HELPER.outranks(Role.MEDIA));
    }
    @Test void homesPersistAndLimitDoesNotPreventUpdatingAnExistingHome(){
        for(int i=0;i<6;i++)command("sethome","h"+i);
        var section=module.data.getConfigurationSection("homes."+player.getUniqueId());assertEquals(2,section.getKeys(false).size());
        command("sethome","h0");assertEquals(2,section.getKeys(false).size());
        SurvivalModule reload=new SurvivalModule(plugin);assertNotNull(reload.data.getLocation("homes."+player.getUniqueId()+".h0"));
        command("delhome","h1");command("sethome","h5");assertNotNull(module.data.getLocation("homes."+player.getUniqueId()+".h5"));
    }
    @Test void newRanksAndCombinedBoosterUseHighestHomeLimit(){
        Role[] ranks={Role.COAL,Role.IRON,Role.REDSTONE,Role.DIAMOND,Role.NETHERITE,Role.BOOSTER,Role.BOOSTER_X2};
        int[] limits={4,6,8,13,23,4,7};for(int i=0;i<ranks.length;i++)assertEquals(limits[i],SurvivalModule.homeLimit(ranks[i]));
        when(roles.effectiveRoleOf(player.getUniqueId())).thenReturn(Role.DIAMOND);when(roles.boosterTier(player.getUniqueId())).thenReturn(2);
        for(int i=0;i<15;i++)command("sethome","d"+i);
        assertEquals(13,module.data.getConfigurationSection("homes."+player.getUniqueId()).getKeys(false).size());
    }
    @Test void spawnBlocksChestsButtonsAndDoorsButStaffCanInteract(){
        var w=player.getWorld();module.data.set("spawn-area.first",new Location(w,0,0,0));module.data.set("spawn-area.second",new Location(w,10,0,10));
        var protection=new SpawnProtection(module);
        for(Material material:new Material[]{Material.CHEST,Material.STONE_BUTTON,Material.OAK_DOOR}){
            var b=w.getBlockAt(5,70,5);b.setType(material);
            var e=new org.bukkit.event.player.PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,b,org.bukkit.block.BlockFace.UP);
            protection.interact(e);assertEquals(org.bukkit.event.Event.Result.DENY,e.useInteractedBlock());
        }
        when(roles.roleOf(player.getUniqueId())).thenReturn(Role.ADMIN);
        var e=new org.bukkit.event.player.PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,w.getBlockAt(5,70,5),org.bukkit.block.BlockFace.UP);
        protection.interact(e);assertNotEquals(org.bukkit.event.Event.Result.DENY,e.useInteractedBlock());
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

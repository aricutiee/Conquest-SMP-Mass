package dev.turtleroles.survival;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.combat.ConquestCombat;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpawnBarrierTest {
    @TempDir Path temp;ServerMock server;World world;TurtleRolesPlugin plugin;SurvivalModule module;SpawnBarrier barrier;Player player;ConquestCombat combat;RoleService roles;
    @BeforeEach void setup(){
        server=MockBukkit.mock();world=server.addSimpleWorld("world_terralith");world.getChunkAt(0,1).load();world.getChunkAt(1,1).load();
        plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(temp.toFile());when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(server);when(plugin.getName()).thenReturn("ConquestSMP");when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        roles=mock(RoleService.class);when(plugin.roleService()).thenReturn(roles);combat=mock(ConquestCombat.class);when(plugin.combat()).thenReturn(combat);
        module=new SurvivalModule(plugin);module.data.set("spawn-area.first",new Location(world,10,60,20));module.data.set("spawn-area.second",new Location(world,19,80,39));
        barrier=new SpawnBarrier(plugin,module);player=mock(Player.class);when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenReturn(new Location(world,9.7,70,25));when(player.isOnline()).thenReturn(true);when(player.getVelocity()).thenReturn(new Vector(4,0,0));when(combat.tagged(player)).thenReturn(true);
    }
    @AfterEach void close(){barrier.close();MockBukkit.unmock();}
    @Test void sweptPathBlocksEverySideAndFastFlightAtEveryHeight(){
        var r=module.spawnArea().region();
        for(double y:new double[]{-1000,70,1000}){
            assertTrue(SpawnBarrier.crosses(r,new Location(world,9,y,25),new Location(world,35,y,25)));
            assertTrue(SpawnBarrier.crosses(r,new Location(world,21,y,25),new Location(world,0,y,25)));
            assertTrue(SpawnBarrier.crosses(r,new Location(world,15,y,19),new Location(world,15,y,50)));
            assertTrue(SpawnBarrier.crosses(r,new Location(world,15,y,41),new Location(world,15,y,0)));
            assertTrue(SpawnBarrier.crosses(r,new Location(world,9,y,19),new Location(world,30,y,45)));
        }
    }
    @Test void retreatTangentialTravelAndOtherWorldDoNotBounce(){
        var r=module.spawnArea().region();
        assertFalse(SpawnBarrier.crosses(r,new Location(world,9.7,70,25),new Location(world,8,70,25)));
        assertFalse(SpawnBarrier.crosses(r,new Location(world,9,70,25),new Location(world,9,70,30)));
        assertFalse(SpawnBarrier.crosses(r,new Location(world,11,70,25),new Location(world,8,70,25)));
        assertFalse(SpawnBarrier.crosses(r,new Location(world,9,70,25),new Location(server.addSimpleWorld("other"),15,70,25)));
        assertTrue(SpawnBarrier.crosses(r,new Location(world,9,70,25),new Location(world,9.7,70,25)));
    }
    @Test void previewIsBoundedPrivateAndRestoresCurrentWorldState(){
        barrier.refresh(player,false);
        var data=org.mockito.ArgumentCaptor.forClass(BlockData.class);verify(player,atLeastOnce()).sendBlockChange(any(Location.class),data.capture());assertTrue(data.getAllValues().stream().allMatch(d->d.getMaterial()==Material.RED_STAINED_GLASS));
        var cells=SpawnBarrier.cells(module.spawnArea().region(),player.getLocation(),6);assertFalse(cells.isEmpty());assertTrue(cells.size()<=364);
        for(var c:cells)assertTrue(world.getBlockAt(c.x(),c.y(),c.z()).getType().isAir());
        var first=cells.iterator().next();world.getBlockAt(first.x(),first.y(),first.z()).setType(Material.STONE);
        clearInvocations(player);when(combat.tagged(player)).thenReturn(false);barrier.refresh(player,false);
        var restored=org.mockito.ArgumentCaptor.forClass(BlockData.class);verify(player,atLeastOnce()).sendBlockChange(any(Location.class),restored.capture());assertTrue(restored.getAllValues().stream().anyMatch(d->d.getMaterial()==Material.STONE));assertTrue(restored.getAllValues().stream().noneMatch(d->d.getMaterial()==Material.RED_STAINED_GLASS));
        assertEquals(Material.STONE,world.getBlockAt(first.x(),first.y(),first.z()).getType());
    }
    @Test void previewClearsWhenPromotedOrFarAwayAndNeverSpansIllegalHeights(){
        barrier.refresh(player,false);clearInvocations(player);when(roles.roleOf(player.getUniqueId())).thenReturn(Role.ADMIN);barrier.refresh(player,false);
        verify(player,atLeastOnce()).sendBlockChange(any(Location.class),any(BlockData.class));clearInvocations(player);barrier.refresh(player,false);verify(player,never()).sendBlockChange(any(Location.class),any(BlockData.class));
        assertTrue(SpawnBarrier.cells(module.spawnArea().region(),new Location(world,0,70,0),6).isEmpty());
        assertTrue(SpawnBarrier.cells(module.spawnArea().region(),new Location(world,9,1000,25),6).isEmpty());
    }
    @Test void bounceCancelsInwardMomentumAndUsesNativeKnockbackOnce(){
        barrier.bounce(player,new Vector(-1,0,0));server.getScheduler().performOneTick();
        verify(player).setVelocity(new Vector(0,0,0));verify(player).knockback(1,1,-0.0);
        barrier.bounce(player,new Vector(-1,0,0));server.getScheduler().performOneTick();verify(player,times(1)).knockback(1,1,-0.0);
    }
    @Test void pendingBounceCancelledOnDeathLogoutOrClose(){
        barrier.bounce(player,new Vector(-1,0,0));barrier.forget(player);server.getScheduler().performOneTick();verify(player,never()).knockback(anyDouble(),anyDouble(),anyDouble());
        barrier.bounce(player,new Vector(-1,0,0));when(combat.tagged(player)).thenReturn(false);server.getScheduler().performOneTick();verify(player,never()).knockback(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void retreatAvoidsWallsAndKeepsThePlayerOutside(){
        var collisionWorld=mock(World.class);when(collisionWorld.getUID()).thenReturn(world.getUID());when(collisionWorld.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(collisionWorld.getMinHeight()).thenReturn(-64);when(collisionWorld.getMaxHeight()).thenReturn(320);
        var air=mock(org.bukkit.block.Block.class);when(air.isPassable()).thenReturn(true);var wall=mock(org.bukkit.block.Block.class);when(wall.isPassable()).thenReturn(false);
        var obstructed=new java.util.concurrent.atomic.AtomicBoolean(false);
        when(collisionWorld.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->obstructed.get()&&(int)i.getArgument(0)==9?wall:air);
        var r=module.spawnArea().region();var from=new Location(collisionWorld,9.7,70,25);var normal=new Vector(-1,0,0);
        var retreat=SpawnBarrier.retreat(r,from,normal);assertFalse(r.contains(retreat));assertTrue(SpawnBarrier.distance(r,retreat)>.35);assertTrue(SpawnBarrier.clear(retreat));
        obstructed.set(true);
        retreat=SpawnBarrier.retreat(r,from,normal);assertTrue(SpawnBarrier.clear(retreat));assertFalse(r.contains(retreat));
        when(collisionWorld.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(wall);
        assertEquals(from,SpawnBarrier.retreat(r,from,normal)); // Keep the last position if no checked alternative exists.

    }
}

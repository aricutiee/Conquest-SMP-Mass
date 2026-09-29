package dev.turtleroles.service;
import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class NpcPlacementTest {
 @TempDir Path dir;
 @Test void placementIsReadableImmediatelyAndAfterReloadAndRemoval(){
  var server=MockBukkit.mock();try{
   var world=server.addSimpleWorld("placement");
   var plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
   var player=mock(Player.class);when(player.isOp()).thenReturn(true);
   var point=mock(Location.class);when(point.getWorld()).thenReturn(world);when(point.getX()).thenReturn(17.5);when(point.getY()).thenReturn(83.0);when(point.getZ()).thenReturn(-20.5);when(point.getYaw()).thenReturn(40f);when(player.getLocation()).thenReturn(point);
   // Far, unloaded chunk keeps this a placement-state test, without display mocks.
   when(point.getX()).thenReturn(100000.5);
   var npcs=new SpawnNpcs(plugin);npcs.onCommand(player,null,"npc",new String[]{"set","reroll"});
   var live=npcs.location(SpawnNpcs.Kind.reroll);assertNotNull(live,"Placement must be readable before restarting");assertEquals(100000.5,live.getX());assertEquals(83,live.getY());assertEquals(220,live.getYaw());
   var reloaded=new SpawnNpcs(plugin);assertEquals(live,reloaded.location(SpawnNpcs.Kind.reroll));
   npcs.onCommand(player,null,"npc",new String[]{"remove","reroll"});assertNull(npcs.location(SpawnNpcs.Kind.reroll));assertNull(new SpawnNpcs(plugin).location(SpawnNpcs.Kind.reroll));
  }finally{MockBukkit.unmock();}
 }
}

package dev.turtleroles.service;
import dev.biomeraces.*;
import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ShardRewardsTest {
 @TempDir Path dir;ServerMock server;
 @BeforeEach void setup(){server=MockBukkit.mock();}
 @AfterEach void close(){MockBukkit.unmock();}
 @Test void boundedAreaAwardsOncePerThreeSecondsAndStopsOutside()throws Exception{
  var world=server.addSimpleWorld("afk");var p=server.addPlayer();p.teleport(new Location(world,2,66,2));
  var yaml=new YamlConfiguration();yaml.set("enabled",true);yaml.set("world",world.getUID().toString());for(String axis:new String[]{"x","z"}){yaml.set("min-"+axis,0);yaml.set("max-"+axis,4);}yaml.set("min-y",65);yaml.set("max-y",67);yaml.save(dir.resolve("afk-zone.yml").toFile());
  var plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());var races=mock(RaceModule.class);when(plugin.races()).thenReturn(races);var store=new PlayerStore(dir.resolve("race.yml"));when(races.store()).thenReturn(store);
  var rewards=new ShardRewards(plugin);assertTrue(rewards.inside(p.getLocation()));assertFalse(rewards.inside(new Location(world,2,68,2)));
  rewards.tick();rewards.tick();assertEquals(0,store.get(p.getUniqueId()).shards);rewards.tick();assertEquals(1,store.get(p.getUniqueId()).shards);
  rewards.tick();rewards.tick();rewards.tick();assertEquals(2,store.get(p.getUniqueId()).shards);
  p.teleport(new Location(world,8,66,2));for(int i=0;i<6;i++)rewards.tick();assertEquals(2,store.get(p.getUniqueId()).shards);
 }
}

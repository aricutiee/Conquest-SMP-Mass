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
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2}) void boundedAreaAwardsAtRankIntervalAndResetsSessionOutside(int tier)throws Exception{
  var world=server.addSimpleWorld("afk");var p=server.addPlayer();p.teleport(new Location(world,2,66,2));
  var yaml=new YamlConfiguration();yaml.set("enabled",true);yaml.set("world",world.getUID().toString());for(String axis:new String[]{"x","z"}){yaml.set("min-"+axis,0);yaml.set("max-"+axis,4);}yaml.set("min-y",65);yaml.set("max-y",67);yaml.save(dir.resolve("afk-zone.yml").toFile());
  var plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());var races=mock(RaceModule.class);when(plugin.races()).thenReturn(races);var store=new PlayerStore(dir.resolve("race.yml"));when(races.store()).thenReturn(store);
  var roles=mock(RoleService.class);when(plugin.roleService()).thenReturn(roles);when(roles.boosterTier(p.getUniqueId())).thenReturn(tier);long interval=ShardRewards.interval(tier);
  var clock=new java.util.concurrent.atomic.AtomicLong();var rewards=new ShardRewards(plugin,clock::get);assertTrue(rewards.inside(p.getLocation()));assertFalse(rewards.inside(new Location(world,2,68,2)));
  rewards.tick();assertTrue(rewards.active(p.getUniqueId()));clock.set(interval-1);rewards.tick();assertEquals(0,store.get(p.getUniqueId()).shards);
  clock.set(interval);rewards.tick();assertEquals(1,store.get(p.getUniqueId()).shards);assertEquals(1,rewards.earned(p.getUniqueId()));
  clock.set(interval*2);rewards.tick();assertEquals(2,store.get(p.getUniqueId()).shards);assertEquals(2,rewards.earned(p.getUniqueId()));
  p.teleport(new Location(world,8,66,2));rewards.tick();assertFalse(rewards.active(p.getUniqueId()));assertEquals(0,rewards.earned(p.getUniqueId()));
  p.teleport(new Location(world,2,66,2));rewards.tick();clock.set(interval*3-1);rewards.tick();assertEquals(2,store.get(p.getUniqueId()).shards);
  clock.set(interval*3);rewards.tick();assertEquals(3,store.get(p.getUniqueId()).shards);assertEquals(1,rewards.earned(p.getUniqueId()));
  clock.set(interval*9);rewards.tick();assertEquals(4,store.get(p.getUniqueId()).shards,"Lag must not grant catch-up rewards");rewards.close();assertFalse(rewards.active(p.getUniqueId()));

 }
}

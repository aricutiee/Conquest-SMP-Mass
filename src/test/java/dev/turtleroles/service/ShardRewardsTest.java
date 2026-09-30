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
 @Test void everyOreRankUsesRequestedRateAndFasterBoosterWins(){
  var ranks=new dev.turtleroles.role.Role[]{dev.turtleroles.role.Role.MEMBER,dev.turtleroles.role.Role.COAL,dev.turtleroles.role.Role.IRON,dev.turtleroles.role.Role.REDSTONE,dev.turtleroles.role.Role.DIAMOND,dev.turtleroles.role.Role.NETHERITE};
  long[] expected={30000,25000,20000,15000,10000,5000};
  for(int i=0;i<ranks.length;i++){assertEquals(expected[i],ShardRewards.interval(ranks[i],0));assertEquals(Math.min(expected[i],20000),ShardRewards.interval(ranks[i],1));assertEquals(Math.min(expected[i],10000),ShardRewards.interval(ranks[i],2));}
  assertEquals(15000,ShardRewards.reschedule(10000,30000,30000,15000));
  assertEquals(10000,ShardRewards.reschedule(10000,30000,30000,5000));
 }
 @Test void operatorCanSetAndGiveAllButMemberCannot()throws Exception{
  var op=server.addPlayer("Operator");op.setOp(true);var member=server.addPlayer("Member");
  var plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());
  var races=mock(RaceModule.class);when(plugin.races()).thenReturn(races);var store=new PlayerStore(dir.resolve("race.yml"));when(races.store()).thenReturn(store);
  var roles=mock(RoleService.class);when(plugin.roleService()).thenReturn(roles);when(roles.roleOf(member.getUniqueId())).thenReturn(dev.turtleroles.role.Role.MEMBER);
  var rewards=new ShardRewards(plugin);var command=mock(org.bukkit.command.Command.class);when(command.getName()).thenReturn("shards");
  rewards.onCommand(member,command,"shards",new String[]{"set","Member","999"});assertEquals(0,store.get(member.getUniqueId()).shards);
  rewards.onCommand(op,command,"shards",new String[]{"set","Member","75"});assertEquals(75,store.get(member.getUniqueId()).shards);
  rewards.onCommand(op,command,"shards",new String[]{"giveall","25"});assertEquals(100,store.get(member.getUniqueId()).shards);assertEquals(25,store.get(op.getUniqueId()).shards);
  rewards.onCommand(op,command,"shards",new String[]{"give","all","5"});assertEquals(105,new PlayerStore(dir.resolve("race.yml")).get(member.getUniqueId()).shards);
  rewards.onCommand(op,command,"shards",new String[]{"set","Member","-1"});assertEquals(105,store.get(member.getUniqueId()).shards);
 }
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

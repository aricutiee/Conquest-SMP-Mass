package dev.biomeraces;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ShardStoreTest {
 @TempDir Path dir;
 @Test void setBalancePersistsZeroAndRejectsNegative()throws Exception {
  Path f=dir.resolve("set.yml");var store=new PlayerStore(f);UUID id=UUID.randomUUID();
  assertTrue(store.setShards(id,1000));assertEquals(1000,new PlayerStore(f).get(id).shards);
  assertFalse(store.setShards(id,-1));assertEquals(1000,store.get(id).shards);
  assertTrue(store.setShards(id,0));assertEquals(0,new PlayerStore(f).get(id).shards);
 }
 @Test void failedSetRollsBack()throws Exception {
  var store=new PlayerStore(dir.resolve("blocked-set/p.yml"));UUID id=UUID.randomUUID();store.get(id).shards=42;
  Files.writeString(dir.resolve("blocked-set"),"file");assertThrows(java.io.IOException.class,()->store.setShards(id,99));assertEquals(42,store.get(id).shards);
 }
 @Test void paidRerollDebitsOnceAndSurvivesReconnectWithoutCooldownReset()throws Exception{
  var file=dir.resolve("players.yml");var store=new PlayerStore(file);UUID id=UUID.randomUUID();var s=store.get(id);s.base=Race.BOGBORN;s.shards=500;s.offenseUntil=123;s.defenseUntil=456;
  assertTrue(store.purchaseReroll(id,Race.DWARF,250));assertFalse(store.purchaseReroll(id,Race.DWARF,250));
  var read=new PlayerStore(file).get(id);assertNull(read.base);assertEquals(Race.DWARF,read.pendingRoll);assertEquals(250,read.shards);assertEquals(123,read.offenseUntil);assertEquals(456,read.defenseUntil);
 }
 @Test void rejectsInsufficientFundsAndDragonborn()throws Exception{var store=new PlayerStore(dir.resolve("p.yml"));UUID id=UUID.randomUUID();store.get(id).base=Race.BOGBORN;store.get(id).shards=249;assertFalse(store.purchaseReroll(id,Race.DWARF,250));store.get(id).shards=500;assertFalse(store.purchaseReroll(id,Race.DRAGONBORN,250));assertEquals(500,store.get(id).shards);}
 @Test void failedSaveRollsBackRaceAndMoney()throws Exception{var store=new PlayerStore(dir.resolve("blocked/p.yml"));Files.writeString(dir.resolve("blocked"),"file");UUID id=UUID.randomUUID();var s=store.get(id);s.base=Race.PETALFOLK;s.shards=250;assertThrows(java.io.IOException.class,()->store.purchaseReroll(id,Race.DWARF,250));assertEquals(Race.PETALFOLK,s.base);assertNull(s.pendingRoll);assertEquals(250,s.shards);}
 @Test void threeRewardedKillsPerPairWindowPersistsAndExpires()throws Exception{Path file=dir.resolve("p.yml");var store=new PlayerStore(file);UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();long now=System.currentTimeMillis();assertFalse(store.rewardKill(a,a,now));for(int i=0;i<3;i++)assertTrue(store.rewardKill(a,b,now+i));assertFalse(store.rewardKill(a,b,now+10));store=new PlayerStore(file);assertFalse(store.rewardKill(a,b,now+100));assertTrue(store.rewardKill(a,c,now+100));assertEquals(100,store.get(a).shards);assertTrue(store.rewardKill(a,b,now+72L*60*60*1000));assertEquals(125,store.get(a).shards);}
 @Test void batchCreditsAndPurchasesPersistAndCannotOverdraw()throws Exception{Path file=dir.resolve("p.yml");var store=new PlayerStore(file);UUID a=UUID.randomUUID(),b=UUID.randomUUID();store.creditBatch(Map.of(a,2L,b,3L));assertFalse(store.spend(a,3));assertTrue(store.spend(a,2));assertEquals(0,new PlayerStore(file).get(a).shards);assertEquals(3,new PlayerStore(file).get(b).shards);assertFalse(store.addShards(b,Long.MAX_VALUE));assertEquals(3,store.get(b).shards);}
 @Test void legacyRaceDataGetsBackupAndZeroStartingShards()throws Exception{Path file=dir.resolve("p.yml");UUID id=UUID.randomUUID();String old="players:\n  "+id+":\n    race: dwarf\n    offense-until: 42\n";Files.writeString(file,old);var store=new PlayerStore(file);assertEquals(0,store.get(id).shards);assertEquals(Race.DWARF,store.get(id).base);assertEquals(old,Files.readString(dir.resolve("p.yml.pre-shards")));}
}

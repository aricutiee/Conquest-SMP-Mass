package dev.turtleroles.survival;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
class LeaderboardsTest {
    Leaderboards.Stats stats(String name,long completed,long active){return new Leaderboards.Stats(UUID.nameUUIDFromBytes(name.getBytes()),name,10,5,completed,3600,active);}
    @Test void completed50AndActive30BothRankThenOnly50Remains(){
        var live=Leaderboards.rankStreaks(List.of(stats("Ari",50,30)));
        assertEquals(2,live.size());assertEquals(50,live.getFirst().value());assertFalse(live.getFirst().active());assertTrue(live.get(1).active());
        var broken=Leaderboards.rankStreaks(List.of(stats("Ari",50,0)));assertEquals(1,broken.size());assertEquals(50,broken.getFirst().value());
    }
    @Test void ranksTopTenAndUsesStableTieBreaks(){
        var entries=new ArrayList<Leaderboards.Stats>();for(int i=0;i<20;i++)entries.add(stats("Player"+i,i+1,0));
        var top=Leaderboards.rankStreaks(entries);assertEquals(10,top.size());assertEquals(20,top.getFirst().value());assertEquals(11,top.getLast().value());
        assertEquals("Alpha",Leaderboards.rank(List.of(stats("Beta",1,0),stats("Alpha",1,0)),Leaderboards.Metric.kills).getFirst().name());
    }
    @Test void completedRecordOnlyIncreasesOnDeath(){
        var server=MockBukkit.mock();try{
            var p=server.addPlayer();var current=new NamespacedKey("conquestsmp","kill_streak");
            p.getPersistentDataContainer().set(current,PersistentDataType.INTEGER,50);Leaderboards.endStreak(p);
            p.getPersistentDataContainer().set(current,PersistentDataType.INTEGER,30);Leaderboards.endStreak(p);
            assertEquals(50,p.getPersistentDataContainer().get(Leaderboards.BROKEN,PersistentDataType.INTEGER));
            p.getPersistentDataContainer().set(current,PersistentDataType.INTEGER,60);Leaderboards.endStreak(p);
            assertEquals(60,p.getPersistentDataContainer().get(Leaderboards.BROKEN,PersistentDataType.INTEGER));
        }finally{MockBukkit.unmock();}
    }
    @Test void milestonesAreEveryTenIncludingBeyond50(){for(int i=0;i<102;i++)assertEquals(i>0&&i%10==0,Leaderboards.milestone(i));}
    @Test void greyRecordsPurpleActiveAndPersonalStatsDiffer(){
        var a=stats("Ari",50,30);var b=stats("Bea",20,10);
        assertEquals(0x96919C,Leaderboards.streakRow(0,new Leaderboards.StreakEntry(a,50,false)).color().value());
        assertEquals(0xB477FF,Leaderboards.streakRow(1,new Leaderboards.StreakEntry(a,30,true)).color().value());
        assertNotEquals(Leaderboards.personal(a,Leaderboards.Metric.streaks),Leaderboards.personal(b,Leaderboards.Metric.streaks));
        assertEquals("1h 0m",Leaderboards.value(Leaderboards.Metric.playtime,3600));assertNull(Leaderboards.parse("invalid"));
    }
}

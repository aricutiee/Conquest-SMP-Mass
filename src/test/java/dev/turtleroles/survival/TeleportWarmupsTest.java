package dev.turtleroles.survival;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class TeleportWarmupsTest {
    ServerMock server;PlayerMock player;TeleportWarmups warmups;
    AtomicLong now=new AtomicLong(),duration=new AtomicLong(30000);AtomicBoolean combat=new AtomicBoolean();AtomicInteger calls=new AtomicInteger();
    @BeforeEach void setup(){server=MockBukkit.mock();server.addSimpleWorld("world_terralith");player=server.addPlayer();warmups=new TeleportWarmups(p->duration.get(),p->combat.get(),now::get);}
    @AfterEach void close(){warmups.close();MockBukkit.unmock();}
    @Test void waitsThenDispatchesOnceAndBlocksConcurrentRequests(){
        warmups.start(player,"Home",r->calls.incrementAndGet());warmups.start(player,"Spawn",r->calls.incrementAndGet());
        now.set(29999);warmups.tick();assertEquals(0,calls.get());now.set(30000);warmups.tick();warmups.tick();assertEquals(1,calls.get());
    }
    @Test void rankChangesRecalculateFromOriginalStart(){
        warmups.start(player,"Spawn",r->{calls.incrementAndGet();r.finish();});now.set(11000);duration.set(10000);warmups.tick();assertEquals(1,calls.get());
        duration.set(5000);warmups.start(player,"Home",r->{calls.incrementAndGet();r.finish();});duration.set(30000);now.set(20000);warmups.tick();assertEquals(1,calls.get());now.set(41000);warmups.tick();assertEquals(2,calls.get());
    }
    @Test void turningAllowedButWalkingCancels(){
        warmups.start(player,"Home",r->calls.incrementAndGet());var origin=player.getLocation();var turn=origin.clone();turn.setYaw(80);turn.setPitch(45);
        warmups.move(new PlayerMoveEvent(player,origin,turn));now.set(5000);warmups.tick();assertEquals(0,calls.get());
        warmups.move(new PlayerMoveEvent(player,origin,origin.clone().add(1,0,0)));now.set(40000);warmups.tick();assertEquals(0,calls.get());
    }
    @Test void combatAndDamageCancelPendingTeleports(){
        warmups.start(player,"Spawn",r->calls.incrementAndGet());combat.set(true);now.set(30000);warmups.tick();assertEquals(0,calls.get());combat.set(false);
        warmups.start(player,"Spawn",r->calls.incrementAndGet());var e=org.mockito.Mockito.mock(EntityDamageEvent.class);org.mockito.Mockito.when(e.getEntity()).thenReturn(player);org.mockito.Mockito.when(e.getFinalDamage()).thenReturn(1.0);warmups.damage(e);now.set(60000);warmups.tick();assertEquals(0,calls.get());
    }
    @Test void asyncTicketInvalidAfterQuitAndNewRequest(){
        duration.set(0);AtomicReference<TeleportWarmups.Request> old=new AtomicReference<>();warmups.start(player,"RTP",old::set);assertTrue(old.get().active());
        warmups.cancel(player,null);warmups.start(player,"Spawn",r->calls.incrementAndGet());assertFalse(old.get().active());old.get().finish();assertEquals(1,calls.get());
    }
}

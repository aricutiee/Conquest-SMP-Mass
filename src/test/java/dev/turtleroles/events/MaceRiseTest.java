package dev.turtleroles.events;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaceRiseTest {
    @Test void risesFiveSecondsShakesTwoSecondsAndReleasesOnlyOnce(){
        var server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();
        World world=mock(World.class);Chunk chunk=mock(Chunk.class);ItemDisplay display=mock(ItemDisplay.class);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        when(world.spawn(any(Location.class),eq(ItemDisplay.class),any(java.util.function.Consumer.class))).thenAnswer(i->{((java.util.function.Consumer<ItemDisplay>)i.getArgument(2)).accept(display);return display;});
        var releases=new AtomicInteger();var rise=new MaceRise(plugin,new Location(world,0,64,0),new ItemStack(Material.MACE),releases::incrementAndGet);
        try{
            assertEquals(1,MaceRise.height(0));assertEquals(6,MaceRise.height(100));assertFalse(MaceRise.shaking(99));assertTrue(MaceRise.shaking(100));assertTrue(MaceRise.shaking(139));
            rise.start();server.getScheduler().performTicks(139);assertEquals(0,releases.get());server.getScheduler().performTicks(1);assertEquals(1,releases.get());rise.close();assertEquals(1,releases.get());
            verify(display).remove();verify(chunk).removePluginChunkTicket(plugin);
        }finally{rise.close();MockBukkit.unmock();}
    }
}

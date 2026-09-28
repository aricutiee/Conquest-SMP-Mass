package dev.turtleroles.survival;
import org.bukkit.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeathChestsTest {
    ServerMock server;JavaPlugin host;DeathChests chests;AtomicLong clock=new AtomicLong(1000);
    @BeforeEach void setup(){server=MockBukkit.mock();host=MockBukkit.createMockPlugin();chests=new DeathChests(host,clock::get);}
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void snapshotHas54SlotsAndClonesItems(){
        ItemStack stack=new ItemStack(Material.DIAMOND,5);var snapshot=DeathChests.readItems(List.of(stack));stack.setAmount(1);
        assertEquals(54,snapshot.length);assertEquals(5,snapshot[0].getAmount());assertNull(snapshot[53]);
    }
    @Test void deathStoresDropsInProtectedChestAndRestoresAfterRestart(){
        var player=server.addPlayer();var event=mock(PlayerDeathEvent.class);when(event.getEntity()).thenReturn(player);
        List<ItemStack> drops=new ArrayList<>(List.of(new ItemStack(Material.DIAMOND,5)));when(event.getDrops()).thenReturn(drops);
        chests.death(event);assertTrue(drops.isEmpty());
        var data=YamlConfiguration.loadConfiguration(new File(host.getDataFolder(),"death-chests.yml"));assertEquals(1,data.getKeys(false).size());
        String id=data.getKeys(false).iterator().next();Location location=data.getLocation(id+".location");assertNotNull(location);assertEquals(Material.CHEST,location.getBlock().getType());
        var restored=new DeathChests(host,clock::get);var broken=new BlockBreakEvent(location.getBlock(),server.addPlayer());restored.breakBlock(broken);assertTrue(broken.isCancelled());
        assertEquals(5,((org.bukkit.block.Chest)location.getBlock().getState()).getBlockInventory().getItem(0).getAmount());
    }
    @Test void keepInventoryDoesNotCreateDuplicateChest(){
        var event=mock(PlayerDeathEvent.class);when(event.getKeepInventory()).thenReturn(true);chests.death(event);
        verify(event,never()).getDrops();
    }
    @Test void emptyChestDisappearsAndExpiryReleasesRemainingItems(){
        var player=server.addPlayer();var event=mock(PlayerDeathEvent.class);when(event.getEntity()).thenReturn(player);
        List<ItemStack> drops=new ArrayList<>(List.of(new ItemStack(Material.DIAMOND,5)));when(event.getDrops()).thenReturn(drops);chests.death(event);
        File file=new File(host.getDataFolder(),"death-chests.yml");var data=YamlConfiguration.loadConfiguration(file);String id=data.getKeys(false).iterator().next();Location loc=data.getLocation(id+".location");
        loc.getWorld().loadChunk(loc.getBlockX()>>4,loc.getBlockZ()>>4);
        clock.addAndGet(599_999);chests.tick();assertEquals(Material.CHEST,loc.getBlock().getType());
        clock.incrementAndGet();chests.tick();assertEquals(Material.AIR,loc.getBlock().getType());assertTrue(YamlConfiguration.loadConfiguration(file).getKeys(false).isEmpty());
        assertTrue(player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).stream().anyMatch(item->item.getItemStack().getType()==Material.DIAMOND && item.getItemStack().getAmount()==5));
        drops.add(new ItemStack(Material.GOLD_INGOT));chests.death(event);data=YamlConfiguration.loadConfiguration(file);id=data.getKeys(false).iterator().next();loc=data.getLocation(id+".location");
        ((org.bukkit.block.Chest)loc.getBlock().getState()).getBlockInventory().clear();chests.tick();assertEquals(Material.AIR,loc.getBlock().getType());
    }
}

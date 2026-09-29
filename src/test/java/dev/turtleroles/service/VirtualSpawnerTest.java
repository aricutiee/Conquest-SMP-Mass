package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VirtualSpawnerTest {
    @TempDir Path dir;
    @Test void requestedCooldownsAndBestBenefitAreExact(){
        assertEquals(60,RankStringCommand.seconds(Role.MEMBER,0));assertEquals(50,RankStringCommand.seconds(Role.COAL,0));
        assertEquals(45,RankStringCommand.seconds(Role.IRON,0));assertEquals(40,RankStringCommand.seconds(Role.REDSTONE,0));
        assertEquals(30,RankStringCommand.seconds(Role.DIAMOND,0));assertEquals(15,RankStringCommand.seconds(Role.NETHERITE,0));
        assertEquals(50,RankStringCommand.seconds(Role.MEMBER,1));assertEquals(30,RankStringCommand.seconds(Role.COAL,2));
        assertEquals(15,RankStringCommand.seconds(Role.NETHERITE,2));assertEquals(0,RankStringCommand.seconds(Role.ADMIN,0));
    }
    @Test void lootTablesProduceBoundedLootAndXpFor500AndRejectInvalidStacks(){
        for(SpawnerKind kind:SpawnerKind.values()){
            assertThrows(IllegalArgumentException.class,()->kind.roll(501,new Random(1)));
            for(int seed=0;seed<20;seed++){
                var batch=kind.roll(500,new Random(seed));
                assertTrue(batch.xp()>=kind.xpMin*500&&batch.xp()<=kind.xpMax*500);
                for(var entry:batch.items().entrySet())assertTrue(entry.getValue()>0&&entry.getValue()<=3000);
                assertFalse(kind.lootLore().isEmpty());
            }
        }
        assertEquals(1000,SpawnerKind.CREEPER.price);assertEquals(250,SpawnerKind.CHICKEN.price);
        assertTrue(SpawnerKind.VILLAGER.roll(1,new Random(1)).items().containsKey(Material.EMERALD));
    }
    @Test void capacityCheckIsAllOrNothingAndDoesNotMutateInput(){
        MockBukkit.mock();try{
            ItemStack[] full=new ItemStack[54];Arrays.fill(full,new ItemStack(Material.STONE,64));
            assertNull(VirtualSpawners.fit(full,Map.of(Material.GUNPOWDER,1)));assertEquals(64,full[0].getAmount());
            ItemStack[] free=new ItemStack[54];free[0]=new ItemStack(Material.GUNPOWDER,60);
            var next=VirtualSpawners.fit(free,Map.of(Material.GUNPOWDER,1000));assertNotNull(next);
            assertEquals(60,free[0].getAmount());assertEquals(1060,Arrays.stream(next).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum());
        }finally{MockBukkit.unmock();}
    }
    @Test void naturalSpawnersRemainBreakableAndPurchasedItemsRetainTypeAndStack(){
        var server=MockBukkit.mock();try{
            var plugin=mock(TurtleRolesPlugin.class);when(plugin.getName()).thenReturn("ConquestSMP");when(plugin.namespace()).thenReturn("conquestsmp");when(plugin.getDataFolder()).thenReturn(dir.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var module=new VirtualSpawners(plugin);var world=server.addSimpleWorld("world");var p=server.addPlayer();
            Block b=world.getBlockAt(0,64,0);b.setType(Material.SPAWNER);var e=new BlockBreakEvent(b,p);module.mine(e);assertFalse(e.isCancelled());
            var item=module.item(SpawnerKind.BLAZE,500);assertEquals(1,item.getAmount());
            assertEquals(500,item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"virtual_spawner_count"),org.bukkit.persistence.PersistentDataType.INTEGER));
            assertEquals("BLAZE",item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"virtual_spawner"),org.bukkit.persistence.PersistentDataType.STRING));
            assertTrue(item.getItemMeta().lore().size()>6);
        }finally{MockBukkit.unmock();}
    }
    @Test void boughtSpawnerPersistsAndProducesOnlyAfter120ActiveSeconds() throws Exception {
        var server=MockBukkit.mock();try{
            var host=MockBukkit.createMockPlugin();var plugin=mock(TurtleRolesPlugin.class);
            when(plugin.namespace()).thenReturn("conquestsmp");when(plugin.getName()).thenReturn("ConquestSMP");when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getServer()).thenReturn(server);when(plugin.getPluginLoader()).thenReturn(host.getPluginLoader());
            when(plugin.getDataFolder()).thenReturn(dir.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var module=new VirtualSpawners(plugin);var world=server.addSimpleWorld("world");world.loadChunk(0,0);var player=server.addPlayer();
            var b=world.getBlockAt(0,64,0);b.setType(Material.SPAWNER);
            var event=mock(org.bukkit.event.block.BlockPlaceEvent.class);when(event.getBlock()).thenReturn(b);when(event.getPlayer()).thenReturn(player);when(event.getItemInHand()).thenReturn(module.item(SpawnerKind.VILLAGER,500));
            module.place(event);verify(event,never()).setCancelled(true);
            var tick=VirtualSpawners.class.getDeclaredMethod("tick");tick.setAccessible(true);
            for(int i=0;i<119;i++)tick.invoke(module);
            var before=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dir.resolve("virtual-spawners.yml").toFile());
            String key=world.getUID()+"_0_64_0";assertEquals(0,before.getInt("farms."+key+".xp"));
            tick.invoke(module);
            var after=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dir.resolve("virtual-spawners.yml").toFile());
            assertEquals(2500,after.getInt("farms."+key+".xp"));assertEquals(500,after.getInt("farms."+key+".count"));
            assertTrue(after.getList("farms."+key+".items").stream().anyMatch(ItemStack.class::isInstance));
            var breaker=new BlockBreakEvent(b,player);module.mine(breaker);assertTrue(breaker.isCancelled());assertEquals(Material.SPAWNER,b.getType());
            module.close();
        }finally{MockBukkit.unmock();}
    }
}


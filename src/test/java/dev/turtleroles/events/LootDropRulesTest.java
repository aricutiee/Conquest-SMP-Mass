package dev.turtleroles.events;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LootDropRulesTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void everyChestGuaranteesAllItemsWithinInclusiveBoundsAndOneUnenchantedGear() {
        var config=new YamlConfiguration();var random=new Random(367);
        Map<Material,int[]> ranges=new EnumMap<>(Material.class);
        ranges.put(Material.ENCHANTED_GOLDEN_APPLE,new int[]{1,2});ranges.put(Material.NETHERITE_INGOT,new int[]{1,2});
        ranges.put(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,new int[]{1,1});ranges.put(Material.NETHER_WART,new int[]{8,32});
        ranges.put(Material.GOLDEN_APPLE,new int[]{4,16});ranges.put(Material.EMERALD,new int[]{2,64});ranges.put(Material.DIAMOND,new int[]{16,64});
        Set<Integer> appleAmounts=new HashSet<>(),emeraldAmounts=new HashSet<>();
        for(int iteration=0;iteration<1000;iteration++) {
            var loot=LootDropRules.loot(config,random);assertEquals(8,loot.size());
            for(ItemStack item:loot) {
                int[] range=ranges.get(item.getType());
                if(range!=null){assertTrue(item.getAmount()>=range[0]&&item.getAmount()<=range[1]);}
                else {assertTrue(item.getType().name().startsWith("DIAMOND_"));assertEquals(1,item.getAmount());assertTrue(item.getEnchantments().isEmpty());}
            }
            var scattered=LootDropRules.scatter(loot,random);assertEquals(27,scattered.length);
            assertEquals(totals(loot),totals(Arrays.asList(scattered)));
            assertTrue(Arrays.stream(scattered).filter(Objects::nonNull).allMatch(i->i.getAmount()>0&&i.getAmount()<=i.getMaxStackSize()));
            appleAmounts.add(totals(loot).get(Material.ENCHANTED_GOLDEN_APPLE));emeraldAmounts.add(totals(loot).get(Material.EMERALD));
        }
        assertEquals(Set.of(1,2),appleAmounts);assertTrue(emeraldAmounts.contains(2));assertTrue(emeraldAmounts.contains(64));
    }
    private Map<Material,Integer> totals(List<ItemStack> items) {
        Map<Material,Integer> result=new EnumMap<>(Material.class);
        for(ItemStack item:items)if(item!=null)result.merge(item.getType(),item.getAmount(),Integer::sum);
        return result;
    }
    @Test void coordinatesStayInCircularRingAndCoverAllQuadrants() {
        var random=new Random(41);Set<String> quadrants=new HashSet<>();
        for(int i=0;i<10000;i++) {
            var point=LootDropRules.offset(random,500,1500);
            assertTrue(LootDropRules.inRange(point.x(),point.z(),500,1500));
            quadrants.add((point.x()<0?"-":"+")+(point.z()<0?"-":"+"));
        }
        assertEquals(4,quadrants.size());assertFalse(LootDropRules.inRange(499,0,500,1500));
        assertFalse(LootDropRules.inRange(1500,1500,500,1500));
        assertTrue(LootDropRules.inRange(500,0,500,1500));assertTrue(LootDropRules.inRange(1500,0,500,1500));
    }
    @Test void lootRangesCanBeTunedWithoutInvalidStacks() {
        var config=new YamlConfiguration();config.set("loot.diamonds.min",32);config.set("loot.diamonds.max",32);
        config.set("loot.emeralds.min",-4);config.set("loot.emeralds.max",400);
        for(int i=0;i<50;i++) {
            var totals=totals(LootDropRules.loot(config,new Random(i)));assertEquals(32,totals.get(Material.DIAMOND));
            assertTrue(totals.get(Material.EMERALD)>=1&&totals.get(Material.EMERALD)<=64);
        }
    }
    @Test void surfacesRejectWaterObstructionsLeavesAndAdjacentChests() {
        var world=MockBukkit.getMock().addSimpleWorld("loot");var place=world.getBlockAt(3,70,3);
        var floor=place.getRelative(org.bukkit.block.BlockFace.DOWN);floor.setType(Material.STONE);
        assertTrue(LootDrops.safe(place));
        floor.setType(Material.WATER);assertFalse(LootDrops.safe(place));
        floor.setType(Material.OAK_LEAVES);assertFalse(LootDrops.safe(place));floor.setType(Material.STONE);
        place.setType(Material.DIAMOND_BLOCK);assertFalse(LootDrops.safe(place));place.setType(Material.AIR);
        place.getRelative(org.bukkit.block.BlockFace.UP).setType(Material.STONE);assertFalse(LootDrops.safe(place));
        place.getRelative(org.bukkit.block.BlockFace.UP).setType(Material.AIR);
        place.getRelative(org.bukkit.block.BlockFace.NORTH).setType(Material.CHEST);assertFalse(LootDrops.safe(place));
    }
    @Test void timerPersistsDeadlineManualStartsResetAndOverdueRestartNeverCatchesUpInBurst() {
        long interval=14_400_000,now=10_000;
        var schedule=new LootDropSchedule(now,0,true,interval);
        assertFalse(schedule.due(now+interval-1));assertTrue(schedule.due(now+interval));
        schedule.reserve(now+interval/2);assertEquals(now+interval/2+interval,schedule.next);
        var restored=new LootDropSchedule(now+10*interval,schedule.next,true,interval);
        assertTrue(restored.due(now+10*interval));restored.reserve(now+10*interval);
        assertFalse(restored.due(now+10*interval));
        restored.automatic(false,now);assertFalse(restored.due(Long.MAX_VALUE));
        restored.automatic(true,now);assertEquals(now+interval,restored.next);
    }
}

package dev.turtleroles.service;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class NpcShopTest {
 @BeforeEach void setup(){MockBukkit.mock();}
 @AfterEach void close(){MockBukkit.unmock();}
 @Test void deliveryStacksWithoutMutatingOriginal(){ItemStack[] storage={new ItemStack(Material.DIAMOND,60),null};ItemStack[] next=SpawnNpcs.delivery(storage,new ItemStack(Material.DIAMOND,8));assertNotNull(next);assertEquals(64,next[0].getAmount());assertEquals(4,next[1].getAmount());assertEquals(60,storage[0].getAmount());assertNull(storage[1]);}
 @Test void fullInventoryRejectsWholePurchase(){ItemStack[] storage={new ItemStack(Material.STONE,64)};assertNull(SpawnNpcs.delivery(storage,new ItemStack(Material.DIAMOND)));assertEquals(Material.STONE,storage[0].getType());}
 @Test void guideLinesWrapAtWordBoundaries(){var lines=SpawnNpcs.wrap("The quick brown fox jumps over the lazy dog",16);assertEquals("The quick brown fox jumps over the lazy dog",String.join(" ",lines));assertTrue(lines.stream().allMatch(s->s.length()<=16));}
}

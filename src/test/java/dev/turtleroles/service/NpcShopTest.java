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
 @org.junit.jupiter.api.Test void orbitMovesAroundAnchorWithBoundedBobbing(){
  var anchor=new org.bukkit.Location(null,10,70,-20);var first=SpawnNpcs.orbit(anchor,0,0);var opposite=SpawnNpcs.orbit(anchor,Math.PI,0);
  org.junit.jupiter.api.Assertions.assertEquals(10.85,first.getX(),.0001);org.junit.jupiter.api.Assertions.assertEquals(9.15,opposite.getX(),.0001);
  for(int n=0;n<100;n++){var point=SpawnNpcs.orbit(anchor,n*.1,n*.2);org.junit.jupiter.api.Assertions.assertTrue(point.getY()>=71.35&&point.getY()<=71.65);}
  org.junit.jupiter.api.Assertions.assertEquals(70,anchor.getY());
 }
}

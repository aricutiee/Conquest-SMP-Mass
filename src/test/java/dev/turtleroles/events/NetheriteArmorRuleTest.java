package dev.turtleroles.events;

import org.bukkit.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.event.inventory.*;

class NetheriteArmorRuleTest {
    ServerMock server;PlayerMock player;NetheriteArmorRule rule;boolean designated;
    @BeforeEach void setup(){server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();player=server.addPlayer();rule=new NetheriteArmorRule(plugin,p->designated);}
    @AfterEach void cleanup(){rule.close();MockBukkit.unmock();}
    @Test void ordinaryArmorIsUnequippedWithoutDeletingIt(){
        ItemStack helmet=new ItemStack(Material.NETHERITE_HELMET);player.getInventory().setHelmet(helmet);
        rule.enforce(player);assertNull(player.getInventory().getHelmet());assertTrue(player.getInventory().contains(Material.NETHERITE_HELMET));
    }
    @Test void OnlyActiveBossIsExemptAndLosesExemptionWhenEventEnds(){
        designated=true;player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));rule.enforce(player);
        assertEquals(Material.NETHERITE_CHESTPLATE,player.getInventory().getChestplate().getType());
        designated=false;player.setOp(true);rule.enforce(player);assertNull(player.getInventory().getChestplate());
    }
    @Test void diamondArmorAndNetheriteToolsAreUnaffected(){
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));rule.enforce(player);
        assertEquals(Material.DIAMOND_HELMET,player.getInventory().getHelmet().getType());assertFalse(NetheriteArmorRule.armor(new ItemStack(Material.NETHERITE_SWORD)));
    }
    @Test void rightClickEquipIsDeniedForBothHands(){
        for(EquipmentSlot hand:new EquipmentSlot[]{EquipmentSlot.HAND,EquipmentSlot.OFF_HAND}){
            var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,new ItemStack(Material.NETHERITE_BOOTS),null,org.bukkit.block.BlockFace.SELF,hand);
            rule.use(event);assertEquals(Event.Result.DENY,event.useItemInHand());
        }
    }
    @Test void smithingAttemptIsCancelledEvenWhenPreviewWasCleared(){
        SmithingInventory inventory=mock(SmithingInventory.class);
        when(inventory.getItem(1)).thenReturn(new ItemStack(Material.DIAMOND_CHESTPLATE));
        when(inventory.getItem(2)).thenReturn(new ItemStack(Material.NETHERITE_INGOT));
        InventoryClickEvent event=mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);when(event.getInventory()).thenReturn(inventory);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.RESULT);
        rule.click(event);verify(event).setCancelled(true);
        assertEquals("You are not allowed to craft netherite armor.",org.bukkit.ChatColor.stripColor(player.nextMessage()));
    }
    @Test void activeBossMayTakeSmithingOutput(){
        designated=true;InventoryClickEvent event=mock(InventoryClickEvent.class);when(event.getWhoClicked()).thenReturn(player);
        rule.click(event);verify(event,never()).setCancelled(true);
    }
    @Test void animationIncludesWeaponsPickaxeAndArmor(){
        assertTrue(WarlordAnimation.visibleGear(new ItemStack(Material.NETHERITE_SWORD)));
        assertTrue(WarlordAnimation.visibleGear(new ItemStack(Material.NETHERITE_AXE)));
        assertTrue(WarlordAnimation.visibleGear(new ItemStack(Material.NETHERITE_PICKAXE)));
        assertTrue(WarlordAnimation.visibleGear(new ItemStack(Material.NETHERITE_HELMET)));
    }
}

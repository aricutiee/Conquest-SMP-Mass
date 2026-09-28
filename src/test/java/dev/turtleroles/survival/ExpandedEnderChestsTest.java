package dev.turtleroles.survival;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.combat.ConquestCombat;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpandedEnderChestsTest {
    ServerMock server;
    @BeforeEach void setup(){server=MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}
    private void physical(ExpandedEnderChests ender,org.bukkit.entity.Player player){
        var block=player.getWorld().getBlockAt(0,65,0);block.setType(Material.ENDER_CHEST);
        ender.interact(new org.bukkit.event.player.PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,block,org.bukkit.block.BlockFace.UP,org.bukkit.inventory.EquipmentSlot.HAND));
    }
    @Test void remoteCommandsDisabledOutsideCombat(){
        var player=server.addPlayer();var ender=new ExpandedEnderChests(mock(TurtleRolesPlugin.class));
        for(String cmd:java.util.List.of("/ec","/echest","/enderchest","/essentials:eenderchest")){
            var event=new PlayerCommandPreprocessEvent(player,cmd);ender.command(event);assertTrue(event.isCancelled());
            assertNull(player.getOpenInventory().getTopInventory());
        }
    }
    @Test void vanillaContentsRemainAndExtraSlotsSurviveCloseAndReopen(){
        var player=spy(server.addPlayer());doNothing().when(player).saveData();var plugin=mock(TurtleRolesPlugin.class);when(plugin.combat()).thenReturn(mock(ConquestCombat.class));
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var ender=new ExpandedEnderChests(plugin);player.getEnderChest().setItem(0,new ItemStack(Material.DIAMOND,3));
        physical(ender,player);var inventory=player.getOpenInventory().getTopInventory();assertEquals(54,inventory.getSize());
        assertEquals(3,inventory.getItem(0).getAmount());inventory.setItem(53,new ItemStack(Material.EMERALD,7));
        ender.closeInventory(new InventoryCloseEvent(player.getOpenInventory()));player.closeInventory();
        var restored=new ExpandedEnderChests(plugin);physical(restored,player);
        assertEquals(7,player.getOpenInventory().getTopInventory().getItem(53).getAmount());assertEquals(3,player.getEnderChest().getItem(0).getAmount());
    }
    @Test void combatBlocksCommandsAndPhysicalEnderChestsEvenForOperators(){
        var player=server.addPlayer();player.setOp(true);
        var plugin=mock(TurtleRolesPlugin.class);var combat=mock(ConquestCombat.class);when(plugin.combat()).thenReturn(combat);when(combat.tagged(player)).thenReturn(true);
        var ender=new ExpandedEnderChests(plugin);
        for(String command:java.util.List.of("/ec","/enderchest","/essentials:enderchest","/conquestsmp:enderchest")) {
            var event=new PlayerCommandPreprocessEvent(player,command);ender.command(event);assertTrue(event.isCancelled());
            assertNotNull(player.nextComponentMessage());
        }
        var world=server.addSimpleWorld("combat");var block=world.getBlockAt(0,65,0);block.setType(Material.ENDER_CHEST);
        var event=new org.bukkit.event.player.PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,block,org.bukkit.block.BlockFace.UP,org.bukkit.inventory.EquipmentSlot.HAND);
        ender.interact(event);assertTrue(event.isCancelled());assertNotNull(player.nextComponentMessage());
    }
    @Test void combatOpenGuardLeavesOrdinaryContainersUsable(){
        var player=server.addPlayer();var plugin=mock(TurtleRolesPlugin.class);var combat=mock(ConquestCombat.class);when(plugin.combat()).thenReturn(combat);when(combat.tagged(player)).thenReturn(true);
        var ender=new ExpandedEnderChests(plugin);
        var chest=server.createInventory(null,54);var chestEvent=new org.bukkit.event.inventory.InventoryOpenEvent(player.openInventory(chest));
        ender.opening(chestEvent);assertFalse(chestEvent.isCancelled());
        var enderEvent=new org.bukkit.event.inventory.InventoryOpenEvent(player.openInventory(player.getEnderChest()));
        ender.opening(enderEvent);assertTrue(enderEvent.isCancelled());
    }

    @Test void staffCanOpen54SlotsByCommandDuringCombat(){
        var player=server.addPlayer();var plugin=mock(TurtleRolesPlugin.class);var roles=mock(dev.turtleroles.service.RoleService.class);
        when(plugin.roleService()).thenReturn(roles);when(plugin.combat()).thenReturn(mock(ConquestCombat.class));when(plugin.combat().tagged(player)).thenReturn(true);
        when(roles.roleOf(player.getUniqueId())).thenReturn(dev.turtleroles.role.Role.ADMIN);
        var ender=new ExpandedEnderChests(plugin);ender.command(new PlayerCommandPreprocessEvent(player,"/ec"));
        assertEquals(54,player.getOpenInventory().getTopInventory().getSize());
        var opened=new org.bukkit.event.inventory.InventoryOpenEvent(player.getOpenInventory());ender.opening(opened);assertFalse(opened.isCancelled());
        when(roles.roleOf(player.getUniqueId())).thenReturn(dev.turtleroles.role.Role.SSER);ender.opening(opened);assertTrue(opened.isCancelled());
    }

}

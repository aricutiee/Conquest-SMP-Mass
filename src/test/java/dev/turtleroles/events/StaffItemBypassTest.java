package dev.turtleroles.events;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.service.*;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StaffItemBypassTest {
    ServerMock server;TurtleRolesPlugin plugin;RoleService roles;Player player;
    @BeforeEach void setup(){server=MockBukkit.mock();player=server.addPlayer();plugin=mock(TurtleRolesPlugin.class);roles=mock(RoleService.class);when(plugin.roleService()).thenReturn(roles);}
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void onlyFourNamedRolesHaveBroadBypassEvenWithOperator(){
        player.setOp(true);
        for(Role role:Role.values()){
            when(roles.roleOf(player.getUniqueId())).thenReturn(role);
            assertEquals(java.util.Set.of(Role.ADMIN,Role.SR_ADMIN,Role.CO_OWNER,Role.OWNER).contains(role),GameplayBypass.allowed(plugin,player));
        }
    }
    @Test void adminCanDepositRewardAndKeepEnderRewardButSserCannot(){
        var guard=new EventRewardGuard(plugin);var top=server.createInventory(null,27);var view=player.openInventory(top);
        var click=mock(InventoryClickEvent.class);when(click.getWhoClicked()).thenReturn(player);when(click.getView()).thenReturn(view);
        when(click.getClickedInventory()).thenReturn(top);when(click.getCursor()).thenReturn(new ItemStack(Material.DRAGON_EGG));when(click.getAction()).thenReturn(InventoryAction.PLACE_ALL);when(click.getHotbarButton()).thenReturn(-1);
        when(roles.roleOf(player.getUniqueId())).thenReturn(Role.ADMIN);guard.click(click);verify(click,never()).setCancelled(true);
        player.getEnderChest().setItem(0,new ItemStack(Material.DRAGON_EGG));guard.evict(player,player.getEnderChest());assertNotNull(player.getEnderChest().getItem(0));
        when(roles.roleOf(player.getUniqueId())).thenReturn(Role.SSER);guard.click(click);verify(click).setCancelled(true);guard.evict(player,player.getEnderChest());assertNull(player.getEnderChest().getItem(0));
    }
    @Test void adminCanRenameCustomItemWithoutRemovingItsIdentity(){
        var rule=new CustomItemNames(plugin);var input=new ItemStack(Material.DRAGON_EGG);var output=input.clone();var meta=output.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Test egg"));output.setItemMeta(meta);
        var inv=mock(Inventory.class);when(inv.getType()).thenReturn(InventoryType.ANVIL);when(inv.getItem(0)).thenReturn(input);
        var view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(inv);
        var click=mock(InventoryClickEvent.class);when(click.getWhoClicked()).thenReturn(player);when(click.getView()).thenReturn(view);when(click.getRawSlot()).thenReturn(2);when(click.getCurrentItem()).thenReturn(output);
        when(roles.roleOf(player.getUniqueId())).thenReturn(Role.ADMIN);rule.take(click);verify(click,never()).setCancelled(true);
        when(roles.roleOf(player.getUniqueId())).thenReturn(Role.MEMBER);rule.take(click);verify(click).setCancelled(true);
    }

    @Test void staffCanWearNetheriteThenDemotionRestoresRule(){
        when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(server);when(plugin.getName()).thenReturn("ConquestSMP");
        when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
        try(var rule=new NetheriteArmorRule(plugin,p->false)){
            when(roles.roleOf(player.getUniqueId())).thenReturn(Role.ADMIN);
            player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));rule.enforce(player);assertNotNull(player.getInventory().getHelmet());
            when(roles.roleOf(player.getUniqueId())).thenReturn(Role.SSER);rule.enforce(player);assertNull(player.getInventory().getHelmet());assertTrue(player.getInventory().contains(Material.NETHERITE_HELMET));
        }
    }
}

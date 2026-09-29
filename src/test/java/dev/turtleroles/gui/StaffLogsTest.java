package dev.turtleroles.gui;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffLogsTest {
    ServerMock server;StaffLogs logs;RoleService roles;JavaPlugin plugin;
    @BeforeEach void setup(){
        server=MockBukkit.mock();var host=MockBukkit.createMockPlugin();plugin=mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("StaffLogsTest");when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(server);
        when(plugin.getPluginLoader()).thenReturn(host.getPluginLoader());when(plugin.getDataFolder()).thenReturn(host.getDataFolder());when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getCommand("staff")).thenReturn(mock(PluginCommand.class));roles=mock(RoleService.class);logs=new StaffLogs(plugin,roles);
    }
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void membersCannotOpenLogsAndAllExistingStaffCan(){
        var player=server.addPlayer();when(roles.roleOf(player.getUniqueId())).thenReturn(Role.MEMBER);assertFalse(logs.allowed(player));
        var before=player.getOpenInventory();logs.onCommand(player,null,"staff",new String[]{"logs"});assertSame(before,player.getOpenInventory());
        for(Role role:Role.values()){when(roles.roleOf(player.getUniqueId())).thenReturn(role);assertEquals(role.isStaff(),logs.allowed(player));}
        player.setOp(true);when(roles.roleOf(player.getUniqueId())).thenReturn(Role.MEMBER);assertTrue(logs.allowed(player));
    }
    @Test void clicksAreCancelledEvenAfterViewerLosesStaffRole(){
        var player=server.addPlayer();when(roles.roleOf(player.getUniqueId())).thenReturn(Role.MEMBER);
        var menu=new StaffLogs.Menu(player.getUniqueId());menu.inventory=server.createInventory(menu,54);menu.inventory.setItem(0,new ItemStack(Material.PLAYER_HEAD));player.openInventory(menu.inventory);
        var click=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);logs.click(click);assertTrue(click.isCancelled());
    }
}

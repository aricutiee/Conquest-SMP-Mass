package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.command.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class KitRoutingTest {
    @TempDir Path data;
    @Test void kitRoutesToOwnExecutorWhenEssentialsOwnsBareCommand() throws Exception {
        var server=MockBukkit.mock();
        try {
            var plugin=mock(TurtleRolesPlugin.class);when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());when(plugin.getDataFolder()).thenReturn(data.toFile());when(plugin.getName()).thenReturn("ConquestSMP");
            var command=mock(PluginCommand.class);var executor=mock(CommandExecutor.class);
            when(plugin.getCommand("smp")).thenReturn(mock(PluginCommand.class));
            when(plugin.getCommand("kit")).thenReturn(command);when(command.getExecutor()).thenReturn(executor);
            try(var kits=new BoosterKits(plugin)) {
                var player=server.addPlayer();player.openInventory(server.createInventory(null,9));
                for(String label:new String[]{"kit","essentials:kit","conquestsmp:kit"}) {
                    var event=new PlayerCommandPreprocessEvent(player,"/"+label+" edit");kits.route(event);assertTrue(event.isCancelled());
                }
                verify(executor,times(3)).onCommand(eq(player),eq(command),eq("kit"),aryEq(new String[]{"edit"}));
            }
        } finally {MockBukkit.unmock();}
    }
    @Test void startCommandRequiresPermissionAndRecordsOnlyOneLaunch() throws Exception {
        var server=MockBukkit.mock();
        try {
            var plugin=mock(TurtleRolesPlugin.class);when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());when(plugin.getDataFolder()).thenReturn(data.toFile());when(plugin.getName()).thenReturn("ConquestSMP");
            var command=mock(PluginCommand.class);when(plugin.getCommand("smp")).thenReturn(command);when(plugin.getCommand("kit")).thenReturn(mock(PluginCommand.class));
            try(var kits=new BoosterKits(plugin)){
                var capture=org.mockito.ArgumentCaptor.forClass(CommandExecutor.class);verify(command).setExecutor(capture.capture());
                var sender=mock(CommandSender.class);capture.getValue().onCommand(sender,command,"smp",new String[]{"start"});
                verify(sender).sendMessage("You cannot start the SMP.");
                when(sender.hasPermission("conquestsmp.launch")).thenReturn(true);
                capture.getValue().onCommand(sender,command,"smp",new String[]{"start"});
                verify(sender).sendMessage("Launch saved. Booster kits unlock in 24 hours.");
                capture.getValue().onCommand(sender,command,"smp",new String[]{"start"});
                verify(sender).sendMessage("The SMP has already started. Its kit unlock timer has not been reset.");
            }
        }finally{MockBukkit.unmock();}
    }
    private static String[] aryEq(String[] value){return org.mockito.AdditionalMatchers.aryEq(value);}
}



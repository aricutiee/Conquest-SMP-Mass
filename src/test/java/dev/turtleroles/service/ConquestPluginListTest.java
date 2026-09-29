package dev.turtleroles.service;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.command.ConsoleCommandSender;
import net.kyori.adventure.text.Component;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConquestPluginListTest {
    @Test void allPluginListAliasesAreInterceptedForOperatorsAndMembers() {
        for(boolean op:List.of(true,false))for(String command:List.of("/plugins","/pl","/bukkit:plugins","/bukkit:pl","/PL test","/paper:plugins")) {
            var player=mock(Player.class);when(player.isOp()).thenReturn(op);
            var event=new PlayerCommandPreprocessEvent(player,command,new HashSet<>());
            new ConquestPluginList().command(event);
            assertTrue(event.isCancelled(),command);verify(player,times(2)).sendMessage(any(Component.class));
        }
        assertFalse(ConquestPluginList.matches("/playsound test"));
    }
    @Test void completionFilteringLeavesConsoleDiagnosticsAvailable() {
        var handler=new ConquestPluginList();
        var player=new TabCompleteEvent(mock(Player.class),"/plugins ",new ArrayList<>(List.of("ActualPlugin")));
        handler.complete(player);assertTrue(player.getCompletions().isEmpty());
        var console=new TabCompleteEvent(mock(ConsoleCommandSender.class),"plugins ",new ArrayList<>(List.of("ActualPlugin")));
        handler.complete(console);assertEquals(List.of("ActualPlugin"),console.getCompletions());
    }
}

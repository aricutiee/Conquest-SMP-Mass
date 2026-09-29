package dev.turtleroles.anticheat;

import ac.grim.grimac.api.*;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrimIntegrationTest {
    @Test void everyAutomaticConsoleCommandIsRejectedIncludingAliasesAndWrappers() {
        for (String command : List.of("ban Alex", "kick Alex", "minecraft:kick Alex", "essentials:tempban Alex 1d",
            "execute as Alex run kick Alex", "punish Alex", "customAlias Alex", " [log]", "[log]\nkick Alex", "/ban Alex"))
            assertFalse(GrimPunishmentPolicy.allows(command), command);
        assertFalse(GrimPunishmentPolicy.allows(null));
    }
    @Test void notificationsAndLocalHistoryAreAllowed() {
        for (String action : List.of("[log]", "[webhook]", "[proxy]", "[ConquestAC] Alex failed Simulation x3"))
            assertTrue(GrimPunishmentPolicy.allows(action));
    }
    @Test void grimEventIsCancelledAndSubscriptionIsCleanedUp() {
        JavaPlugin plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        GrimAbstractAPI api = mock(GrimAbstractAPI.class); EventBus bus = mock(EventBus.class); when(api.getEventBus()).thenReturn(bus);
        GrimBinding binding = new GrimBinding(plugin, api);
        verify(bus).subscribe(eq(plugin), eq(CommandExecuteEvent.class), any());
        CommandExecuteEvent command = new CommandExecuteEvent(mock(GrimUser.class), mock(AbstractCheck.class), "test", "kick Alex");
        binding.action(command); assertTrue(command.isCancelled());
        CommandExecuteEvent alert = new CommandExecuteEvent(mock(GrimUser.class), mock(AbstractCheck.class), "test", "[ConquestAC] Alex failed Simulation");
        binding.action(alert); assertFalse(alert.isCancelled());
        binding.close(); verify(bus,times(2)).unregisterListener(eq(plugin), any());
    }
    @Test @SuppressWarnings("unchecked") void profileKeepsDetectionAndCorrectionWithoutPunishmentCommands() {
        Map<String,Object> config = yaml("config.yml");
        Map<String,Object> simulation = (Map<String,Object>)config.get("Simulation");
        assertEquals(.003, simulation.get("threshold")); assertEquals(3, simulation.get("setback-violation-threshold"));
        assertTrue(((Number)simulation.get("max-advantage")).doubleValue() > 0);
        assertTrue(((Number)simulation.get("immediate-setback-threshold")).doubleValue() > 0);
        assertEquals(false, config.get("experimental-checks"));
        assertEquals(true, ((Map<?,?>)config.get("Reach")).get("block-impossible-hits"));
        Map<String, Map<String,Object>> groups = (Map<String,Map<String,Object>>)yaml("punishments.yml").get("Punishments");
        assertTrue(groups.keySet().containsAll(Set.of("Simulation", "Knockback", "Reach", "BadPackets", "Misc")));
        for (Map<String,Object> group : groups.values()) {
            assertFalse(((List<?>)group.get("checks")).isEmpty());
            for (String line : (List<String>) group.get("commands")) {
                assertTrue(line.matches("\\d+:\\d+ \\[(alert|log)]"), line);
            }
        }
        assertTrue(yaml("messages.yml").get("alerts-format").toString().startsWith(GrimPunishmentPolicy.ALERT_PREFIX));
    }
    private Map<String,Object> yaml(String name) { return new Yaml().load(getClass().getResourceAsStream("/grim-profile/"+name)); }
}

package dev.turtleroles.service;
import dev.turtleroles.role.Role;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FakePlayersTest {
    @Test void onlyStoredOwnerQualifies(){for(Role role:Role.values())assertEquals(role==Role.OWNER,FakePlayers.owner(role));}
    @Test void oneThirdAfkAndPingDistribution(){
        assertEquals(10,FakePlayers.afkCount(30));assertEquals(3,FakePlayers.afkCount(10));
        Random random=new Random(123);boolean low=false,high=false;
        for(int i=0;i<1000;i++){int ping=FakePlayers.ping(random);assertTrue(ping>=10&&ping<=180);low|=ping<40;high|=ping>=140;}
        assertTrue(low&&high);
    }
    @Test void suppliedNamesCannotInjectChatOrProfileFields(){
        assertTrue(FakePlayers.validName("PixelKnight123"));
        for(String name:List.of("/op Ari","a.b","Name With Space","x","01234567890123456","<red>Ari"))assertFalse(FakePlayers.validName(name));
    }
    @Test void simulationIsNotInPublicCatalog(){assertTrue(ConquestPluginList.MODULES.stream().noneMatch(m->m.id().contains("fake")||m.description().contains("simulated")));}
    @Test void simulatedTargetsIgnoreRequestsButCannotBypassCombat() throws Exception {
        var server=org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            var plugin=org.mockito.Mockito.mock(dev.turtleroles.TurtleRolesPlugin.class);
            org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(new java.io.File("build/fake-test"));
            var combat=org.mockito.Mockito.mock(dev.turtleroles.combat.ConquestCombat.class);org.mockito.Mockito.when(plugin.combat()).thenReturn(combat);
            var handler=new FakePlayers(plugin);var field=FakePlayers.class.getDeclaredField("players");field.setAccessible(true);
            @SuppressWarnings("unchecked") var profiles=(Map<String,FakePlayers.Fake>)field.get(handler);
            profiles.put("pixel",new FakePlayers.Fake("Pixel",UUID.randomUUID(),2000000000,140));
            var player=server.addPlayer("Visitor");
            for(String command:List.of("/msg Pixel hello","/tpa Pixel","/tpahere Pixel","/essentials:msg Pixel hi")){
                var event=new org.bukkit.event.player.PlayerCommandPreprocessEvent(player,command,new HashSet<>());handler.target(event);assertTrue(event.isCancelled());
            }
            org.mockito.Mockito.when(combat.tagged(player)).thenReturn(true);
            var blocked=new org.bukkit.event.player.PlayerCommandPreprocessEvent(player,"/tpa Pixel",new HashSet<>());handler.target(blocked);assertTrue(blocked.isCancelled());
            server.addPlayer("Pixel");var real=new org.bukkit.event.player.PlayerCommandPreprocessEvent(player,"/msg Pixel hi",new HashSet<>());handler.target(real);assertFalse(real.isCancelled());
            var unknown=new org.bukkit.event.player.PlayerCommandPreprocessEvent(player,"/tpa Unknown",new HashSet<>());handler.target(unknown);assertFalse(unknown.isCancelled());
        } finally {org.mockbukkit.mockbukkit.MockBukkit.unmock();}
    }
}

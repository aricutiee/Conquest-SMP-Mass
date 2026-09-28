package dev.turtleroles.survival;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DimensionAnnouncementTest {
    @Test void bothDimensionsAndBothStatesHaveRedTitlesAndDragonSound(){
        MockBukkit.mock();try{
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            for(String dimension:new String[]{"nether","end"})for(boolean opened:new boolean[]{true,false}){
                var player=mock(Player.class);var location=new Location(null,0,70,0);when(player.getLocation()).thenReturn(location);
                SurvivalModule.announceDimensionTo(player,dimension,opened);
                var capture=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.title.Title.class);verify(player).showTitle(capture.capture());
                var title=capture.getValue();assertEquals((dimension.equals("nether")?"NETHER":"THE END")+(opened?" OPENED":" LOCKED"),plain.serialize(title.title()));
                assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED,title.title().color());assertTrue(plain.serialize(title.subtitle()).endsWith(opened?"has been opened.":"has been locked."));
                verify(player).playSound(location,Sound.ENTITY_ENDER_DRAGON_GROWL,.7f,1f);
            }
        }finally{MockBukkit.unmock();}
    }
}

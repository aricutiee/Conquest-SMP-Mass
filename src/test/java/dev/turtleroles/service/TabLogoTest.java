package dev.turtleroles.service;

import dev.turtleroles.pack.PackStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TabLogoTest {
    @Test void logoAppearsOnlyForViewerWhosePackLoadedAndReservesItsHeight() {
        PresentationService presentation = new PresentationService(mock(RoleService.class));
        TabListService tab = new TabListService(mock(Plugin.class), presentation);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        for (PackStatus status : PackStatus.values()) {
            presentation.setPackStatus(player.getUniqueId(), status);
            Component header = tab.header(player);
            String text = PlainTextComponentSerializer.plainText().serialize(header);
            if (status == PackStatus.LOADED) {
                assertEquals(TabListService.LOGO_GLYPHS + "\n".repeat(8), text);
                assertEquals(40, TabListService.LOGO_GLYPHS.chars().filter(c -> c >= 0xE100 && c < 0xE128).count());
                assertEquals("turtleroles:header", header.children().getFirst().font().asString());
                assertTrue(9 * 9 >= 64, "Header rows must contain the bitmap height");
            } else {
                assertEquals("CONQUEST SMP", text);
            }
        }
        presentation.setPackStatus(player.getUniqueId(), PackStatus.LOADED);
        presentation.forgetPlayer(player.getUniqueId());
        assertFalse(presentation.canUseGlyphFor(player));
    }
}

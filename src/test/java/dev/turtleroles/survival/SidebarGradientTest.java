package dev.turtleroles.survival;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;

class SidebarGradientTest {
    @Test void everyLetterAndDigitUsesTheSetwordPurple() throws Exception {
        var atlas=ImageIO.read(Path.of(System.getProperty("turtleroles.generatedPackDir"),"assets/turtleroles/textures/font/sidebar.png").toFile());
        assertEquals(96,atlas.getWidth());assertEquals(24,atlas.getHeight());
        for(int i=0;i<36;i++) {
            for(int y:new int[]{2,6}) {
                boolean ink=false;
                for(int x=0;x<6;x++) {
                    int color=atlas.getRGB((i%16)*6+x,(i/16)*8+y);
                    if((color>>>24)!=0) {
                        ink=true;assertEquals(0xFFC69AFF,color,"glyph "+i);
                    }
                }
                assertTrue(ink,"Missing top/bottom ink for glyph "+i);
            }
        }
    }
    @Test void loadedPackUsesUntintedBitmapAndFallbackUsesReadableUnicode() {
        var custom=SurvivalSidebar.letters("Playtime: 2d 7h",true);
        assertEquals("turtleroles:sidebar",custom.font().asString());
        assertEquals(TextColor.color(0xFFFFFF),custom.color());
        assertEquals("PLAYTIME: 2D 7H",PlainTextComponentSerializer.plainText().serialize(custom));
        var fallback=SurvivalSidebar.letters("Ping: 42ms",false);
        assertEquals("minecraft:default",fallback.font().asString());
        assertEquals(TextColor.color(0xB477FF),fallback.color());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(fallback).contains("42"));
    }
    @Test void titleAndIpMatchWithLightEdgesAndDarkMiddle()throws Exception{
        var atlas=ImageIO.read(Path.of(System.getProperty("turtleroles.generatedPackDir"),"assets/turtleroles/textures/font/sidebar_title.png").toFile());
        for(int y:new int[]{2,4,6})for(int x=0;x<atlas.getWidth();x++)if((atlas.getRGB(x,y)>>>24)!=0)assertEquals(y==4?0xFF60308F:0xFFC69AFF,atlas.getRGB(x,y));
        var root=Path.of(System.getProperty("turtleroles.generatedPackDir"),"assets/turtleroles");
        assertArrayEquals(java.nio.file.Files.readAllBytes(root.resolve("textures/font/sidebar_title.png")),java.nio.file.Files.readAllBytes(root.resolve("textures/font/sidebar_ip.png")));
        String ip=java.nio.file.Files.readString(root.resolve("font/sidebar_ip.json"));
        assertTrue(ip.contains("\"height\":8"));assertTrue(ip.contains("\"ascent\":7"));
        assertEquals("turtleroles:sidebar_title",SurvivalSidebar.title(true).font().asString());
    }
}

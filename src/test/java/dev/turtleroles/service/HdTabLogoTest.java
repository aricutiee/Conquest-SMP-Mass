package dev.turtleroles.service;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HdTabLogoTest {
    @Test void emittedHeaderReassemblesAllTilesWithoutGapsOrOverlap() throws Exception {
        Path root = Path.of(System.getProperty("turtleroles.generatedPackDir"));
        Map<String, Object> json = new Yaml().load(Files.readString(root.resolve("assets/turtleroles/font/header.json")));
        Map<Integer, Map<?, ?>> bitmaps = new HashMap<>();
        Map<Integer, Float> spaces = new HashMap<>();
        for (Object item : (List<?>) json.get("providers")) {
            Map<?, ?> provider = (Map<?, ?>) item;
            if (provider.get("type").equals("bitmap")) {
                String chars = (String) ((List<?>) provider.get("chars")).getFirst();
                assertEquals(1, chars.codePointCount(0, chars.length()));
                assertNull(bitmaps.put(chars.codePointAt(0), provider));
            } else {
                ((Map<?, ?>) provider.get("advances")).forEach((key, value) ->
                    spaces.put(((String) key).codePointAt(0), ((Number) value).floatValue()));
            }
        }
        BufferedImage reconstructed = new BufferedImage(2000, 800, BufferedImage.TYPE_INT_ARGB);
        var graphics = reconstructed.createGraphics();
        float cursor = 0;
        int tileCount = 0, partialWidthTiles = 0;
        Set<Integer> seen = new HashSet<>();
        for (int codepoint : TabListService.LOGO_GLYPHS.codePoints().toArray()) {
            if (spaces.containsKey(codepoint)) {
                cursor += spaces.get(codepoint);
                assertTrue(cursor >= 0 && cursor <= 160, "Spacing leaves the header rectangle");
                continue;
            }
            var provider = bitmaps.get(codepoint);
            assertNotNull(provider, "No font provider for emitted codepoint");
            assertTrue(seen.add(codepoint), "Duplicate tile");
            int index = codepoint - 0xE100;
            int height = ((Number) provider.get("height")).intValue();
            int top = 7 - ((Number) provider.get("ascent")).intValue();
            assertEquals((index % 10) * 16, cursor, 0.0001, "Horizontal seam or row rewind error");
            assertEquals((index / 10) * 16, top, "Vertical seam");
            assertEquals(16, height);
            String texture = provider.get("file").toString().replace("turtleroles:", "assets/turtleroles/textures/");
            BufferedImage tile = ImageIO.read(root.resolve(texture).toFile());
            assertEquals(200, tile.getWidth());
            assertEquals(200, tile.getHeight());
            assertTrue(tile.getWidth() < 256 && tile.getHeight() < 256, "Vanilla font atlas fit");
            graphics.drawImage(tile, Math.round(cursor * 12.5f), Math.round(top * 12.5f), null);
            int rightmost = -1;
            for (int y = 0; y < tile.getHeight(); y++) {
                for (int x = 0; x < tile.getWidth(); x++) {
                    if ((tile.getRGB(x, y) >>> 24) != 0) rightmost = Math.max(rightmost, x);
                }
            }
            if (rightmost < 199) partialWidthTiles++;
            // Advance rule verified against the installed vanilla 1.21.11 client.
            cursor += (int) (0.5 + (double) ((rightmost + 1) * ((float) height / tile.getHeight()))) + 1;
            tileCount++;
        }
        graphics.dispose();
        assertEquals(40, tileCount);
        assertEquals(160, cursor);
        assertTrue(partialWidthTiles > 0, "Exercise correction of transparent right margins");
        BufferedImage preview = ImageIO.read(Path.of("build/generated/turtleroles/previews/tab-logo-hd.png").toFile());
        assertArrayEquals(preview.getRGB(0, 0, 2000, 800, null, 0, 2000),
            reconstructed.getRGB(0, 0, 2000, 800, null, 0, 2000), "Every original tile pixel must be retained");
        assertEquals(0, reconstructed.getRGB(0, 0) >>> 24);
    }
}

package dev.turtleroles;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetValidationTest {
    @Test void tabLogoIsPackagedAsTransparentAtlasSizedBitmapForExactServerVersion() throws Exception {
        Path root = Path.of(System.getProperty("turtleroles.generatedPackDir"));
        var yaml = new org.yaml.snakeyaml.Yaml();
        java.util.Map<String, Object> metadata = yaml.load(Files.readString(root.resolve("pack.mcmeta")));
        var pack = (java.util.Map<?, ?>) metadata.get("pack");
        assertEquals(java.util.List.of(75, 0), pack.get("min_format"));
        assertEquals(java.util.List.of(75, 0), pack.get("max_format"));
        java.util.Map<String, Object> font = yaml.load(Files.readString(root.resolve("assets/turtleroles/font/header.json")));
        var providers = (java.util.List<?>) font.get("providers");
        assertEquals(41, providers.size());
        var provider = (java.util.Map<?, ?>) providers.getFirst();
        assertEquals(java.util.List.of("\uE100"), provider.get("chars"));
        assertEquals(16, provider.get("height"));
        assertEquals(7, provider.get("ascent"));
        assertEquals("turtleroles:font/conquest_logo_0.png", provider.get("file"));
        BufferedImage logo = ImageIO.read(root.resolve("assets/turtleroles/textures/font/conquest_logo_0.png").toFile());
        assertEquals(200, logo.getWidth());
        assertEquals(200, logo.getHeight());
        assertEquals(0, logo.getRGB(0, 0) >>> 24);
        assertTrue(logo.getColorModel().hasAlpha());
        try (ZipFile zip = new ZipFile(Path.of(System.getProperty("turtleroles.generatedZip")).toFile())) {
            assertNotNull(zip.getEntry("assets/turtleroles/font/header.json"));
            for (int i = 0; i < 40; i++) {
                assertNotNull(zip.getEntry("assets/turtleroles/textures/font/conquest_logo_" + i + ".png"));
            }
        }
    }
    private static final String[] TEXTURES = {"owner.png", "co_owner.png", "sr_admin.png", "admin.png", "moderator.png", "helper.png", "member.png", "media.png", "king.png", "sser.png", "booster.png", "booster_x2.png"};

    @Test
    void generatedPngsAreTransparentReadableAndTightlyTrimmed() throws Exception {
        Path badgeDir = Path.of(System.getProperty("turtleroles.generatedBadgeDir"));
        for (String texture : TEXTURES) {
            BufferedImage image = ImageIO.read(badgeDir.resolve(texture).toFile());
            assertNotNull(image, texture);
            assertEquals(8, image.getHeight(), texture);
            assertTrue(image.getWidth() >= 25 && image.getWidth() <= 80, texture + " width");
            int transparent = 0;
            int opaque = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) == 0) {
                        transparent++;
                    } else {
                        opaque++;
                    }
                }
            }
            assertTrue(transparent > 0, texture + " has a transparent texture margin");
            assertTrue(opaque > 150, texture + " has visible pixels");
            assertTrue(!edgeHasOpaque(image, 0) && !edgeHasOpaque(image, image.getWidth() - 1), texture + " has transparent outer edges");
        }
    }

    @Test
    void packZipHasCorrectRootAndFontReferences() throws Exception {
        Path zipPath = Path.of(System.getProperty("turtleroles.generatedZip"));
        assertTrue(Files.size(zipPath) > 0);
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("assets/turtleroles/font/roles.json"));
            assertTrue(zip.stream().noneMatch(e -> e.getName().toLowerCase().contains("shock")));
            for (String texture : TEXTURES) {
                assertNotNull(zip.getEntry("assets/turtleroles/textures/font/" + texture), texture);
            }
        }
        String fontJson = Files.readString(Path.of(System.getProperty("turtleroles.generatedPackDir", "build/generated/turtleroles/resource-pack")).resolve("assets/turtleroles/font/roles.json"));
        for (String codepoint : Set.of("\\uE001", "\\uE002", "\\uE003", "\\uE004", "\\uE005", "\\uE006", "\\uE007", "\\uE008", "\\uE009")) {
            assertTrue(fontJson.contains(codepoint));
        }
    }

    @Test
    void newBadgesHaveRequestedColors() throws Exception {
        Path badgeDir=Path.of(System.getProperty("turtleroles.generatedBadgeDir"));
        var booster=ImageIO.read(badgeDir.resolve("booster.png").toFile());
        var sser=ImageIO.read(badgeDir.resolve("sser.png").toFile());
        assertEquals(0x9747D9,booster.getRGB(1,0)&0xFFFFFF);
        assertEquals(0x5BCEFA,sser.getRGB(1,0)&0xFFFFFF);
        assertTrue((sser.getRGB(sser.getWidth()/2,0)&0xFFFFFF)!=(sser.getRGB(1,0)&0xFFFFFF));
    }

    @Test
    void kingBadgeContainsFullWordAndDetailedCrown() throws Exception {
        Path badgeDir = Path.of(System.getProperty("turtleroles.generatedBadgeDir"));
        BufferedImage king = ImageIO.read(badgeDir.resolve("king.png").toFile());
        assertEquals(40, king.getWidth());
        // Both upper arms of the first K must be present; this catches a missing font glyph.
        assertEquals(king.getRGB(15, 1), king.getRGB(19, 1));
        assertTrue((king.getRGB(15, 1) & 0xFFFFFF) != (king.getRGB(16, 1) & 0xFFFFFF));
        // A light center jewel and the red inset make the crown legible at native size.
        assertEquals(0xFFF8C8, king.getRGB(7, 0) & 0xFFFFFF);
        assertEquals(0xD94445, king.getRGB(7, 5) & 0xFFFFFF);
    }



    @Test
    void crownHasDedicatedWearableModelAndTextureInPack() throws Exception {
        Path pack = Path.of(System.getProperty("turtleroles.generatedPackDir"));
        String definition = Files.readString(pack.resolve("assets/kingscrown/items/crown.json"));
        assertTrue(definition.contains("kingscrown:item/crown"));
        String model = Files.readString(pack.resolve("assets/kingscrown/models/item/crown.json"));
        assertTrue(model.contains("kingscrown:item/crown_palette"));
        var yaml = new org.yaml.snakeyaml.Yaml();
        java.util.Map<String, Object> root = yaml.load(model);
        var elements = (java.util.List<?>) root.get("elements");
        assertTrue(elements.size() > 6, "The wearable crown needs a three-dimensional mesh");
        var display = (java.util.Map<?, ?>) root.get("display");
        assertTrue(display.containsKey("head"), "The model needs an explicit worn transform");
        BufferedImage texture = ImageIO.read(pack.resolve("assets/kingscrown/textures/item/crown_palette.png").toFile());
        assertEquals(64, texture.getWidth());
        assertEquals(64, texture.getHeight());
    }

    @Test
    void crownWallsClearPlayerHeadAndOuterSkinLayer() throws Exception {
        Path pack = Path.of(System.getProperty("turtleroles.generatedPackDir"));
        java.util.Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(
            Files.readString(pack.resolve("assets/kingscrown/models/item/crown.json")));
        var display = (java.util.Map<?, ?>) root.get("display");
        var head = (java.util.Map<?, ?>) display.get("head");
        var scale = (java.util.List<?>) head.get("scale");
        var translation = (java.util.List<?>) head.get("translation");
        assertEquals(10.5, ((Number) translation.get(1)).doubleValue(), 0.01);
        assertEquals(0.85, ((Number) scale.get(0)).doubleValue(), 0.01);
        assertEquals(0.85, ((Number) scale.get(2)).doubleValue(), 0.01);
    }

    private boolean edgeHasOpaque(BufferedImage image, int x) {
        for (int y = 0; y < image.getHeight(); y++) {
            if ((image.getRGB(x, y) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }
}


package dev.turtleroles.assets;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BadgeAssetGenerator {
    private static final List<RoleArt> ROLES = List.of(
        new RoleArt("owner", "OWNER", 0xE001, "owner.png", new int[]{0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA}, false),
        new RoleArt("co_owner", "CO-OWNER", 0xE002, "co_owner.png", new int[]{0x380610, 0x650B1B, 0x8A162A}, false),
        new RoleArt("sr_admin", "SR ADMIN", 0xE003, "sr_admin.png", new int[]{0x080A0D, 0x242830, 0x8E98A6}, false),
        new RoleArt("admin", "ADMIN", 0xE004, "admin.png", new int[]{0x650E24, 0x8B1234, 0xBD2142}, false),
        new RoleArt("sser", "SSER", 0xE00A, "sser.png", new int[]{0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA}, false),
        new RoleArt("moderator", "MODERATOR", 0xE005, "moderator.png", new int[]{0x14532D, 0x15803D, 0x41CE70}, false),
        new RoleArt("helper", "HELPER", 0xE006, "helper.png", new int[]{0xA96D09, 0xD49A12, 0xFFE477}, false),
        new RoleArt("media", "MEDIA", 0xE009, "media.png", new int[]{0xBD188F, 0xFF45C5, 0xFFA6E8}, false),
        new RoleArt("booster_x2", "BOOSTER X2", 0xE00C, "booster_x2.png", new int[]{0x542080, 0x9747D9, 0xDBB0FF}, false),
        new RoleArt("booster", "BOOSTER", 0xE00B, "booster.png", new int[]{0x542080, 0x9747D9, 0xDBB0FF}, false),
        new RoleArt("coal", "COAL", 0xE00D, "coal.png", new int[]{0x303038, 0x555560, 0x92929C}, false),
        new RoleArt("iron", "IRON", 0xE00E, "iron.png", new int[]{0x777780, 0xBBBBCC, 0xEEEEFF}, false),
        new RoleArt("redstone", "REDSTONE", 0xE00F, "redstone.png", new int[]{0x750D18, 0xD82838, 0xFF727C}, false),
        new RoleArt("diamond", "DIAMOND", 0xE010, "diamond.png", new int[]{0x146D80, 0x32C9DE, 0xA0F4FF}, false),
        new RoleArt("netherite", "NETHERITE", 0xE011, "netherite.png", new int[]{0x302636, 0x635069, 0xAE90B8}, false),
        new RoleArt("member", "MEMBER", 0xE007, "member.png", new int[]{0x454B55, 0x626975, 0xAEB5C0}, false),
        new RoleArt("king", "KING", 0xE008, "king.png", new int[]{0x9B5C00, 0xF0AD20, 0xFFE784}, false)
    );

    private static final Map<Character, String[]> FONT = font();

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException("Expected packDir badgeDir previewDir zip sha1 crownDir logoPng audioDir");
        }
        Path packDir = Path.of(args[0]);
        Path badgeDir = Path.of(args[1]);
        Path previewDir = Path.of(args[2]);
        Path zip = Path.of(args[3]);
        Path sha1 = Path.of(args[4]);
        Files.createDirectories(packDir);

        Files.createDirectories(badgeDir);
        Files.createDirectories(previewDir);
        Path textureDir = packDir.resolve("assets/turtleroles/textures/font");
        Path fontDir = packDir.resolve("assets/turtleroles/font");
        Files.createDirectories(textureDir);
        Files.createDirectories(fontDir);

        StringBuilder providers = new StringBuilder();
        providers.append("{\n  \"providers\": [\n");
        for (int i = 0; i < ROLES.size(); i++) {
            RoleArt role = ROLES.get(i);
            // Native-resolution artwork matches the font metrics exactly.
            BufferedImage image = drawBadge(role);
            ImageIO.write(image, "png", badgeDir.resolve(role.texture).toFile());
            ImageIO.write(image, "png", textureDir.resolve(role.texture).toFile());
            ImageIO.write(scale(image, 4), "png", previewDir.resolve(role.id + "-8x.png").toFile());
            providers.append("    {\"type\":\"bitmap\",\"file\":\"turtleroles:font/")
                .append(role.texture)
                .append("\",\"height\":8,\"ascent\":7,\"chars\":[\"\\u")
                .append(String.format("%04X", role.codepoint))
                .append("\"]}");
            if (i + 1 < ROLES.size()) {
                providers.append(",");
            }
            providers.append("\n");
        }
        providers.append("  ]\n}\n");
        Files.writeString(fontDir.resolve("roles.json"), providers.toString(), StandardCharsets.UTF_8);
        Files.writeString(packDir.resolve("pack.mcmeta"), """
            {
              "pack": {
                "description": "Conquest SMP tab logo, role badges and the King's Crown",
                "min_format": [75, 0],
                "max_format": [75, 0]
              }
            }
            """, StandardCharsets.UTF_8);

        Path crownDir = Path.of(args[5]);
        copyCrownAsset(crownDir.resolve("crown_item.json"), packDir.resolve("assets/kingscrown/items/crown.json"));
        copyCrownAsset(crownDir.resolve("crown_model.json"), packDir.resolve("assets/kingscrown/models/item/crown.json"));
        copyCrownAsset(crownDir.resolve("crown_palette.png"), packDir.resolve("assets/kingscrown/textures/item/crown_palette.png"));
        writeTabLogo(Path.of(args[6]), textureDir, fontDir, previewDir);
        writeStatIcons(textureDir, fontDir, previewDir);
        SidebarFontGenerator.write(textureDir, fontDir, previewDir);

        Files.createDirectories(zip.getParent());
        copyCrownAsset(Path.of(args[7]).resolve("warlord_dread.ogg"),packDir.resolve("assets/conquestsmp/sounds/warlord_dread.ogg"));
        copyCrownAsset(Path.of(args[7]).resolve("CREDITS.txt"),packDir.resolve("MUSIC-CREDITS.txt"));
        Files.writeString(packDir.resolve("assets/conquestsmp/sounds.json"),"""
            {"warlord_dread":{"sounds":[{"name":"conquestsmp:warlord_dread","stream":true}]}}
            """,StandardCharsets.UTF_8);
        zipDirectory(packDir, zip);
        Files.writeString(sha1, hex(sha1(Files.readAllBytes(zip))) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static void writeStatIcons(Path textureDir, Path fontDir, Path previewDir) throws IOException {
        // One pixel per GUI pixel, matching the existing badges. White ink takes the scoreboard's purple tint.
        String[][] icons={
            {"00000011","00000111","00001110","00011100","10111000","01110000","01101000","10000000"},
            {"00111100","01111110","11111111","11011011","11011011","01100110","00111100","00101000"},
            {"00001000","00011000","00111010","00111110","01111110","11101111","11000111","01111110"},
            {"00000011","00000011","00011011","00011011","01111011","01111011","11111011","11111011"},
            {"00111100","01000010","10010001","10010001","10011101","10000001","01000010","00111100"}
        };
        BufferedImage atlas=new BufferedImage(40,8,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<icons.length;i++)for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            if(icons[i][y].charAt(x)=='1')atlas.setRGB(i*8+x,y,SidebarFontGenerator.ink());
        ImageIO.write(atlas,"png",textureDir.resolve("stats.png").toFile());
        ImageIO.write(scale(atlas,12),"png",previewDir.resolve("stat-icons.png").toFile());
        Files.writeString(fontDir.resolve("stats.json"),
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"turtleroles:font/stats.png\",\"height\":8,\"ascent\":7,\"chars\":[\"\\uE300\\uE301\\uE302\\uE303\\uE304\"]}]}\n",StandardCharsets.UTF_8);
    }

    private static void writeTabLogo(Path source, Path textureDir, Path fontDir, Path previewDir) throws IOException {
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) throw new IOException("Cannot read the tab logo: " + source);
        // Keep near-native detail, then tile below the client's 256px glyph
        // atlas limit. Integer GUI positions prevent rounding seams.
        int columns = 10, rows = 4, tileSize = 200, guiTileSize = 16;
        int height = rows * tileSize;
        int artworkWidth = (int) Math.round(height * (double) original.getWidth() / original.getHeight());
        int width = columns * tileSize;
        if (artworkWidth > width) throw new IOException("Logo aspect ratio exceeds the tab layout");
        BufferedImage logo = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = logo.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(original, (width - artworkWidth) / 2, 0, artworkWidth, height, null);
        graphics.dispose();
        // A complete reconstruction is useful for visual QA, outside the pack.
        ImageIO.write(logo, "png", previewDir.resolve("tab-logo-hd.png").toFile());
        Files.deleteIfExists(textureDir.resolve("conquest_logo.png"));
        StringBuilder providers = new StringBuilder("{\"providers\":[\n");
        StringBuilder advances = new StringBuilder("{\"type\":\"space\",\"advances\":{");
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                BufferedImage tile = logo.getSubimage(column * tileSize, row * tileSize, tileSize, tileSize);
                String name = "conquest_logo_" + index + ".png";
                ImageIO.write(tile, "png", textureDir.resolve(name).toFile());
                providers.append(String.format(java.util.Locale.ROOT,
                    "{\"type\":\"bitmap\",\"file\":\"turtleroles:font/%s\",\"height\":%d,\"ascent\":%d,\"chars\":[\"\\u%04X\"]},\n",
                    name, guiTileSize, 7 - row * guiTileSize, 0xE100 + index));
                // Vanilla 1.21.11 rounds the rightmost nontransparent column,
                // then adds one GUI pixel. Undo both that trim and the gap.
                int inkWidth = 0;
                for (int x = tileSize - 1; x >= 0 && inkWidth == 0; x--) {
                    for (int y = 0; y < tileSize; y++) {
                        if ((tile.getRGB(x, y) >>> 24) != 0) { inkWidth = x + 1; break; }
                    }
                }
                int advance = (int) (0.5 + (double) (inkWidth * ((float) guiTileSize / tileSize))) + 1;
                if (index > 0) advances.append(',');
                advances.append(String.format(java.util.Locale.ROOT, "\"\\u%04X\":%d", 0xE200 + index, guiTileSize - advance));
            }
        }
        advances.append(",\"\\uE2FF\":-160}}");
        providers.append(advances).append("]}\n");
        Files.writeString(fontDir.resolve("header.json"), providers.toString(), StandardCharsets.UTF_8);
    }

    private static void copyCrownAsset(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static BufferedImage drawBadge(RoleArt role) {
        // Draw on the actual eight-pixel GUI grid: no fractional downsampling.
        int iconWidth = role.id.equals("king") ? 13 : role.turtle ? 8 : 0;
        int width = (role.id.equals("king") ? 23 : textWidth(role.label)) + iconWidth + 4;
        BufferedImage image = new BufferedImage(width, 8, BufferedImage.TYPE_INT_ARGB);
        int background = switch (role.id) {
            case "co_owner" -> 0x650B1B;
            case "sr_admin" -> 0x15171C;
            case "admin" -> 0xD32149;
            case "moderator" -> 0x168D42;
            case "helper" -> 0xFFE151;
            case "media" -> 0xFF45C5;
            case "booster", "booster_x2" -> 0x9747D9;
            case "member" -> 0x697586;
            case "king" -> 0xF8D56A;
            default -> 0x5BCEFA;
        };
        for (int y = 0; y < 8; y++) {
            for (int x = 1; x < width - 1; x++) {
                int color = (role.id.equals("owner") || role.id.equals("sser"))
                    ? gradient(role.colors, (double) (x - 1) / (width - 3))
                    : background;
                image.setRGB(x, y, argb(color));
            }
        }
        if (role.turtle) {
            int light = argb(0xBAFFD7);
            rect(image, 3, 2, 4, 4, light);
            rect(image, 4, 3, 2, 2, argb(0x08734E));
            rect(image, 7, 3, 1, 2, light);
            rect(image, 3, 1, 1, 1, light);
            rect(image, 6, 1, 1, 1, light);
            rect(image, 3, 6, 1, 1, light);
            rect(image, 6, 6, 1, 1, light);
        }
        if (role.id.equals("king")) {
            rect(image, 1, 0, 13, 8, argb(0x52331A));
            String[] crown = {
                ".....H.....",
                ".H...H...H.",
                ".G...G...G.",
                ".GG.GGG.GG.",
                ".GGGGGGGGG.",
                ".OGGGRGGGO.",
                ".OOOOOOOOO.",
                "..GGGGGGG.."
            };
            for (int y = 0; y < crown.length; y++) {
                for (int x = 0; x < crown[y].length(); x++) {
                    int color = switch (crown[y].charAt(x)) {
                        case 'H' -> 0xFFF8C8;
                        case 'G' -> 0xF7C64F;
                        case 'D' -> 0xB9751C;
                        case 'O' -> 0x321B12;
                        case 'R' -> 0xD94445;
                        default -> -1;
                    };
                    if (color >= 0) image.setRGB(x + 2, y, argb(color));
                }
            }
        }
        // Dark ink keeps the pastel flag and yellow helper badge readable.
        int ink = (role.id.equals("owner") || role.id.equals("sser")) || role.id.equals("helper") || role.id.equals("king") ? 0x192337 : 0xFFFFFF;
        if (role.id.equals("king")) drawKingLabel(image, 2 + iconWidth, argb(ink));
        else drawText(image, role.label, 2 + iconWidth, 1, argb(ink));
        return image;
    }

    private static void drawKingLabel(BufferedImage image, int startX, int color) {
        String[][] letters = {
            glyph("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
            glyph("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
            glyph("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
            glyph("01111", "10000", "10000", "10111", "10001", "10001", "01111")
        };
        for (int letter = 0; letter < letters.length; letter++) {
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (letters[letter][row].charAt(col) == '1') {
                        image.setRGB(startX + letter * 6 + col, row + 1, color);
                    }
                }
            }
        }
    }
    private static boolean insidePlaque(int x, int y, int width) {
        // Square corners keep the badge aligned with Minecraft's rectangular
        // player-list rows.
        return true;
    }

    private static void drawBorder(BufferedImage image, int width, int[] colors) {
        int rim = shade(colors[Math.min(colors.length - 1, 1)], 1.45, 255);
        int dark = shade(colors[0], 0.36, 255);
        for (int x = 0; x < width; x++) {
            setIfInside(image, x, 0, rim);
            setIfInside(image, x, 15, dark);
        }
        for (int y = 0; y < 16; y++) {
            setIfInside(image, 0, y, rim);
            setIfInside(image, width - 1, y, dark);
        }
        for (int x = 1; x < width - 1; x++) {
            setIfInside(image, x, 1, shade(rim & 0xFFFFFF, 0.92, 255));
            setIfInside(image, x, 14, shade(dark & 0xFFFFFF, 0.92, 255));
        }
    }

    private static void setIfInside(BufferedImage image, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && ((image.getRGB(x, y) >>> 24) != 0)) {
            image.setRGB(x, y, argb);
        }
    }

    private static void drawTurtle(BufferedImage image, int x, int y) {
        int shellDark = argb(0x164B35);
        int shell = argb(0x3E9161);
        int shellLight = argb(0xA8CA7B);
        int accent = argb(0xFFE477);
        rect(image, x + 3, y + 2, 6, 7, shellDark);
        rect(image, x + 4, y + 1, 4, 1, shellLight);
        rect(image, x + 4, y + 3, 4, 5, shell);
        rect(image, x + 5, y + 4, 2, 3, shellDark);
        rect(image, x + 4, y + 5, 4, 1, shellLight);
        rect(image, x + 5, y, 2, 1, accent);
        rect(image, x + 0, y + 4, 2, 3, accent);
        rect(image, x + 10, y + 4, 2, 3, accent);
        rect(image, x + 2, y + 9, 2, 2, accent);
        rect(image, x + 8, y + 9, 2, 2, accent);
    }

    private static void drawOutlinedText(BufferedImage image, String text, int x, int y) {
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (Math.abs(ox) + Math.abs(oy) <= 1) {
                    drawText(image, text, x + ox, y + oy, argb(0x050507));
                }
            }
        }
        drawText(image, text, x, y, argb(0xFFFFFF));
        drawText(image, text, x, y + 1, argb(0xECEFF4));
    }

    private static void drawText(BufferedImage image, String text, int x, int y, int color) {
        int cursor = x;
        for (char ch : text.toCharArray()) {
            String[] glyph = FONT.get(ch);
            if (glyph == null) {
                cursor += 3;
                continue;
            }
            for (int row = 0; row < glyph.length; row++) {
                for (int col = 0; col < glyph[row].length(); col++) {
                    if (glyph[row].charAt(col) == '1') {
                        int px = cursor + col;
                        int py = y + row;
                        if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                            image.setRGB(px, py, color);
                        }
                    }
                }
            }
            cursor += glyph[0].length() + 1;
        }
    }

    private static int textWidth(String text) {
        int width = 0;
        for (char ch : text.toCharArray()) {
            String[] glyph = FONT.get(ch);
            width += (glyph == null ? 2 : glyph[0].length()) + 1;
        }
        return Math.max(0, width - 1);
    }

    private static int gradient(int[] colors, double t) {
        if (colors.length == 1) {
            return colors[0];
        }
        double scaled = t * (colors.length - 1);
        int left = Math.min(colors.length - 2, (int) Math.floor(scaled));
        double local = scaled - left;
        return lerp(colors[left], colors[left + 1], local);
    }

    private static int lerp(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return ((int) Math.round(ar + (br - ar) * t) << 16)
            | ((int) Math.round(ag + (bg - ag) * t) << 8)
            | (int) Math.round(ab + (bb - ab) * t);
    }

    private static int shade(int rgb, double shade, int alpha) {
        int r = Math.max(0, Math.min(255, (int) (((rgb >> 16) & 0xFF) * shade)));
        int g = Math.max(0, Math.min(255, (int) (((rgb >> 8) & 0xFF) * shade)));
        int b = Math.max(0, Math.min(255, (int) ((rgb & 0xFF) * shade)));
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static void rect(BufferedImage image, int x, int y, int width, int height, int color) {
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                if (xx >= 0 && yy >= 0 && xx < image.getWidth() && yy < image.getHeight()) {
                    image.setRGB(xx, yy, color);
                }
            }
        }
    }

    private static BufferedImage trimTransparent(BufferedImage image) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, out.getWidth(), out.getHeight(), minX, minY, maxX + 1, maxY + 1, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scale(BufferedImage image, int factor) {
        BufferedImage scaled = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        g.dispose();
        return scaled;
    }

    private static BufferedImage transparentMargin(BufferedImage image, int margin) {
        BufferedImage out = new BufferedImage(
            image.getWidth() + margin * 2,
            image.getHeight() + margin * 2,
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g = out.createGraphics();
        g.drawImage(image, margin, margin, null);
        g.dispose();
        return out;
    }

    private static void zipDirectory(Path source, Path zip) throws IOException {
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            try (var files = Files.walk(source)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = source.relativize(file).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(0L);
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private static byte[] sha1(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static Map<Character, String[]> font() {
        Map<Character, String[]> map = new LinkedHashMap<>();
        map.put('A', glyph("01110", "10001", "10001", "11111", "10001", "10001", "10001"));
        map.put('B', glyph("11110", "10001", "10001", "11110", "10001", "10001", "11110"));
        map.put('C', glyph("01111", "10000", "10000", "10000", "10000", "10000", "01111"));
        map.put('D', glyph("11110", "10001", "10001", "10001", "10001", "10001", "11110"));
        map.put('E', glyph("11111", "10000", "10000", "11110", "10000", "10000", "11111"));
        map.put('F', glyph("11111", "10000", "10000", "11110", "10000", "10000", "10000"));
        map.put('G', glyph("01111", "10000", "10000", "10111", "10001", "10001", "01111"));
        map.put('H', glyph("10001", "10001", "10001", "11111", "10001", "10001", "10001"));
        map.put('I', glyph("11111", "00100", "00100", "00100", "00100", "00100", "11111"));
        map.put('K', glyph("10001", "10010", "10100", "11000", "10100", "10010", "10001"));
        map.put('L', glyph("10000", "10000", "10000", "10000", "10000", "10000", "11111"));
        map.put('M', glyph("10001", "11011", "10101", "10101", "10001", "10001", "10001"));
        map.put('N', glyph("10001", "11001", "10101", "10011", "10001", "10001", "10001"));
        map.put('O', glyph("01110", "10001", "10001", "10001", "10001", "10001", "01110"));
        map.put('P', glyph("11110", "10001", "10001", "11110", "10000", "10000", "10000"));
        map.put('R', glyph("11110", "10001", "10001", "11110", "10100", "10010", "10001"));
        map.put('S', glyph("01111", "10000", "10000", "01110", "00001", "00001", "11110"));
        map.put('T', glyph("11111", "00100", "00100", "00100", "00100", "00100", "00100"));
        map.put('V', glyph("10001", "10001", "10001", "10001", "01010", "01010", "00100"));
        map.put('W', glyph("10001", "10001", "10001", "10101", "10101", "11011", "10001"));
        map.put('-', glyph("000", "000", "000", "111", "000", "000", "000"));
        map.put(' ', glyph("00", "00", "00", "00", "00", "00", "00"));
        map.put('A', glyph("01110","10001","11111","10001","10001"));
        map.put('B', glyph("11110","10001","11110","10001","11110"));
        map.put('C', glyph("01111","10000","10000","10000","01111"));
        map.put('D', glyph("11110","10001","10001","10001","11110"));
        map.put('E', glyph("11111","10000","11110","10000","11111"));
        map.put('H', glyph("10001","10001","11111","10001","10001"));
        map.put('I', glyph("111","010","010","010","111"));
        map.put('L', glyph("10000","10000","10000","10000","11111"));
        map.put('M', glyph("10001","11011","10101","10001","10001"));
        map.put('N', glyph("10001","11001","10101","10011","10001"));
        map.put('O', glyph("01110","10001","10001","10001","01110"));
        map.put('P', glyph("11110","10001","11110","10000","10000"));
        map.put('R', glyph("11110","10001","11110","10010","10001"));
        map.put('S', glyph("01111","10000","01110","00001","11110"));
        map.put('T', glyph("11111","00100","00100","00100","00100"));
        map.put('W', glyph("10001","10001","10101","11011","10001"));
        map.put('-', glyph("000","000","111","000","000"));
        map.put('X', glyph("10001","01010","00100","01010","10001"));
        map.put('2', glyph("11110","00001","01110","10000","11111"));
        map.put(' ', glyph("00","00","00","00","00"));        return map;
    }

    private static String[] glyph(String... rows) {
        return rows;
    }

    private record RoleArt(String id, String label, int codepoint, String texture, int[] colors, boolean turtle) {
    }
}


package dev.turtleroles.service;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public final class TabListService {
    private static final Key LOGO_FONT = Key.key("turtleroles:header");
    private static final Key DEFAULT_FONT = Key.key("minecraft:default");
    static final String LOGO_GLYPHS = logoGlyphs();

    private static String logoGlyphs() {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 10; column++) {
                int index = row * 10 + column;
                text.append((char) (0xE100 + index)).append((char) (0xE200 + index));
            }
            // All rows share one text baseline; provider ascents place them
            // vertically. Rewind X between rows, leaving a final 160px width.
            if (row < 3) text.append('\uE2FF');
        }
        return text.toString();
    }
    private final Plugin plugin;
    private final PresentationService presentation;
    private BukkitTask task;

    public TabListService(Plugin plugin, PresentationService presentation) {
        this.plugin = plugin;
        this.presentation = presentation;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void refreshAll() {
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        presentation.refreshAll();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendPlayerListHeaderAndFooter(header(viewer), footer(viewer, tps));
        }
    }

    Component header(Player viewer) {
        if (!presentation.canUseGlyphFor(viewer)) {
            return Component.text("CONQUEST SMP", TextColor.color(0xA970FF))
                .font(DEFAULT_FONT).decorate(TextDecoration.BOLD);
        }
        // Four 16px tile rows occupy 64 GUI pixels. Reserve eight following
        // text rows before player names, exactly as in the previous layout.
        return Component.empty().font(DEFAULT_FONT)
            .append(Component.text(LOGO_GLYPHS, NamedTextColor.WHITE).font(LOGO_FONT)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.shadowColor(0)))
            .append(Component.text("\n".repeat(8)).font(DEFAULT_FONT));
    }

    Component footer(Player viewer, double tps) {
        TextColor tpsColor = TextColor.color(0xC89AFF);
        boolean decorated = false;
        Component result = Component.empty().font(DEFAULT_FONT);
        result = result.append(Component.newline());
        result = result
            .append(Component.text("PING: ", TextColor.color(0xA970FF)))
            .append(Component.text(viewer.getPing() + "ms", TextColor.color(0xD9B8FF)))
            .append(Component.text("  |  ", TextColor.color(0x713BB5)))
            .append(Component.text("TPS: ", TextColor.color(0xA970FF)))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", tps), tpsColor));
        if (decorated) result = result.append(Component.newline());
        return result;
    }

}

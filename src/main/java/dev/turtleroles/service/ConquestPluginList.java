package dev.turtleroles.service;

import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import java.util.*;

/** A branded in-game module overview. Console retains its real diagnostic list. */
public final class ConquestPluginList implements Listener {
    public static boolean matches(String input) {
        String root=input.strip().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        while(root.startsWith("/"))root=root.substring(1);
        root=root.substring(root.lastIndexOf(':')+1);
        return root.equals("plugins")||root.equals("pl");
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void command(PlayerCommandPreprocessEvent event) {
        if(!matches(event.getMessage()))return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("Conquest SMP Systems",TextColor.color(0xB477FF)));
        event.getPlayer().sendMessage(Component.text("Conquest SMP | Conquest AC | Conquest Integrations",TextColor.color(0xD7BBF5)));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void complete(TabCompleteEvent event) {
        if(event.getSender() instanceof Player && matches(event.getBuffer()))event.setCompletions(List.of());
    }
}

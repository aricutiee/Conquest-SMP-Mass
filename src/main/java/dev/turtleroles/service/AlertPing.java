package dev.turtleroles.service;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Quiet notification ping for server broadcasts, not ordinary player chat. */
public final class AlertPing implements Listener {
    private final Plugin plugin;
    private final Map<UUID,Long> last=new HashMap<>();
    public AlertPing(Plugin plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void alert(BroadcastMessageEvent event) {
        if(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).isBlank())return;
        var recipients=new ArrayList<>(event.getRecipients());
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || !plugin.getConfig().getBoolean("notifications.enabled",true))return;
            long now=System.currentTimeMillis();
            last.entrySet().removeIf(entry->now-entry.getValue()>10_000);
            for(var recipient:recipients)if(recipient instanceof Player player && player.isOnline() && now-last.getOrDefault(player.getUniqueId(),0L)>=500) {
                last.put(player.getUniqueId(),now);
                player.playSound(player.getLocation(),plugin.getConfig().getString("notifications.sound","minecraft:block.note_block.pling"),SoundCategory.MASTER,
                    (float)Math.clamp(plugin.getConfig().getDouble("notifications.volume",0.18),0,1),
                    (float)Math.clamp(plugin.getConfig().getDouble("notifications.pitch",1.8),0.5,2));
            }
        });
    }
}

package dev.turtleroles.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.*;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.Plugin;

/** Uses vanilla Unicode glyphs because server-list clients have not loaded our pack. */
public final class ConquestMotd implements Listener {
    private final Plugin plugin;
    public ConquestMotd(Plugin plugin) { this.plugin=plugin; }
    public static String smallCaps(String input) {
        String alphabet="abcdefghijklmnopqrstuvwxyz", caps="ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder result=new StringBuilder();
        for(char c:input.toCharArray()) {int i=alphabet.indexOf(Character.toLowerCase(c));result.append(i<0?c:caps.charAt(i));}
        return result.toString();
    }
    public static int width(String text) {
        int width=0;
        for(char c:text.toCharArray()) width+=c==' '?4:c=='.'?2:c=='ɪ'?4:6;
        return width;
    }
    public static Component line(String raw,int frameWidth,int textColor) {
        String text=smallCaps(raw);
        // Keep every line inside the vanilla server-list wrap width, including both decorations.
        frameWidth=Math.min(250,frameWidth);
        int flank=Math.max(0,(frameWidth-width(text))/2);
        int noise=Math.max(0,(flank-4)/6);
        int spaces=flank>=4?1:0;
        Component result=Component.empty();
        for(int i=0;i<noise;i++) result=result.append(scramble(i,noise));
        String padding=" ".repeat(spaces);
        result=result.append(Component.text(padding)).append(Component.text(text,TextColor.color(textColor))
                .decoration(TextDecoration.OBFUSCATED,false)).append(Component.text(padding));
        // Flat siblings keep both sides independent during Minecraft's two-line MOTD wrapping.
        for(int i=noise-1;i>=0;i--) result=result.append(scramble(i,noise));
        return result;
    }
    private static Component scramble(int index,int count) {
        double t=count<=1?1:(double)index/(count-1);
        int color=((0x20+(int)(0x50*t))<<16)|((0x0C+(int)(0x24*t))<<8)|(0x34+(int)(0x74*t));
        return Component.text("x",TextColor.color(color)).decoration(TextDecoration.OBFUSCATED,true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void ping(ServerListPingEvent e) {
        if(!plugin.getConfig().getBoolean("server-list.enabled",true))return;
        int width=Math.max(180,Math.min(250,plugin.getConfig().getInt("server-list.width-pixels",250)));
        e.motd(line(plugin.getConfig().getString("server-list.title","welcome to the conquest smp"),width,0xA35BDF)
            .append(Component.newline()).append(line(plugin.getConfig().getString("server-list.subtitle","law of the strongest."),width,0x683493)));
    }
}

